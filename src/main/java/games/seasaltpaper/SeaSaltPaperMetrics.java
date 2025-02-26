package games.seasaltpaper;

import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;
import games.seasaltpaper.actions.*;
import games.seasaltpaper.cards.CardColor;
import games.seasaltpaper.cards.CardSuite;
import games.seasaltpaper.cards.CardType;
import games.seasaltpaper.cards.SeaSaltPaperCard;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SeaSaltPaperMetrics implements IMetricsCollection {

    public static class ActionsCount extends GameMetrics.ActionsCount {

        @Override
        protected void initializeActionsTracked() {
            actionsTracked = Set.of(BoatDuo.class, CrabDuo.class, FishDuo.class, SwimmerSharkDuo.class, DrawAndDiscard.class, LastChance.class, Stop.class);
        }
    }

    public static class cardTypeCount extends AbstractMetric {

        HashMap<String, Integer> cardTypeCount = new HashMap<>();
        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.ROUND_OVER) {
                SeaSaltPaperGameState sspgs = (SeaSaltPaperGameState) e.state;
                for (int i = 0; i < sspgs.getNPlayers(); i++) {
                    for (SeaSaltPaperCard c : sspgs.getPlayerHands().get(i)) {
                        cardTypeCount.put(c.getCardType().name(), cardTypeCount.getOrDefault(c.getCardType().name(), 0) + 1);
                    }
                    for (SeaSaltPaperCard c : sspgs.getPlayerDiscards().get(i)) {
                        cardTypeCount.put(c.getCardType().name(), cardTypeCount.getOrDefault(c.getCardType().name(), 0) + 1);
                    }
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                for (CardType t : CardType.values()) {
                    records.put(t.name(), cardTypeCount.getOrDefault(t.name(), 0));
                }
                return true;
            }
            return false;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.ROUND_OVER, Event.GameEvent.GAME_OVER);
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            cardTypeCount.clear();
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (CardType t : CardType.values()) {
                columns.put(t.name(), Integer.class);
            }
            return columns;
        }
    }

    public static class cardSuiteCount extends AbstractMetric {
        HashMap<String, Integer> cardSuiteCount = new HashMap<>();
        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.ROUND_OVER) {
                SeaSaltPaperGameState sspgs = (SeaSaltPaperGameState) e.state;
                for (int i = 0; i < sspgs.getNPlayers(); i++) {
                    for (SeaSaltPaperCard c : sspgs.getPlayerHands().get(i)) {
                        cardSuiteCount.put(c.getCardSuite().name(), cardSuiteCount.getOrDefault(c.getCardSuite().name(), 0) + 1);
                    }
                    for (SeaSaltPaperCard c : sspgs.getPlayerDiscards().get(i)) {
                        cardSuiteCount.put(c.getCardSuite().name(), cardSuiteCount.getOrDefault(c.getCardSuite().name(), 0) + 1);
                    }
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                for (CardSuite t : CardSuite.values()) {
                    records.put(t.name(), cardSuiteCount.getOrDefault(t.name(), 0));
                }
                return true;
            }
            return false;
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            cardSuiteCount.clear();
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.ROUND_OVER, Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (CardSuite t : CardSuite.values()) {
                columns.put(t.name(), Integer.class);
            }
            return columns;
        }
    }

    public static class cardColorCount extends AbstractMetric {
        HashMap<String, Integer> cardColorCount = new HashMap<>();

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.ROUND_OVER) {
                SeaSaltPaperGameState sspgs = (SeaSaltPaperGameState) e.state;
                for (int i = 0; i < sspgs.getNPlayers(); i++) {
                    for (SeaSaltPaperCard c : sspgs.getPlayerHands().get(i)) {
                        cardColorCount.put(c.getCardColor().name(), cardColorCount.getOrDefault(c.getCardColor().name(), 0) + 1);
                    }
                    for (SeaSaltPaperCard c : sspgs.getPlayerDiscards().get(i)) {
                        cardColorCount.put(c.getCardColor().name(), cardColorCount.getOrDefault(c.getCardColor().name(), 0) + 1);
                    }
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                for (CardColor t : CardColor.values()) {
                    records.put(t.name(), cardColorCount.getOrDefault(t.name(), 0));
                }
                return true;
            }
            return false;
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            cardColorCount.clear();
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.ROUND_OVER, Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (CardColor t : CardColor.values()) {
                columns.put(t.name(), Integer.class);
            }
            return columns;
        }
    }
}
