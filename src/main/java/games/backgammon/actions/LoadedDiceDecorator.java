package games.backgammon.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IPlayerDecorator;

import java.util.ArrayList;
import java.util.List;

public class LoadedDiceDecorator implements IPlayerDecorator {

    List<double[]> pdfs;
    int currentPDFIndex = 0;

    public LoadedDiceDecorator(List<double[]> pdfOptions) {
        this.pdfs = pdfOptions;
    }

    @Override
    public List<AbstractAction> actionFilter(AbstractGameState state, List<AbstractAction> possibleActions) {
        // we add the LoadDice action to the list of possible actions for the decision player
        List<AbstractAction> newPossibleActions = new ArrayList<>(possibleActions);
        for (int i = 0; i < pdfs.size(); i++) {
            if (currentPDFIndex == i) continue; // skip the current pdf, as this is already the in use
            double[] pdf = pdfs.get(i);
            newPossibleActions.add(new LoadDice(0, pdf));
        }
        return newPossibleActions;
    }

    @Override
    public void recordDecision(AbstractGameState state, AbstractAction action) {
        // based on the actual action selected, we amend the current pdf index
        if (action instanceof LoadDice loadDice) {
            currentPDFIndex = pdfs.indexOf(loadDice.newPDF);
        }
    }

    @Override
    public boolean decisionPlayerOnly() {
        return true; // the opponent is not modelled as being able to cheat
    }
}
