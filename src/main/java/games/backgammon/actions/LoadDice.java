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

    public LoadDice(int die, double[] newPDF) {
        this.newPDF = newPDF;
        this.die = die;
    }

    @Override
    public boolean execute(AbstractGameState gs) {
        BGGameState state = (BGGameState) gs;
        state.setDicePdf(die, newPDF);
        return true;
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
