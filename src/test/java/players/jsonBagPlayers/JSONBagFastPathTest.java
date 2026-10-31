package players.jsonBagPlayers;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.GameType;
import org.junit.Test;
import players.simple.RandomPlayer;

import utilities.Utils;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for the dense fast path: interned {@link TokenBag}s and {@link PrototypeSet} distances must agree with the
 * plain {@code Map<String,Integer>} + {@link utilities.Utils#jensenShannonDistance(Map, Map)} definitions, which are the
 * reference behaviour of the JSON-Bag representation.
 */
public class JSONBagFastPathTest {

    private static final GameType[] GAMES = {
            GameType.CantStop, GameType.Connect4, GameType.DotsAndBoxes, GameType.Wonders7
    };

    private static List<AbstractGameState> states(GameType gameType, int nPlayers, int wanted) {
        List<AbstractGameState> states = new ArrayList<>();
        var game = gameType.createGameInstance(nPlayers, 1234L);
        List<core.AbstractPlayer> players = new ArrayList<>();
        Random rnd = new Random(7);
        for (int i = 0; i < nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
        game.reset(players);
        AbstractGameState gs = game.getGameState();
        while (gs.isNotTerminal() && states.size() < wanted) {
            List<AbstractAction> actions = game.getForwardModel().computeAvailableActions(gs);
            if (actions.isEmpty()) break;
            states.add(gs.copy());
            game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())).copy());
        }
        return states;
    }

    /** The reference value: trajectory bag plus state bag, plain maps, plain jsd, subtract-and-normalise. */
    private static double referenceValue(List<Map<String, Integer>> prototypes, Map<String, Integer> trajectory,
                                         AbstractGameState gs, int playerId) {
        Map<String, Integer> bag = new HashMap<>(trajectory);
        Tokenizer.merge(bag, new LinkedHashMap<>(Tokenizer.tokenize(gs)));
        double[] distances = new double[prototypes.size()];
        for (int i = 0; i < prototypes.size(); i++)
            distances[i] = Utils.jensenShannonDistance(prototypes.get(i), bag);
        double sum = 0;
        double[] values = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            values[i] = 1.0 - distances[i];
            sum += values[i];
        }
        for (int i = 0; i < values.length; i++) values[i] /= sum;
        return values[playerId];
    }

    @Test
    public void denseDistanceMatchesTheMapDefinitionForEveryGame() {
        for (GameType gameType : GAMES) {
            int nPlayers = gameType == GameType.Wonders7 ? 3 : 2;
            List<AbstractGameState> corpus = states(gameType, nPlayers, 20);
            assertFalse(gameType + " produced no states", corpus.isEmpty());

            // one prototype per player, each a real bag with a little extra mass so the prototypes differ
            List<Map<String, Integer>> prototypes = new ArrayList<>();
            for (int p = 0; p < nPlayers; p++) {
                Map<String, Integer> prototype = new HashMap<>(Tokenizer.tokenize(corpus.get(0)));
                prototype.put("synthetic.protoype." + p, 3 + p);
                prototypes.add(prototype);
            }

            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.resetCurrentBag(corpus.get(0));
            Map<String, Integer> trajectory = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(corpus.get(0))));

            for (AbstractGameState gs : corpus) {
                Map<String, Integer> stateBag = new LinkedHashMap<>(Tokenizer.tokenize(gs));
                for (int player = 0; player < nPlayers; player++) {
                    double expected = referenceValue(prototypes, trajectory, gs, player);
                    double actual = heuristic.evaluateState(gs, player);
                    assertEquals(gameType + " player " + player, expected, actual, 1e-12);
                }
                Tokenizer.merge(trajectory, stateBag);
                heuristic.updateJSONBag(gs);
            }
        }
    }

    @Test
    public void tokenBagBehavesLikeAMap() {
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        Map<String, Integer> reference = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));

        Map<String, Integer> bag = Tokenizer.tokenize(gs);
        assertEquals(reference, bag);
        assertEquals(bag, reference);
        for (Map.Entry<String, Integer> e : reference.entrySet())
            assertEquals(e.getValue(), bag.get(e.getKey()));

        bag.put("a.new.token", 4);
        assertEquals(Integer.valueOf(4), bag.get("a.new.token"));
        assertEquals(Integer.valueOf(4), bag.remove("a.new.token"));
        assertNull(bag.get("a.new.token"));
        assertEquals(reference, bag);
    }

    @Test
    public void twoHeuristicsInterleavedDoNotShareState() {
        // the tokeniser keeps its walk state in a ThreadLocal; two value functions in flight together must not see
        // each other's prototypes, filters or bags
        List<AbstractGameState> corpus = states(GameType.DotsAndBoxes, 2, 15);
        Map<String, Integer> first = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(corpus.get(0))));
        Map<String, Integer> second = new HashMap<>();
        second.put("a.prototype.of.its.own", 5);
        second.putAll(new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(corpus.get(3)))));

        List<Map<String, Integer>> prototypesA = Arrays.asList(first, first);
        List<Map<String, Integer>> prototypesB = Arrays.asList(second, second);
        JSONBagHeuristic a = new JSONBagHeuristic(prototypesA);
        JSONBagHeuristic b = new JSONBagHeuristic(prototypesB);
        a.resetCurrentBag(corpus.get(0));
        b.resetCurrentBag(corpus.get(0));
        Map<String, Integer> trajectoryA = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(corpus.get(0))));
        Map<String, Integer> trajectoryB = new HashMap<>(trajectoryA);

        for (AbstractGameState gs : corpus) {
            double fromA = a.evaluateState(gs, 0);
            double fromB = b.evaluateState(gs, 0);

            assertEquals(referenceValue(prototypesA, trajectoryA, gs, 0), fromA, 1e-12);
            assertEquals(referenceValue(prototypesB, trajectoryB, gs, 0), fromB, 1e-12);

            Map<String, Integer> stateBag = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));
            Tokenizer.merge(trajectoryA, stateBag);
            Tokenizer.merge(trajectoryB, stateBag);
            a.updateJSONBag(gs);
            b.updateJSONBag(gs);
        }
    }

    /** A static nested class: Gson serialises a local class as null, which would make the test vacuous. */
    static class ExtremeHolder {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        long longMin = Long.MIN_VALUE;
        int[] array = {Integer.MIN_VALUE, 7};
    }

    /** Numbers at the edge of the integer range must print like Number.toString(); the digit loop works in long. */
    @Test
    public void extremeIntegerValuesKeepTheirText() {
        Map<String, Integer> bag = Tokenizer.tokenize(new ExtremeHolder());
        assertEquals(Integer.valueOf(1), bag.get(".min." + Integer.MIN_VALUE));
        assertEquals(Integer.valueOf(1), bag.get(".max." + Integer.MAX_VALUE));
        assertEquals(Integer.valueOf(1), bag.get(".longMin." + Long.MIN_VALUE));
        assertEquals(Integer.valueOf(1), bag.get(".array[0]." + Integer.MIN_VALUE));
        assertEquals(Integer.valueOf(1), bag.get(".array." + Integer.MIN_VALUE));
    }

    /** The map version short-circuits an empty distribution to distance 1; the dense path must agree. */
    @Test
    public void anEmptyPrototypeIsAtDistanceOne() {
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        Map<String, Integer> empty = new HashMap<>();
        Map<String, Integer> real = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));
        List<Map<String, Integer>> prototypes = Arrays.asList(empty, real);

        JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
        heuristic.resetCurrentBag(gs);

        assertEquals(1.0, Utils.jensenShannonDistance(empty, heuristic.currentJSONBag), 0.0);
        Map<String, Integer> trajectory = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));
        for (int player = 0; player < 2; player++) {
            assertEquals("player " + player, referenceValue(prototypes, trajectory, gs, player),
                    heuristic.evaluateState(gs, player), 1e-12);
        }
    }

    /**
     * A numeric frequency above the int range used to be truncated to a negative count, which zeroed the token out of
     * the non-zero list and made the distance loop compute 0 * log2(0) = NaN. Frequencies stay non-negative and the
     * dense path agrees with the map definition.
     */
    @Test
    public void hugeNumericFrequenciesStayNonNegativeAndFinite() {
        String json = "{\"k\":[2147483648,2147483648]}";
        Map<String, Integer> bag = Tokenizer.tokenize(json, "", false, "both", true, false, false, false, null, false);
        for (Map.Entry<String, Integer> e : bag.entrySet()) {
            assertTrue("token " + e.getKey() + " has negative count " + e.getValue(), e.getValue() >= 0);
        }
        assertEquals("the value token counts occurrences", Integer.valueOf(2), bag.get(".k.2147483648"));
        assertNull("a wrapped-negative frequency is dropped rather than poisoning the bag", bag.get(".k"));

        double selfDistance = Utils.jensenShannonDistance(bag, bag);
        assertTrue("self distance is not finite: " + selfDistance, !Double.isNaN(selfDistance));
        assertEquals(0.0, selfDistance, 0.0);

        // and the dense path must agree that a bag is at distance zero from itself, with no NaN in the loop
        TokenBag dense = (TokenBag) Tokenizer.tokenize("{\"k\":[2147483648,2147483648]}", "", false,
                "both", true, false, false, false, null, false);
        PrototypeSet prototypes = PrototypeSet.compile(List.of(dense, dense), dense.dictionary());
        double[] out = new double[2];
        prototypes.distances(dense, out);
        // finite, and zero up to the cancellation left by a 2^31:1 probability ratio (it used to be NaN)
        for (double d : out) {
            assertTrue("distance is not finite: " + d, !Double.isNaN(d));
            assertTrue("distance " + d + " should be ~0", Math.abs(d) < 1e-6);
        }
    }

    @Test
    public void densePathRejectsWhatTheKeyedApiRejects() {
        TokenBag bag = (TokenBag) Tokenizer.tokenize("{\"a\":1,\"b\":1}", "", false, "both", true,
                false, false, false, null, false);
        PrototypeSet prototypes = PrototypeSet.compile(List.of(bag), bag.dictionary());

        // a buffer that cannot hold one distance per prototype
        assertThrows(IllegalArgumentException.class, () -> prototypes.distances(bag, new double[0]));

        // negative mass cannot reach the dense loop at all: the bag refuses to hold it, so the loop's own
        // guard only catches a future change to TokenBag
        assertThrows(IllegalArgumentException.class, () -> bag.add(bag.dictionary().idOfRaw(".a"), -3));
    }

    @Test
    public void recordingNestsAndUnwindsInOrder() {
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        TokenBag bag = (TokenBag) Tokenizer.tokenize(gs);
        long before = bag.total();
        int distinctBefore = bag.distinct();

        bag.startRecording();
        bag.add(0, 5);
        bag.startRecording();
        bag.add(0, 7);
        bag.rollback();                       // undoes only the inner add
        assertEquals(before + 5, bag.total());
        bag.rollback();                       // undoes the outer add
        assertEquals(before, bag.total());
        assertEquals(distinctBefore, bag.distinct());
    }

    @Test
    public void filterWorksOnABagFromTheTokenizer() {
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        Map<String, Integer> reference = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));
        List<String> keys = new ArrayList<>(reference.keySet());
        Collections.sort(keys);
        String kept = keys.get(0);

        Map<String, Integer> bag = Tokenizer.tokenize(gs);
        Tokenizer.filter(bag, Collections.singleton(kept), true);   // keySet().retainAll, via iterator.remove
        assertEquals(Collections.singleton(kept), bag.keySet());

        Map<String, Integer> blacklisted = Tokenizer.tokenize(gs);
        Tokenizer.filter(blacklisted, Collections.singleton(kept), false);   // keySet().remove
        assertNull(blacklisted.get(kept));
        assertEquals(reference.size() - 1, blacklisted.size());
    }

    @Test
    public void changingTheFilterDropsNewlyExcludedTokens() {
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        String kept = Tokenizer.tokenize(gs).keySet().stream()
                .filter(k -> k.startsWith(".gameType")).findFirst().orElseThrow();

        JSONBagHeuristic heuristic = new JSONBagHeuristic(List.of(new HashMap<>(), new HashMap<>()));
        heuristic.setFilterSet(Collections.singletonList(kept));
        heuristic.resetCurrentBag(gs);

        assertEquals(Collections.singleton(kept), new HashSet<>(heuristic.currentJSONBag.keySet()));
    }

    @Test
    public void compiledPrototypeKeepsZeroCountKeysOutOfTheSupport() {
        // a zero-count key carries no mass, so {state tokens, "a":0} must score exactly like {state tokens}
        AbstractGameState gs = GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState();
        Map<String, Integer> base = new HashMap<>(new LinkedHashMap<>(Tokenizer.tokenize(gs)));
        Map<String, Integer> withZeroKey = new HashMap<>(base);
        withZeroKey.put("a", 0);

        JSONBagHeuristic withoutZero = new JSONBagHeuristic(Arrays.asList(base, base));
        JSONBagHeuristic withZero = new JSONBagHeuristic(Arrays.asList(withZeroKey, withZeroKey));
        withoutZero.resetCurrentBag(gs);
        withZero.resetCurrentBag(gs);

        assertEquals(0.5, withoutZero.evaluateState(gs, 0), 1e-12);
        assertEquals(withoutZero.evaluateState(gs, 0), withZero.evaluateState(gs, 0), 1e-15);
    }
}
