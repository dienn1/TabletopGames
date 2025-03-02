package games.wonders7.metrics;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.actions.AbstractAction;
import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.GameMetrics;
import evaluation.metrics.IMetricsCollection;
import games.wonders7.Wonders7Constants;
import games.wonders7.Wonders7GameParameters;
import games.wonders7.Wonders7GameState;
import games.wonders7.Wonders7GameStateContainer;
import games.wonders7.actions.*;
import games.wonders7.cards.Wonder7Card;

import java.util.*;


public class Wonders7Metrics implements IMetricsCollection {

    public static class GameSeeds extends AbstractMetric {
        @Override
        public boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Wonders7GameState state = (Wonders7GameState) e.state;
            Wonders7GameParameters params = (Wonders7GameParameters) state.getGameParameters();
            records.put("CardSeed", params.cardShuffleSeed);
            records.put("WonderSeed", params.wonderShuffleSeed);
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return new HashSet<>(Collections.singletonList(Event.GameEvent.GAME_OVER));
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new LinkedHashMap<>();
            columns.put("CardSeed", Integer.class);
            columns.put("WonderSeed", Integer.class);
            return columns;
        }
    }


    public static class Boards extends AbstractMetric {
        @Override
        public boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Wonders7GameState state = (Wonders7GameState) e.state;
            for (int i = 0; i < state.getNPlayers(); i++) {
                records.put("Player " + i +  " Board", state.getPlayerWonderBoard(i).wonderType().name());
            }
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return new HashSet<>(Collections.singletonList(Event.GameEvent.GAME_OVER));
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new LinkedHashMap<>();
            for (int i = 0; i < nPlayersPerGame; i++) {
                columns.put("Player " + i +  " Board", String.class);
            }
            return columns;
        }
    }

    public static class ActionsCount extends GameMetrics.ActionsCount {

        @Override
        protected void updateActionCount(AbstractAction a) {
            if (a instanceof ChooseCard c) {
                a = c.actionChosen;
            }
            if (actionsTracked.contains(a.getClass())) {
                String actionName = a.getClass().getSimpleName();
                actionsCount.put(actionName, actionsCount.getOrDefault(actionName, 0) + 1);
            }
        }

        @Override
        protected void initializeActionsTracked() {
            actionsTracked = Set.of(BuildFromDiscard.class, BuildStage.class, DiscardCard.class, PlayCard.class);
        }
    }

    public static class CardTypeCount extends AbstractMetric {

        HashMap<String, Integer> cardTypeCount = new HashMap<>();
        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.ACTION_CHOSEN) {
                AbstractAction a = e.action;
                if (a instanceof ChooseCard c) {
                    a = c.actionChosen;
                }
                if (a instanceof PlayCard p) {
                    Wonder7Card card = Wonder7Card.factory(p.cardType);
                    cardTypeCount.put(card.buildingType.name(), cardTypeCount.getOrDefault(card.buildingType.name(), 0) + 1);
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                for (Wonder7Card.Type cardType : Wonder7Card.Type.values()) {
                    records.put(cardType.name(), cardTypeCount.getOrDefault(cardType.name(), 0));
                }
                return true;
            }
            return false;
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            cardTypeCount.clear();
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.ACTION_CHOSEN, Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (Wonder7Card.Type cardType : Wonder7Card.Type.values()) {
                columns.put(cardType.name(), Integer.class);
            }
            return columns;
        }
    }

    public static class ResourcesPerPlayer extends AbstractMetric {

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Wonders7GameState wgs = (Wonders7GameState) e.state;
            for (int i=0; i < e.state.getNPlayers(); i++) {
                for (Wonders7Constants.Resource r : Wonders7Constants.Resource.values()) {
                    records.put("Player-" + i + "." + r.name(), wgs.getResource(i, r));
                }
            }
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (int i=0; i<nPlayersPerGame; i++) {
                for (Wonders7Constants.Resource r : Wonders7Constants.Resource.values()) {
                    columns.put("Player-" + i + "." + r.name(), Integer.class);
                }
            }
            return columns;
        }
    }

    public static class Resources extends AbstractMetric {

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Wonders7GameState wgs = (Wonders7GameState) e.state;
            for (Wonders7Constants.Resource r : Wonders7Constants.Resource.values()) {
                int resourceCount = 0;
                for (int i = 0; i < e.state.getNPlayers(); i++) {
                    resourceCount += wgs.getResource(i, r);
                }
                records.put(r.name(), resourceCount);
            }
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            for (Wonders7Constants.Resource r : Wonders7Constants.Resource.values()) {
                columns.put(r.name(), Integer.class);
            }
            return columns;
        }
    }

    public static class SaveStateWonders7 extends GameMetrics.SaveStateOnEvent {

        public SaveStateWonders7(String[] args) {
            super(args);
        }

        @Override
        protected AbstractGameStateContainer getGSContainer(AbstractGameState gs) {
            return new Wonders7GameStateContainer((Wonders7GameState) gs);
        }

        @Override
        public Set<IGameEvent> getSaveEventTypes() {
            return Collections.singleton(Event.GameEvent.ROUND_OVER);
        }
    }

    public static class GameParams extends AbstractMetric {

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            Wonders7GameParameters params = (Wonders7GameParameters) e.state.getGameParameters();
            records.put("nCostNeighbourResource", params.nCostNeighbourResource);
            records.put("nCostDiscountedResource", params.nCostDiscountedResource);
            records.put("nCoinsDiscard", params.nCoinsDiscard);
            records.put("startingCoins", params.startingCoins);
            return true;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("nCostNeighbourResource", Integer.class);
            columns.put("nCostDiscountedResource", Integer.class);
            columns.put("nCoinsDiscard", Integer.class);
            columns.put("startingCoins", Integer.class);
            return columns;
        }
    }

}