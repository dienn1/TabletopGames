package games.uno;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;
import games.catan.CatanGameState;
import games.catan.CatanGameStateContainer;

import java.util.Collections;
import java.util.Set;

public class UnoMetrics implements IMetricsCollection {
    public static class SaveStateUno extends GameMetrics.SaveStateOnEvent {

        public SaveStateUno(String[] args) {
            super(args);
        }

        @Override
        protected AbstractGameStateContainer getGSContainer(AbstractGameState gs) {
            return new UnoGameStateContainer((UnoGameState) gs);
        }

        @Override
        protected boolean isValidSave(Event e) {
            return e.state.getTurnCounter() % e.state.getNPlayers() == 0;
        }

        @Override
        protected Set<IGameEvent> getSaveEventTypes() {
            return Collections.singleton(Event.GameEvent.TURN_OVER);
        }
    }
}
