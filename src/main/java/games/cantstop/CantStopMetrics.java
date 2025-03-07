package games.cantstop;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;

import java.util.*;

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

    public static class GameParams extends AbstractMetric {

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            CantStopParameters params = (CantStopParameters) e.state.getGameParameters();
            records.put("TWO_MAX", params.TWO_MAX);
            records.put("THREE_MAX", params.THREE_MAX);
            records.put("FOUR_MAX", params.FOUR_MAX);
            records.put("FIVE_MAX", params.FIVE_MAX);
            records.put("SIX_MAX", params.SIX_MAX);
            records.put("SEVEN_MAX", params.SEVEN_MAX);
            records.put("EIGHT_MAX", params.EIGHT_MAX);
            records.put("NINE_MAX", params.NINE_MAX);
            records.put("TEN_MAX", params.TEN_MAX);
            records.put("ELEVEN_MAX", params.ELEVEN_MAX);
            records.put("TWELVE_MAX", params.TWELVE_MAX);
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>>  columns = new HashMap<>();
            columns.put("TWO_MAX", Integer.class);
            columns.put("THREE_MAX", Integer.class);
            columns.put("FOUR_MAX", Integer.class);
            columns.put("FIVE_MAX", Integer.class);
            columns.put("SIX_MAX", Integer.class);
            columns.put("SEVEN_MAX", Integer.class);
            columns.put("EIGHT_MAX", Integer.class);
            columns.put("NINE_MAX", Integer.class);
            columns.put("TEN_MAX", Integer.class);
            columns.put("ELEVEN_MAX", Integer.class);
            columns.put("TWELVE_MAX", Integer.class);
            return columns;
        }
    }
}
