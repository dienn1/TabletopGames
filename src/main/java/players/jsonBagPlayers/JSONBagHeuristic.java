package players.jsonBagPlayers;

import core.AbstractGameState;
import core.CoreConstants;
import core.interfaces.IStateHeuristic;
import org.json.simple.JSONObject;

import java.util.*;

/**
 * Value function built on JSON-Bag prototypes: a state is scored by the Jensen-Shannon distance between the token
 * bag of the trajectory so far plus the state itself, and one prototype bag per player.
 * <p>
 * The bags are dense integer arrays indexed by interned token ids ({@link TokenBag}), the prototypes are compiled to
 * one interleaved probability array ({@link PrototypeSet}), and the state being valued is merged into the trajectory
 * bag and then rolled back, so scoring a state copies nothing; the only allocation left in the path is one
 * scratch array of distances per prototype.
 */
public class JSONBagHeuristic implements IStateHeuristic {

    /** Map view of {@link #current} - the trajectory bag so far. Kept as a Map for callers and experiments. */
    Map<String, Integer> currentJSONBag;
    Set<String> filterSet;

    final List<Map<String, ? extends Number>> prototypes;

    private final TokenDictionary dict;
    private final PrototypeSet compiled;
    private final TokenBag current;
    private double[] distanceBuffer;

    public JSONBagHeuristic(List<? extends Map<String, ? extends Number>> prototypes_) {
        this(prototypes_, new ArrayList<>());
    }

    public JSONBagHeuristic(List<? extends Map<String, ? extends Number>> prototypes_, Collection<String> filterList_) {
        dict = new TokenDictionary();
        filterSet = Set.copyOf(filterList_);
        dict.setFilter(filterSet, true);
        prototypes = new ArrayList<>(prototypes_.size());
        prototypes.addAll(prototypes_);
        compiled = PrototypeSet.compile(prototypes, dict);
        current = new TokenBag(dict);
        currentJSONBag = current;
        distanceBuffer = new double[compiled.count];
    }

    /**
     * Builds the value function from the JSON object a player descriptor nests under "heuristic":
     * <pre>
     * "heuristic": { "class": "players.jsonBagPlayers.JSONBagHeuristic",
     *                "prototypeDir": "Experiments/valueFuncTest2/Connect4/prototype100_0_allSegFS_seg4_cum",
     *                "nPrototypes": 2,
     *                "fullPrototypes": true,
     *                "filterFile": ".../features-top100.json" }
     * </pre>
     * prototypeDir is required; nPrototypes defaults to 2, fullPrototypes to true, and an absent or empty
     * filterFile keeps every token. The prototypes are filtered before they are compiled: {@link PrototypeSet}
     * interns every key it is given, so an unfiltered prototype would carry tokens the bag can never produce.
     *
     * @param json the descriptor.
     */
    public JSONBagHeuristic(JSONObject json) {
        this(load(json));
    }

    private JSONBagHeuristic(Loaded loaded) {
        this(loaded.prototypes(), loaded.filter());
    }

    private record Loaded(List<Map<String, Integer>> prototypes, List<String> filter) {
    }

    private static Loaded load(JSONObject json) {
        String dir = stringValue(json, "prototypeDir", "");
        if (dir.isBlank())
            throw new IllegalArgumentException("JSONBagHeuristic needs a prototypeDir in its JSON descriptor: " + json.toJSONString());
        int nPrototypes = intValue(json, "nPrototypes", 2);
        if (nPrototypes < 1)
            throw new IllegalArgumentException("JSON-Bag nPrototypes must be at least 1, got " + nPrototypes);
        boolean full = booleanValue(json, "fullPrototypes", true);
        String filterFile = stringValue(json, "filterFile", "");
        List<String> filter = filterFile.isBlank() ? List.of() : Tokenizer.loadStringList(filterFile);
        if (!filterFile.isBlank() && filter.isEmpty())
            throw new IllegalArgumentException("JSON-Bag filter file " + filterFile + " is missing or empty");
        List<Map<String, Integer>> prototypes = Tokenizer.loadPrototypes(dir, nPrototypes, full);
        for (int i = 0; i < prototypes.size(); i++) {
            if (prototypes.get(i).isEmpty())
                throw new IllegalArgumentException("JSON-Bag prototype " + i + " is empty: expected "
                        + (full ? "full_prototype" : "prototype") + i + ".json under " + dir);
        }
        if (!filter.isEmpty()) {
            Set<String> keep = Set.copyOf(filter);
            for (Map<String, Integer> prototype : prototypes)
                Tokenizer.filter(prototype, keep, true);
        }
        return new Loaded(prototypes, filter);
    }

    private static String stringValue(JSONObject json, String key, String defaultValue) {
        Object value = json.get(key);
        return value == null ? defaultValue : value.toString().trim();
    }

    private static int intValue(JSONObject json, String key, int defaultValue) {
        Object value = json.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static boolean booleanValue(JSONObject json, String key, boolean defaultValue) {
        Object value = json.get(key);
        if (value instanceof Boolean flag) return flag;
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    public void setFilterSet(Collection<String> filterSet_) {
        filterSet = Set.copyOf(filterSet_);
        dict.setFilter(filterSet, true);
    }

    /** The compiled prototypes, for tests and benchmarks that need to inspect them. */
    PrototypeSet compiledPrototypes() {
        return compiled;
    }

    public void updateJSONBag(AbstractGameState gs) {
        Tokenizer.tokenizeInto(gs, current);
    }

    public void resetCurrentBag(AbstractGameState gs) {
        current.clear();
        updateJSONBag(gs);
    }

    /** Starts the trajectory bag at the given observation. Called by MCTSPlayer at the start of a game. */
    @Override
    public void reset(AbstractGameState gs) {
        resetCurrentBag(gs);
    }

    /** Adds the observed state to the trajectory bag. Called by MCTSPlayer once per decision. */
    @Override
    public void observe(AbstractGameState gs) {
        updateJSONBag(gs);
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        if (gs.getNPlayers() != compiled.count) {
            throw new RuntimeException("PLAYER COUNT AND PROTOTYPE COUNT MISMATCHED: " + gs.getNPlayers() + " vs. " + compiled.count);
        }
        CoreConstants.GameResult result = gs.getPlayerResults()[playerId];
        if (result == CoreConstants.GameResult.LOSE_GAME)
            return 0;
        if (result == CoreConstants.GameResult.WIN_GAME)
            return 1.0;
        return valuesFromBag(gs)[playerId];
    }

    /**
     * The value of a state for every player, before any terminal result is applied: values the trajectory so far
     * plus {@code gs}, then undoes the merge instead of copying the whole bag. The single place the hot path
     * lives, so {@link #evaluateState} and {@link #evaluateAll} cannot drift apart.
     */
    private double[] valuesFromBag(AbstractGameState gs) {
        if (distanceBuffer.length != compiled.count) distanceBuffer = new double[compiled.count];
        current.startRecording();
        try {
            Tokenizer.tokenizeInto(gs, current);
            compiled.distances(current, distanceBuffer);
        } finally {
            current.rollback();
        }
        return subtractNormalized(distanceBuffer);
    }

    /**
     * The value of {@code gs} for every player, from one tokenisation and one distance pass. Identical to
     * calling {@link #evaluateState(AbstractGameState, int)} for each player, including the terminal
     * short-circuit: a player who has lost scores 0 and a player who has won scores 1 without any bag work.
     */
    @Override
    public double[] evaluateAll(AbstractGameState gs) {
        if (gs.getNPlayers() != compiled.count) {
            throw new RuntimeException("PLAYER COUNT AND PROTOTYPE COUNT MISMATCHED: " + gs.getNPlayers() + " vs. " + compiled.count);
        }
        double[] values = new double[compiled.count];
        CoreConstants.GameResult[] results = gs.getPlayerResults();
        boolean needsDistances = false;
        for (int i = 0; i < values.length; i++) {
            if (results[i] == CoreConstants.GameResult.LOSE_GAME) {
                values[i] = 0;
            } else if (results[i] == CoreConstants.GameResult.WIN_GAME) {
                values[i] = 1.0;
            } else {
                needsDistances = true;
            }
        }
        if (!needsDistances)
            return values;

        double[] normalised = valuesFromBag(gs);
        for (int i = 0; i < values.length; i++) {
            if (results[i] != CoreConstants.GameResult.LOSE_GAME && results[i] != CoreConstants.GameResult.WIN_GAME)
                values[i] = normalised[i];
        }
        return values;
    }

    private double[] invertNormalized(double[] distancesArray) {
        double[] ret = new double[distancesArray.length];
        double sum = 0.0;
        for (int i = 0; i < distancesArray.length; i++) {
            ret[i] = 1.0 / distancesArray[i];
            sum += ret[i];
        }
        for (int i = 0; i < ret.length; i++) {
            ret[i] /= sum;
        }
        return ret;
    }

    private double[] subtractNormalized(double[] distancesArray) {
        double[] ret = new double[distancesArray.length];
        double sum = 0.0;
        for (int i = 0; i < distancesArray.length; i++) {
            ret[i] = 1.0 - distancesArray[i];
            sum += ret[i];
        }
        if (!(sum > 1e-12)) {
            throw new IllegalStateException("JSON-Bag value function is degenerate: every prototype is at maximal "
                    + "Jensen-Shannon distance from the current bag (distances " + Arrays.toString(distancesArray)
                    + "), so no player can be ranked. The trajectory shares none of its tokens with any prototype - "
                    + "check the prototypes and the token filter.");
        }
        for (int i = 0; i < ret.length; i++) {
            ret[i] /= sum;
        }
        return ret;
    }

    private double[] negative(double[] distances) {
        double[] ret = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            ret[i] = - distances[i];
        }
        return ret;
    }

    private double[] softmax(double[] values, double temperature) {
        double[] ret = new double[values.length];
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            if (v > max) max = v;
        }
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            ret[i] = Math.exp((values[i] - max) / temperature);
            sum += ret[i];
        }
        for (int i = 0; i < ret.length; i++) {
            ret[i] /= sum;
        }
        return ret;
    }

    @Override
    public JSONBagHeuristic copyHeuristic() {
        JSONBagHeuristic copy = new JSONBagHeuristic(prototypes, new ArrayList<>(filterSet));
        copy.current.copyTokensFrom(current);
        return copy;
    }

    @Override
    public double minValue() {
        return 0;
    }
}
