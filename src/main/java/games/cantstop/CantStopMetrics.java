package games.cantstop;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;

import java.util.Collections;
import java.util.Set;

public class CantStopMetrics implements IMetricsCollection {
    public static class SaveStateCantStop extends GameMetrics.SaveStateOnEvent {

        public SaveStateCantStop(String[] args) {
            super(args);
        }

        @Override
        protected AbstractGameStateContainer getGSContainer(AbstractGameState gs) {
            return new CantStopGameStateContainer((CantStopGameState) gs);
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
