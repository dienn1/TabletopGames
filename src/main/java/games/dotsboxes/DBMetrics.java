package games.dotsboxes;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;

import java.util.*;

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

    // TODO make this a generic class in GameMetrics
    public static class ScoresPerTurn extends AbstractMetric {

        ArrayList<Double> scoreArrayPerTurn = new ArrayList<>();
        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.TURN_OVER && e.state.getTurnCounter() % 2 == 0) {
                for (int i=0; i<e.state.getNPlayers(); i++) {
                    scoreArrayPerTurn.add(e.state.getGameScore(i));
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                for (int i=0; i<e.state.getNPlayers(); i++) {
                    scoreArrayPerTurn.add(e.state.getGameScore(i));
                }
                String scoreArrayStr = scoreArrayPerTurn.toString();
                records.put("ScoresPerTurn", scoreArrayStr);
                return true;
            }
            return false;
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            scoreArrayPerTurn.clear();
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.TURN_OVER, Event.GameEvent.GAME_OVER);
        }
        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("ScoresPerTurn", String.class);
            return columns;
        }
    }
}
