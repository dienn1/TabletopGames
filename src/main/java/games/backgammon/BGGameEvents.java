package games.backgammon;

import core.interfaces.IGameEvent;

import java.util.Set;

public enum BGGameEvents implements IGameEvent {

    CheatingDetected,
    Blot;

    @Override
    public Set<IGameEvent> getValues() {
        return Set.of(BGGameEvents.values());
    }
}
