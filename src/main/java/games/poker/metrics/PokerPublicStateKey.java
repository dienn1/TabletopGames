package games.poker.metrics;

import core.AbstractGameState;
import core.interfaces.IStateKey;
import games.poker.PokerGameState;

public class PokerPublicStateKey implements IStateKey {

    @Override
    public Object getKey(AbstractGameState state, int playerId) {
        PokerGameState pgs = (PokerGameState) state;
        return pgs.publicHash();
    }
}
