package games.seasaltpaper.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;

public class Stop extends AbstractAction {

    int playerId;


    public Stop(int playerId) {
        this.playerId = playerId;
    }

    @Override
    public boolean execute(AbstractGameState gs) {
        return false;
    }

    @Override
    public AbstractAction copy() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String getString(AbstractGameState gameState) {
        return "Player " + playerId + " declares \"STOP!\"";
    }
}
