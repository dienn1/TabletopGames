package players.mcts;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.CoreConstants;
import core.Game;
import core.interfaces.IStateHeuristic;
import games.GameType;
import games.connect4.Connect4Heuristic;
import org.junit.Test;
import players.PlayerConstants;
import players.simple.RandomPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * MCTSPlayer must drive the IStateHeuristic lifecycle: reset once per game, observe once per decision,
 * and a copy per instantiation.
 */
public class MCTSHeuristicLifecycleTest {

    /** Counts the lifecycle calls. copyHeuristic() returns an independent counter. */
    static class CountingHeuristic implements IStateHeuristic {
        int resets, observations, evaluateAllCalls, evaluateStateCalls;

        @Override
        public double evaluateState(AbstractGameState gs, int playerId) {
            evaluateStateCalls++;
            return 0.5;
        }

        @Override
        public double[] evaluateAll(AbstractGameState gs) {
            evaluateAllCalls++;
            double[] values = new double[gs.getNPlayers()];
            Arrays.fill(values, 0.5);
            return values;
        }

        @Override
        public void reset(AbstractGameState gs) {
            resets++;
        }

        @Override
        public void observe(AbstractGameState gs) {
            observations++;
        }

        @Override
        public IStateHeuristic copyHeuristic() {
            return new CountingHeuristic();
        }
    }

    private static MCTSPlayer player(IStateHeuristic heuristic, int budget) {
        MCTSParams params = new MCTSParams();
        params.setParameterValue("rolloutLength", 0);
        params.setParameterValue("information", "Information_Set");
        params.setParameterValue("budgetType", PlayerConstants.BUDGET_ITERATIONS);
        params.setParameterValue("budget", budget);
        params.setParameterValue("heuristic", heuristic);
        return new MCTSPlayer(params);
    }

    private static Game play(MCTSPlayer player, long seed) {
        List<AbstractPlayer> players = new ArrayList<>();
        players.add(player);
        players.add(new RandomPlayer(new Random(5)));
        return Game.runOne(GameType.Connect4, null, players, seed, false, null, null, 0);
    }

    @Test
    public void resetAndObserveAreDrivenByThePlayer() {
        CountingHeuristic heuristic = new CountingHeuristic();

        Game game = play(player(heuristic, 30), 1234L);

        assertNotEquals(CoreConstants.GameResult.GAME_ONGOING, game.getGameState().getGameStatus());
        assertEquals("reset must run exactly once per game", 1, heuristic.resets);
        assertTrue("observe must run at every decision, was " + heuristic.observations,
                heuristic.observations >= 5);
    }

    @Test
    public void everyInstantiationGetsItsOwnHeuristic() {
        CountingHeuristic heuristic = new CountingHeuristic();
        MCTSParams params = new MCTSParams();
        params.setParameterValue("heuristic", heuristic);

        MCTSPlayer first = params.instantiate();
        MCTSPlayer second = params.instantiate();

        assertNotSame(first.getStateHeuristic(), second.getStateHeuristic());
        assertNotSame(first.getStateHeuristic(), heuristic);
    }

    @Test
    public void aTunableHeuristicIsStillCopiedIndependently() {
        MCTSParams params = new MCTSParams();
        params.setParameterValue("heuristic", new Connect4Heuristic());

        MCTSParams copy = params.copy();

        assertNotSame("TunableParameters.copy() already deep-copies a tunable heuristic; the override must not undo it",
                params.heuristic, copy.heuristic);
    }

    @Test
    public void copyKeepsTheBudget() {
        MCTSParams params = new MCTSParams();
        params.setParameterValue("numDeterminizations", 10);
        params.setParameterValue("budget", 40);

        assertEquals(params.budget, params.copy().budget);
    }
}
