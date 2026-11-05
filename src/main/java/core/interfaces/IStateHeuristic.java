package core.interfaces;

import core.AbstractGameState;

public interface IStateHeuristic {

    /**
     * Returns a score for the state that should be maximised by the player (the higher, the better).
     * Ideally bounded between [-1, 1].
     * @param gs - game state to evaluate and score.
     * @param playerId - id of the player we're evaluating the game for.
     * @return - value of given state.
     */
    double evaluateState(AbstractGameState gs, int playerId);

    /**
     * Called once at the start of a game, with the player's initial observation.
     * A stateful heuristic uses this to start a fresh trajectory. The default does nothing.
     * @param gs - the initial observed state.
     */
    default void reset(AbstractGameState gs) {
    }

    /**
     * Called once per decision, with the state the player is about to act on.
     * A stateful heuristic uses this to add the observation to its running trajectory. The default does nothing.
     * @param gs - the observed state for this decision.
     */
    default void observe(AbstractGameState gs) {
    }

    /**
     * A copy of this heuristic for a different player instance. Heuristics that carry mutable state must
     * override this to return an independent copy; stateless heuristics return themselves.
     * <p>
     * Not named {@code copy()} on purpose: {@link evaluation.optimisation.TunableParameters} already declares
     * {@code public TunableParameters copy()}, and many of this interface's implementers extend it. Two methods
     * with that signature and unrelated return types cannot coexist in one class.
     * @return an independent heuristic, or this one if it holds no mutable state.
     */
    default IStateHeuristic copyHeuristic() {
        return this;
    }

    /**
     * The value of one state for every player, evaluated in a single pass. The default calls
     * {@link #evaluateState(AbstractGameState, int)} once per player. Heuristics whose per-state work is
     * expensive (a tokenisation, a network forward pass) override it to do that work once.
     * @param gs - game state to evaluate.
     * @return one value per player, indexed by player id.
     */
    default double[] evaluateAll(AbstractGameState gs) {
        double[] values = new double[gs.getNPlayers()];
        for (int i = 0; i < values.length; i++)
            values[i] = evaluateState(gs, i);
        return values;
    }

    default double minValue() {
        return -1;
    }

    default double maxValue() {
        return +1;
    }
}
