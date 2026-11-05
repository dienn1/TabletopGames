package players.simple;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.CoreConstants;
import core.Game;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import games.GameType;
import org.junit.Test;
import players.jsonBagPlayers.JSONBagHeuristic;
import players.jsonBagPlayers.JSONBagOSLAPlayer;
import players.jsonBagPlayers.Tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * A plain OSLAPlayer driven by a JSONBagHeuristic must behave like JSONBagOSLAPlayer: same value function,
 * same bag lifecycle, same decision. The two players differ only in that JSONBagOSLAPlayer shuffles the
 * action list before scoring (so it breaks exact ties differently) and OSLAPlayer adds a 1e-6 noise term.
 */
public class OSLAPlayerJsonBagTest {

    private static List<Map<String, Integer>> prototypesFor(GameType gameType, int nPlayers, long seed) {
        AbstractGameState gs = gameType.createGameInstance(nPlayers, seed).getGameState();
        Map<String, Integer> zero = new HashMap<>(Tokenizer.tokenize(gs));
        // player 1's prototype carries a token the bag can never contain, so the two players are ranked differently
        Map<String, Integer> one = new HashMap<>(zero);
        one.put(".syntheticTokenForPlayerOne", 1);
        return new ArrayList<>(List.of(zero, one));
    }

    /** The values both players compute at this state, under their shared bag convention (state twice, then successor). */
    private static double[] valuesAt(Game game, AbstractGameState gs, List<AbstractAction> actions,
                                     List<Map<String, Integer>> prototypes) {
        JSONBagHeuristic reference = new JSONBagHeuristic(prototypes);
        reference.reset(gs);
        reference.observe(gs);
        double[] values = new double[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            AbstractGameState copy = gs.copy();
            game.getForwardModel().next(copy, actions.get(i).copy());
            values[i] = reference.evaluateState(copy, 0);
        }
        return values;
    }

    /** True when the best action wins by a margin the 1e-6 noise term cannot overturn. */
    private static boolean hasClearWinner(double[] values) {
        double best = Double.NEGATIVE_INFINITY, second = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (value > best) {
                second = best;
                best = value;
            } else if (value > second) {
                second = value;
            }
        }
        return best - second > 1e-5;
    }

    @Test
    public void picksTheSameActionAsTheJSONBagOSLAPlayer() {
        List<Map<String, Integer>> prototypes = prototypesFor(GameType.Connect4, 2, 11L);
        int compared = 0;
        for (long gameSeed : new long[]{11L, 22L, 33L}) {
            Game game = GameType.Connect4.createGameInstance(2, gameSeed);
            AbstractGameState gs = game.getGameState();
            Random rnd = new Random(gameSeed);
            while (gs.isNotTerminal() && compared < 10) {
                List<AbstractAction> actions = game.getForwardModel().computeAvailableActions(gs);
                if (actions.size() > 1 && gs.getCurrentPlayer() == 0
                        && hasClearWinner(valuesAt(game, gs, actions, prototypes))) {
                    JSONBagOSLAPlayer bagPlayer = new JSONBagOSLAPlayer(prototypes, List.of(), new Random(gameSeed));
                    bagPlayer.setForwardModel(game.getForwardModel());
                    bagPlayer.initializePlayer(gs);

                    OSLAPlayer plain = new OSLAPlayer(new JSONBagHeuristic(prototypes), new Random(gameSeed));
                    plain.setForwardModel(game.getForwardModel());
                    plain.initializePlayer(gs);

                    AbstractAction expected = bagPlayer._getAction(gs.copy(), new ArrayList<>(actions));
                    AbstractAction actual = plain._getAction(gs.copy(), new ArrayList<>(actions));

                    assertEquals("seed " + gameSeed + " turn " + gs.getTurnCounter(),
                            expected.toString(), actual.toString());
                    compared++;
                }
                game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())));
            }
        }
        assertTrue("no state with a clear value winner was found; the comparison never ran", compared > 0);
    }

    @Test
    public void aCopyOwnsItsHeuristic() {
        JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypesFor(GameType.Connect4, 2, 5L));
        OSLAPlayer player = new OSLAPlayer(heuristic, new Random(1));
        AbstractGameState gs = GameType.Connect4.createGameInstance(2, 5L).getGameState();
        player.initializePlayer(gs);

        OSLAPlayer copy = player.copy();

        assertNotSame("a copy must not share the heuristic, or both players write into one trajectory bag",
                heuristic, copy.heuristic);
        double before = heuristic.evaluateState(gs, 0);
        ((IStateHeuristic) copy.heuristic).observe(GameType.Connect4.createGameInstance(2, 6L).getGameState());
        assertEquals("observing the copy must not change the original's bag",
                before, heuristic.evaluateState(gs, 0), 0.0);
    }

    @Test
    public void aPlayerWithoutAHeuristicStillPlays() {
        List<AbstractPlayer> players = new ArrayList<>();
        players.add(new OSLAPlayer(new Random(3)));
        players.add(new RandomPlayer(new Random(4)));

        Game game = Game.runOne(GameType.Connect4, null, players, 77L, false, null, null, 0);

        assertNotEquals(CoreConstants.GameResult.GAME_ONGOING, game.getGameState().getGameStatus());
    }
}
