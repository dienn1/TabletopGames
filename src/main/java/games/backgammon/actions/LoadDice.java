package games.backgammon.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.components.Dice;
import games.backgammon.BGGameState;

import java.util.Arrays;

/**
 * This modifies the pdf of one of the dice in the game
 */
public class LoadDice extends AbstractAction {

    protected final double[] newPDF;
    protected final int die;
    protected final boolean singleRoll;

    static LoadDice getPermanentShift(int die, double[] newPDF) {
        return new LoadDice(die, newPDF, false);
    }
    static LoadDice getOneOffShift(int die,  double[] newPDF) {
        return new LoadDice(die, newPDF, true);
    }

    private LoadDice(int die, double[] newPDF, boolean singleRoll) {
        this.newPDF = newPDF;
        this.die = die;
        this.singleRoll = singleRoll;
    }

    @Override
    public boolean execute(AbstractGameState gs) {
        BGGameState state = (BGGameState) gs;
        double[] originalPDF = state.getDicePdf(die);
        state.setDicePdf(die, newPDF);
        state.rollDice();
        if (singleRoll) {
            // reset pdf
            state.setDicePdf(die, originalPDF);
        }
        return true;
    }

    public double[] getPdf() {
        double[] copy = new double[newPDF.length];
        System.arraycopy(newPDF, 0, copy, 0, newPDF.length);
        return copy;
    }

    @Override
    public AbstractAction copy() {
        return this; // immutable
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LoadDice other = (LoadDice) obj;
        return die == other.die && Arrays.equals(newPDF, other.newPDF);
    }

    @Override
    public int hashCode() {
        return die * 31 + Arrays.hashCode(newPDF);
    }

    @Override
    public String toString() {
        return "Load Die " + die + ", pdf = " + Arrays.toString(newPDF);
    }

    @Override
    public String getString(AbstractGameState gameState) {
        return toString();
    }
}
