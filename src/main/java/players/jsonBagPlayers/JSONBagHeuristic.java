package players.jsonBagPlayers;

import core.AbstractGameState;
import core.CoreConstants;
import core.interfaces.IStateHeuristic;

import java.util.*;

import static players.jsonBagPlayers.JensenShannonDistance.distances;

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

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        if (gs.getNPlayers() != compiled.count) {
            throw new RuntimeException("PLAYER COUNT AND PROTOTYPE COUNT MISMATCHED: " + gs.getNPlayers() + " vs. " + compiled.count);
        }
        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.LOSE_GAME)
            return 0;
        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.WIN_GAME)
            return 1.0;

        // value the trajectory so far plus this state, then undo the merge instead of copying the whole bag
        if (distanceBuffer.length != compiled.count) distanceBuffer = new double[compiled.count];
        current.startRecording();
        try {
            Tokenizer.tokenizeInto(gs, current);
            distances(compiled, current, distanceBuffer);
        } finally {
            current.rollback();
        }
        double[] values = subtractNormalized(distanceBuffer);
        return values[playerId];
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

    public JSONBagHeuristic copy() {
        JSONBagHeuristic copy = new JSONBagHeuristic(prototypes, new ArrayList<>(filterSet));
        copy.current.copyTokensFrom(current);
        return copy;
    }

    @Override
    public double minValue() {
        return 0;
    }
}
