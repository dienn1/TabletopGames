package games.connect4;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Connect4Metrics implements IMetricsCollection {

    public static class SaveStateConnect4 extends GameMetrics.SaveStateOnEvent {

        public SaveStateConnect4(String[] args) {
            super(args);
        }

        @Override
        protected AbstractGameStateContainer getGSContainer(AbstractGameState gs) {
            return new Connect4GameStateContainer((Connect4GameState) gs);
        }

        @Override
        protected Set<IGameEvent> getSaveEventTypes() {
            return Collections.singleton(Event.GameEvent.ROUND_OVER);
        }
    }

    public static class GameParams extends AbstractMetric {

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Connect4GameParameters params = (Connect4GameParameters) e.state.getGameParameters();
            records.put("gridSize", params.gridSize);
            records.put("winCount", params.winCount);
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("gridSize", Integer.class);
            columns.put("winCount", Integer.class);
            return columns;
        }
    }

}
