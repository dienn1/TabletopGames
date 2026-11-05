package players.jsonBagPlayers;

import core.AbstractGameState;
import core.CoreConstants;
import games.GameType;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The lifecycle methods on JSONBagHeuristic: reset starts the trajectory bag, observe extends it,
 * evaluateAll answers every player from one pass, and a copy shares no mutable state.
 */
public class JSONBagHeuristicLifecycleTest {

    private AbstractGameState state;
    private JSONBagHeuristic heuristic;

    @Before
    public void setup() {
        state = GameType.DotsAndBoxes.createGameInstance(2, 42L).getGameState();
        Map<String, Integer> bag = Tokenizer.tokenize(state);
        heuristic = new JSONBagHeuristic(Arrays.asList(new HashMap<>(bag), new HashMap<>(bag)));
    }

    private static Map<String, Integer> merge(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> merged = new HashMap<>(a);
        b.forEach((token, count) -> merged.merge(token, count, Integer::sum));
        return merged;
    }

    @Test
    public void resetStartsTheBagAtTheGivenState() {
        heuristic.reset(state);

        assertEquals(Tokenizer.tokenize(state), heuristic.currentJSONBag);
    }

    @Test
    public void observeAddsTheStateToTheBag() {
        heuristic.reset(state);
        AbstractGameState next = GameType.DotsAndBoxes.createGameInstance(2, 7L).getGameState();

        heuristic.observe(next);

        assertEquals(merge(Tokenizer.tokenize(state), Tokenizer.tokenize(next)), heuristic.currentJSONBag);
    }

    @Test
    public void evaluateAllMatchesEvaluateState() {
        heuristic.reset(state);
        AbstractGameState other = GameType.DotsAndBoxes.createGameInstance(2, 11L).getGameState();

        double[] all = heuristic.evaluateAll(other);

        assertEquals(2, all.length);
        for (int p = 0; p < all.length; p++)
            assertEquals("player " + p, heuristic.evaluateState(other, p), all[p], 1e-12);
    }

    @Test
    public void evaluateAllKeepsTheTerminalShortCircuit() {
        heuristic.reset(state);
        state.setPlayerResult(CoreConstants.GameResult.LOSE_GAME, 0);
        state.setPlayerResult(CoreConstants.GameResult.WIN_GAME, 1);

        double[] values = heuristic.evaluateAll(state);

        assertEquals(0.0, values[0], 0.0);
        assertEquals(1.0, values[1], 0.0);
    }

    @Test
    public void copyCarriesTheBagButSharesNoState() {
        heuristic.reset(state);

        JSONBagHeuristic copy = heuristic.copyHeuristic();
        assertEquals(heuristic.currentJSONBag, copy.currentJSONBag);

        copy.observe(GameType.DotsAndBoxes.createGameInstance(2, 99L).getGameState());

        assertNotEquals("the copy must not write into the original bag",
                heuristic.currentJSONBag, copy.currentJSONBag);
    }
}
