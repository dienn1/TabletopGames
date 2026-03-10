package games.backgammon.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IPlayerDecorator;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LoadedDiceDecorator implements IPlayerDecorator {

    List<double[]> pdfs;
    int currentPDFIndex = 0;

    public LoadedDiceDecorator(int sides, double[] probabilities) {
        pdfs = new ArrayList<>(probabilities.length / sides);
        int nDice = probabilities.length / sides;
        for (int i = 0; i < nDice; i++) {
            double[] pdf = new double[sides];
            for (int j = 0; j < sides; j++) {
                pdf[j] = probabilities[i * sides + j];
            }
            pdfs.add(pdf);
        }
    }

    public LoadedDiceDecorator(JSONObject json) {
        // we expect a JSON object with a "probabilities" field
        pdfs = new ArrayList<>();
        int sides = json.get("sides") != null ? ((Long) json.get("sides")).intValue() : 6; // default to 6 sides if not specified
        List<Double> probabilities = (List<Double>) json.get("probabilities");
        double[] pdf = new double[sides];
        for (int i = 0; i < probabilities.size(); i++) {
            pdf[i % sides] += probabilities.get(i);
            if ((i + 1) % sides == 0) {
                pdfs.add(pdf);
                pdf = new double[sides];
            }
        }
    }

    public double[] getPDF(int n) {
        return pdfs.get(n);
    }

    public int getPDFCount() {
        return pdfs.size();
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
