package games.backgammon.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.backgammon.BGGameState;

public class RollDice extends AbstractAction {

    @Override
    public boolean execute(AbstractGameState gs) {
        BGGameState state = (BGGameState) gs;
        state.rollDice();
        return true;
    }

    @Override
    public AbstractAction copy() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RollDice;
    }

    @Override
    public int hashCode() {
        return 430234;
    }


    @Override
    public String toString() {
        return "Roll Dice";
    }

    @Override
    public String getString(AbstractGameState gameState) {
        return toString();
    }
}
