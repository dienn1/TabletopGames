package core.interfaces;

import core.AbstractGameState;
import games.GameType;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The lifecycle methods a stateful heuristic (JSON-Bag) relies on must be inert for the heuristics that
 * do not override them, and must leave the single abstract method untouched so lambdas keep working.
 */
public class IStateHeuristicTest {

    private AbstractGameState state;

    @Before
    public void setup() {
        state = GameType.DotsAndBoxes.createGameInstance(2, 42L).getGameState();
    }

    @Test
    public void defaultsAreInertAndCopyReturnsTheSameInstance() {
        IStateHeuristic heuristic = (gs, playerId) -> 0.5;

        heuristic.reset(state);
        heuristic.observe(state);

        assertSame(heuristic, heuristic.copyHeuristic());
    }

    @Test
    public void evaluateAllCallsEvaluateStateOncePerPlayer() {
        AtomicInteger calls = new AtomicInteger();
        IStateHeuristic heuristic = (gs, playerId) -> {
            calls.incrementAndGet();
            return playerId + 1;
        };

        double[] values = heuristic.evaluateAll(state);

        assertEquals(state.getNPlayers(), values.length);
        assertEquals("one evaluateState call per player", state.getNPlayers(), calls.get());
        for (int i = 0; i < values.length; i++)
            assertEquals(i + 1.0, values[i], 1e-12);
    }
}
