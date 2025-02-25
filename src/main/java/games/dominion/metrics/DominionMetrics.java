package games.dominion.metrics;

import core.interfaces.IGameEvent;
import evaluation.listeners.MetricsGameListener;
import evaluation.metrics.AbstractMetric;
import evaluation.metrics.Event;
import evaluation.metrics.IMetricsCollection;
import games.dominion.*;
import games.dominion.actions.DominionAction;
import games.dominion.actions.DominionAttackAction;
import games.dominion.actions.TrashCard;
import games.dominion.cards.CardType;

import java.util.*;

@SuppressWarnings("unused")
public class DominionMetrics implements IMetricsCollection {

    public static class CardsInSupplyGameEnd extends AbstractMetric {
        CardType[] cardTypes;

        public CardsInSupplyGameEnd(){
            super();
            cardTypes = CardType.values();
        }
        public CardsInSupplyGameEnd(String[] args) {
            super(args);
            cardTypes = new CardType[args.length];
            for (int i = 0; i < args.length; i++) {
                cardTypes[i] = CardType.valueOf(args[i]);
            }
        }

        @Override
        public boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            for (CardType type : cardTypes) {
                records.put(type.toString(), ((DominionGameState)e.state).cardsOfType(type, -1, DominionConstants.DeckType.SUPPLY));
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
            for (CardType type : cardTypes) {
                columns.put(type.toString(), Integer.class);
            }
            return columns;
        }
    }

    public static class EmptySupplySlots extends AbstractMetric {
        @Override
        public boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            records.put("EmptySupplySlots", (int)((DominionGameState)e.state).cardsIncludedInGame().stream()
                    .filter(c -> ((DominionGameState)e.state).cardsOfType(c, -1, DominionConstants.DeckType.SUPPLY) == 0)
                    .count());
            return true;
        }

        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            return Collections.singletonMap("EmptySupplySlots", Integer.class);
        }
    }


    public static class GameSeeds extends AbstractMetric {

        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.GAME_OVER);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("InitialShuffle", Integer.class);
            return columns;
        }

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            DominionGameState state = (DominionGameState)e.state;
            DominionParameters params = (DominionParameters)state.getGameParameters();
            records.put("InitialShuffle", params.initialShuffleSeed);
            return true;
        }


    }

    public static class ActionFeatures extends AbstractMetric {

        public Set<IGameEvent> getDefaultEventTypes() {
            return Collections.singleton(Event.GameEvent.ACTION_CHOSEN);
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("Money", Integer.class);
            columns.put("Actions", Integer.class);
            columns.put("Buys", Integer.class);
            return columns;
        }

        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            DominionGameState state = (DominionGameState)e.state;
            records.put("Money", state.getAvailableSpend(e.playerID));
            records.put("Actions", state.getActionsLeft());
            records.put("Buys", state.getBuysLeft());
            return true;
        }
    }

    public static class DominionActionValue extends AbstractMetric {

        int plusActions = 0;
        int plusDraws = 0;
        int plusBuys = 0;
        int plusMoney = 0;
        int attackCount = 0;
        int trashCardCount = 0;
        @Override
        protected boolean _run(MetricsGameListener listener, Event e, Map<String, Object> records) {
            if (e.type == Event.GameEvent.ACTION_CHOSEN) {
                if (e.action instanceof DominionAction dAction) {
                    plusActions += dAction.type.plusActions;
                    plusDraws += dAction.type.plusDraws;
                    plusBuys += dAction.type.plusBuys;
                    plusMoney += dAction.type.plusMoney;
                }
                if (e.action instanceof DominionAttackAction) {
                    attackCount++;
                }
                else if (e.action instanceof TrashCard) {
                    trashCardCount++;
                }
                return false;
            }
            if (e.type == Event.GameEvent.GAME_OVER) {
                records.put("plusActions", plusActions);
                records.put("plusDraws", plusDraws);
                records.put("plusBuys", plusBuys);
                records.put("plusMoney", plusMoney);
                records.put("attackCount", attackCount);
                records.put("trashCardCount", trashCardCount);
                return true;
            }
            return false;
        }

        @Override
        public Set<IGameEvent> getDefaultEventTypes() {
            return Set.of(Event.GameEvent.ACTION_CHOSEN, Event.GameEvent.GAME_OVER);
        }

        @Override
        public void notifyGameOver() {
            super.notifyGameOver();
            plusActions = 0;
            plusDraws = 0;
            plusBuys = 0;
            plusMoney = 0;
            attackCount = 0;
            trashCardCount = 0;
        }

        @Override
        public Map<String, Class<?>> getColumns(int nPlayersPerGame, Set<String> playerNames) {
            Map<String, Class<?>> columns = new HashMap<>();
            columns.put("plusActions", Integer.class);
            columns.put("plusDraws", Integer.class);
            columns.put("plusBuys", Integer.class);
            columns.put("plusMoney", Integer.class);
            columns.put("attackCount", Integer.class);
            columns.put("trashCardCount", Integer.class);
            return columns;
        }
    }
}
