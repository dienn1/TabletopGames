package games.backgammon;

import core.AbstractForwardModel;
import core.AbstractPlayer;
import core.DecoratedForwardModel;
import core.actions.AbstractAction;
import core.interfaces.IPlayerDecorator;
import games.backgammon.actions.LoadDice;
import games.backgammon.actions.LoadedDiceDecorator;
import games.backgammon.actions.RollDice;
import org.junit.Before;
import org.junit.Test;
import players.PlayerFactory;
import players.mcts.MCTSPlayer;

import java.util.List;

import static org.junit.Assert.*;

public class CheatingAgentTests {

    BGGameState gameState;
    BGParameters parameters;
    AbstractForwardModel forwardModel;
    AbstractPlayer decoratedMCTSPlayer;

    @Before
    public void setUp() {
        parameters = new BGParameters();
        parameters.setParameterValue("doubleActions", false);
        gameState = new BGGameState(parameters, 2);
        forwardModel = new BGForwardModel();
        forwardModel.setup(gameState);
        assertEquals(new RollDice(), forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, new RollDice());

        decoratedMCTSPlayer = PlayerFactory.createPlayer("src/test/java/games/backgammon/CheatingAgent.json");
        decoratedMCTSPlayer.setPlayerID(0);
        decoratedMCTSPlayer.setForwardModel(forwardModel);
    }

    @Test
    public void loadCheatingAgentFromJSON() {
        assertTrue(decoratedMCTSPlayer instanceof MCTSPlayer);
        List<IPlayerDecorator> decorators = decoratedMCTSPlayer.getDecorators();
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
        // move two pieces
        IPlayerDecorator loadedDiceDecorator = decoratedMCTSPlayer.getDecorators().get(0);
        forwardModel = (new DecoratedForwardModel(forwardModel)).addDecorator(1, loadedDiceDecorator);

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

        // and not for player 0 : they can only roll the dice, no cheating options
        assertEquals(BGGamePhase.RollDice, gameState.getGamePhase());
        assertEquals(0, gameState.getCurrentPlayer());
        actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(new RollDice(), actions.getFirst());
        assertEquals(1, actions.size());
    }


    @Test
    public void loadDiceActionChangesTheProbabilities() {
        LoadedDiceDecorator loadedDiceDecorator = (LoadedDiceDecorator) decoratedMCTSPlayer.getDecorators().get(0);
        forwardModel = (new DecoratedForwardModel(forwardModel)).addDecorator(1, loadedDiceDecorator);

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
        assertTrue(sixCountTwo > 4);
        assertEquals(1, loadedDiceDecorator.getCurrentPDF());
    }

    @Test
    public void loadDiceOptionsExcludeTheCurrentSelectedPdfViaFM() {
        LoadedDiceDecorator loadedDiceDecorator = new LoadedDiceDecorator(6,
                new double[]{
                        0.167, 0.167, 0.167, 0.167, 0.167, 0.167,
                        0.1, 0.1, 0.1, 0.1, 0.1, 0.5,
                        1.0, 0.0, 0.0, 0.0, 0.0, 0.0
                });
        forwardModel = (new DecoratedForwardModel(forwardModel)).addDecorator(1, loadedDiceDecorator);

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        List<AbstractAction> actions = forwardModel.computeAvailableActions(gameState);
        assertEquals(0, loadedDiceDecorator.getCurrentPDF());
        forwardModel.next(gameState, actions.get(1));
        assertEquals(1, loadedDiceDecorator.getCurrentPDF());

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
            assertEquals(0.167, ((LoadDice) actions.get(1)).getPdf()[i], 0.01);
            assertEquals(i == 0 ? 1.0 : 0.0, ((LoadDice) actions.get(2)).getPdf()[i], 1e-3);
        }

    }

    @Test
    public void loadDiceOptionsExcludeTheCurrentSelectedPdfViaPlayer() {
        LoadedDiceDecorator loadedDiceDecorator = (LoadedDiceDecorator) decoratedMCTSPlayer.getDecorators().getFirst();
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        AbstractAction actionTaken;
        int count = 0;
        do {
            actionTaken = decoratedMCTSPlayer.getAction(gameState, forwardModel.computeAvailableActions(gameState));
            count++;
        } while (count < 10 && !(actionTaken instanceof LoadDice));
        if (!(actionTaken instanceof LoadDice)) {
            fail("Failed to take a LoadDice action after 10 attempts, got " + actionTaken);
        }
        int selectedPdf = ((LoadDice) actionTaken).getPdf()[0] == 1.0 ? 2 : 1; // we have two options, one with pdf[0] = 1.0, and one with pdf[0] = 0.1
        assertEquals(selectedPdf, loadedDiceDecorator.getCurrentPDF());
        forwardModel.next(gameState, actionTaken);
        assertEquals(selectedPdf, loadedDiceDecorator.getCurrentPDF());

        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        forwardModel.next(gameState, new RollDice()); // p0
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());
        forwardModel.next(gameState, forwardModel.computeAvailableActions(gameState).getFirst());

        List<AbstractAction> actions = loadedDiceDecorator.actionFilter(gameState, forwardModel.computeAvailableActions(gameState));
        assertEquals(BGGamePhase.RollDice, gameState.getGamePhase());
        assertEquals(1, gameState.getCurrentPlayer());
        assertEquals(3, actions.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(0.167, ((LoadDice) actions.get(1)).getPdf()[i], 0.01);
            assertEquals(i == 0 ? 1.0 : 0.0, ((LoadDice) actions.get(2)).getPdf()[i], 1e-3);
        }

    }

    @Test
    public void decoratedPlayerUsesDecoratedForwardModelInPlanning() {
        fail("Not yet implemented");
        // TODO: If we take a decision, we then look at the tree and confirm we have no LoadDice actions for the opponent
        // TODO: and several for the correct player
    }

    @Test
    public void decoratorOnPlayerIsNotAffectedByDecisionsRecordedOnDecoratedForwardModel() {
        fail("Not yet implemented");
    }
}
