package games.dotsboxes;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;

import java.util.Collections;
import java.util.Set;

public class DBMetrics implements IMetricsCollection {
    public static class SaveStateDB extends GameMetrics.SaveStateOnEvent {


        public SaveStateDB(String[] args) {
            super(args);
        }

        @Override
        protected AbstractGameStateContainer getGSContainer(AbstractGameState gs) {
            return new DBGameStateContainer((DBGameState) gs);
        }

        @Override
        protected boolean isValidSave(Event e) {
            return e.state.getTurnCounter() % 2 == 0;
        }

        @Override
        protected Set<IGameEvent> getSaveEventTypes() {
            return Collections.singleton(Event.GameEvent.TURN_OVER);
        }
    }
}
