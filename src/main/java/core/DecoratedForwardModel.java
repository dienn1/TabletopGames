package core;

import core.actions.AbstractAction;
import core.interfaces.IPlayerDecorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecoratedForwardModel extends AbstractForwardModel {

    // This wraps a Forward Model in one or more Decorators that modify (restrict) the actions available to the player.
    // This enables the Forward Model to be passed to the decision algorithm (e.g. MCTS), and ensure that any
    // restrictions are applied to the actions available to the player during search, and not just
    // in the main game loop.

    // most function calls are forwarded to the wrapped forward model, except for
    // _computeAvailableActions, to which we first apply the decorators

    protected Map<Integer, List<IPlayerDecorator>> playerSpecificDecorators = new HashMap<>();
    protected List<IPlayerDecorator> generalDecorators = new ArrayList<>();
    final AbstractForwardModel wrappedFM;

    public DecoratedForwardModel(AbstractForwardModel forwardModel) {
        this.wrappedFM = forwardModel;
    }

    public DecoratedForwardModel addDecorator(int playerId, IPlayerDecorator decorator) {
        if (playerId == -1)
            generalDecorators.add(decorator);
        else {
            if (!playerSpecificDecorators.containsKey(playerId)) {
                playerSpecificDecorators.put(playerId, new ArrayList<>());
            }
            playerSpecificDecorators.get(playerId).add(decorator);
        }
        return this;
    }

    public void clearDecorators() {
        generalDecorators.clear();
        for (List<IPlayerDecorator> decorators : playerSpecificDecorators.values()) {
            decorators.clear();
        }
    }

    @Override
    protected void _setup(AbstractGameState firstState) {
        wrappedFM._setup(firstState);
    }

    @Override
    protected void _next(AbstractGameState currentState, AbstractAction action) {
        for (IPlayerDecorator decorator : generalDecorators) {
            decorator.recordDecision(currentState, action);
        }
        if (playerSpecificDecorators.containsKey(currentState.getCurrentPlayer())) {
            for (IPlayerDecorator decorator : playerSpecificDecorators.get(currentState.getCurrentPlayer())) {
                decorator.recordDecision(currentState, action);
            }
        }
        wrappedFM._next(currentState, action);
    }

    @Override
    protected List<AbstractAction> _computeAvailableActions(AbstractGameState gameState) {
        List<AbstractAction> actions = wrappedFM.computeAvailableActions(gameState);

        // Then apply Decorators regardless of source of actions
        for (IPlayerDecorator decorator : generalDecorators) {
            actions = decorator.actionFilter(gameState, actions);
        }
        if (playerSpecificDecorators.containsKey(gameState.getCurrentPlayer())) {
            for (IPlayerDecorator decorator : playerSpecificDecorators.get(gameState.getCurrentPlayer())) {
                actions = decorator.actionFilter(gameState, actions);
            }
        }
        return actions;
    }

    @Override
    protected void endPlayerTurn(AbstractGameState state) {
        wrappedFM.endPlayerTurn(state);
    }
}
