package games.backgammon;

import core.AbstractPlayer;
import core.interfaces.IPlayerDecorator;
import games.backgammon.actions.LoadedDiceDecorator;
import games.backgammon.actions.RollDice;
import org.junit.Before;
import org.junit.Test;
import players.PlayerFactory;
import players.mcts.MCTSPlayer;
import utilities.JSONUtils;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CheatingAgentTests {

    BGGameState gameState;
    BGParameters parameters;
    BGForwardModel forwardModel;

    @Before
    public void setUp() {
        parameters = new BGParameters();
        parameters.setParameterValue("doubleActions", false);
        gameState = new BGGameState(parameters, 2);
        forwardModel = new BGForwardModel();
        forwardModel.setup(gameState);
        assertEquals(new RollDice(), forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, new RollDice());
    }

    @Test
    public void loadCheatingAgentFromJSON() {
        AbstractPlayer cheater = PlayerFactory.createPlayer("src/test/java/games/backgammon/CheatingAgent.json");
        assertTrue(cheater instanceof MCTSPlayer);
        List<IPlayerDecorator> decorators = cheater.getDecorators();
        assertEquals(1, decorators.size());
        assertTrue(decorators.get(0) instanceof LoadedDiceDecorator);

        LoadedDiceDecorator loadedDiceDecorator = (LoadedDiceDecorator) decorators.get(0);
        assertEquals(3, loadedDiceDecorator.getPDFCount());
        assertEquals(6, loadedDiceDecorator.getPDF(0).length);
        for (int i = 0; i < 6; i++) {
            assertEquals(1.0 / 6, loadedDiceDecorator.getPDF(0)[i], 1e-3);
        }
        assertEquals(6, loadedDiceDecorator.getPDF(2).length);
        for (int i = 0; i < 6; i++) {
            assertEquals(i == 0 ? 1.0 : 0.0, loadedDiceDecorator.getPDF(2)[i], 1e-3);
        }
    }

}
