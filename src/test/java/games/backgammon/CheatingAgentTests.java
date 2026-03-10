package games.backgammon;

import core.AbstractForwardModel;
import core.AbstractPlayer;
import core.DecoratedForwardModel;
import core.StandardForwardModel;
import core.actions.AbstractAction;
import core.interfaces.IPlayerDecorator;
import games.backgammon.actions.LoadDice;
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
    AbstractForwardModel forwardModel;

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

    @Test
    public void cheaterGetsAdditionalOptionsInRollDicePhase() {
        AbstractPlayer cheater = PlayerFactory.createPlayer("src/test/java/games/backgammon/CheatingAgent.json");
        // move two pieces
        IPlayerDecorator loadedDiceDecorator = cheater.getDecorators().get(0);
        forwardModel = new DecoratedForwardModel(forwardModel, List.of(loadedDiceDecorator), 1);

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        // check we have cheat options for player 1
        assertEquals(BGGamePhase.RollDice, gameState.getGamePhase());
        assertEquals(1, gameState.getCurrentPlayer());
        List<AbstractAction> actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(new RollDice(), actions.getFirst());
        assertEquals(3, actions.size());
        assertTrue(actions.get(1) instanceof LoadDice);
        assertTrue(actions.get(2) instanceof LoadDice);

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        // and not for player 0
        assertEquals(BGGamePhase.RollDice, gameState.getGamePhase());
        assertEquals(0, gameState.getCurrentPlayer());
        actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(new RollDice(), actions.getFirst());
        assertEquals(1, actions.size());
    }


    @Test
    public void loadDiceActionChangesTheProbabilities() {
        AbstractPlayer cheater = PlayerFactory.createPlayer("src/test/java/games/backgammon/CheatingAgent.json");
        LoadedDiceDecorator loadedDiceDecorator = (LoadedDiceDecorator) cheater.getDecorators().get(0);
        forwardModel = new DecoratedForwardModel(forwardModel, List.of(loadedDiceDecorator), 1);

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        List<AbstractAction> actions = forwardModel.computeAvailableActions(gameState);
        LoadDice loadDiceAction = (LoadDice) actions.get(1);
        forwardModel.next(gameState, loadDiceAction);

        // this should include rolling the dice
        assertEquals(BGGamePhase.MovePieces, gameState.getGamePhase());
        assertEquals(1, gameState.getCurrentPlayer());

        // Now we roll the dice 100 times, and expect about 50 sixes
        int sixCountOne = 0;
        int sixCountTwo = 0;
        for (int i = 0; i < 100; i++) {
            gameState.rollDice();
            if (gameState.availableDiceValues[0] == 6) {
                sixCountOne++;
            }
            if (gameState.availableDiceValues[1] == 6) {
                sixCountTwo++;
            }
        }
        assertTrue(sixCountOne > 35);
        assertTrue(sixCountTwo < 25);
        assertEquals(1, loadedDiceDecorator.getCurrentPDF());
    }

    @Test
    public void loadDiceOptionsExcludeTheCurrentSelectedPdf() {
        AbstractPlayer cheater = PlayerFactory.createPlayer("src/test/java/games/backgammon/CheatingAgent.json");
        LoadedDiceDecorator loadedDiceDecorator = (LoadedDiceDecorator) cheater.getDecorators().get(0);
        forwardModel = new DecoratedForwardModel(forwardModel, List.of(loadedDiceDecorator), 1);

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        List<AbstractAction> actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(0, loadedDiceDecorator.getCurrentPDF());
        forwardModel.next(gameState, actions.get(2));
        assertEquals(2,  loadedDiceDecorator.getCurrentPDF());

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        forwardModel.next(gameState, new RollDice()); // p0
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        assertEquals(BGGamePhase.RollDice, gameState.getGamePhase());
        assertEquals(1, gameState.getCurrentPlayer());
        actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(3, actions.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(0.167, ((LoadDice) actions.get(1)).getPdf()[i], 1e-3);
            assertEquals(i == 0 ? 1.0 : 0.0, ((LoadDice) actions.get(2)).getPdf()[i], 1e-3);
        }

    }
}
