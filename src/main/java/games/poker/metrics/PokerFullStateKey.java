package games.poker.metrics;

import core.AbstractGameState;
import core.interfaces.IStateKey;
import games.poker.PokerGameState;

public class PokerFullStateKey implements IStateKey {
    @Override
    public Object getKey(AbstractGameState state, int playerId) {
        // public hash plus our own hand
        PokerGameState pgs = (PokerGameState) state;
        return pgs.publicHash() - 941 * pgs.getPlayerDecks().get(playerId).hashCode();
    }
}
