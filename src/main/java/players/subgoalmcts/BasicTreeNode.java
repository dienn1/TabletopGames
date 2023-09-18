package players.subgoalmcts;
import core.AbstractGameState;
import core.actions.AbstractAction;
import core.actions.MacroAction;
import core.interfaces.ISubGoal;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class BasicTreeNode {
    // Root node of tree
    BasicTreeNode root, rootSubGoal;
    // Parent of this node
    BasicTreeNode parent;

    // Parent of this node
    BasicTreeNode subgoalParent;

    // Children of this node
    Map<AbstractAction, BasicTreeNode> children = new HashMap<>();
    Map<MacroAction, BasicTreeNode> subGoalChildren = new HashMap<>();
    // Depth of this node
    final int depth;

    // Total value of this node
    private double totValue;
    // Number of visits
    private int nVisits;
    // Number of FM calls and State copies up until this node
    private int fmCallsCount;
    // Parameters guiding the search
    private BasicMCTSPlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    // State in this node (closed loop)
    private AbstractGameState state;

    protected BasicTreeNode(BasicMCTSPlayer player, BasicTreeNode parent, AbstractGameState state, Random rnd) {
        this.player = player;
        this.fmCallsCount = 0;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        this.rootSubGoal = parent == null? this : parent.rootSubGoal == null? root : parent.rootSubGoal;
        totValue = 0.0;
        setState(state);
        if (parent != null) {
            depth = parent.depth + 1;
        } else {
            depth = 0;
        }
        this.rnd = rnd;
        randomPlayer.setForwardModel(player.getForwardModel());
    }

    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void mctsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(player.params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            BasicTreeNode selected = treePolicy();
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition
            PlayerConstants budgetType = player.params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > player.params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private BasicTreeNode treePolicy() {

        BasicTreeNode cur = this;
        List<AbstractAction> sequence = new ArrayList<>();
        List<AbstractGameState> stateSequence = new ArrayList<>();

        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal() && cur.depth < player.params.maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand(sequence, stateSequence);
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                boolean toSubgoal = actionChosen instanceof MacroAction;
                sequence.add(actionChosen);
                stateSequence.add(cur.state);
                if(toSubgoal)
                    cur = cur.subGoalChildren.get(actionChosen);
                else
                    cur = cur.children.get(actionChosen);
            }
        }

        return cur;
    }


    private void setState(AbstractGameState newState) {
        state = newState;
        for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, player.params.actionSpace)) {
            children.put(action, null); // mark a new node to be expanded
        }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */
    private BasicTreeNode expand(List<AbstractAction> sequence, List<AbstractGameState> stateSequence) {
        // Find random child not already created
        Random r = new Random(player.params.getRandomSeed());
        // pick a random unchosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));
        sequence.add(chosen);
        stateSequence.add(state);

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node
        BasicTreeNode tn = new BasicTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);

        // TODO We can also try to manage subgoals for the opponent.
        if (state.getCurrentPlayer() == player.getPlayerID() &&
                nextState instanceof ISubGoal && ((ISubGoal)nextState).isSubGoal(state, chosen) &&
                !rootSubGoal.containsSubgoal(nextState)) {

            MacroAction macroAction = new MacroAction(state.getCurrentPlayer(), sequence, stateSequence);
            // Create link from root or previous subgoal in this branch
            rootSubGoal.subGoalChildren.put(macroAction, tn);
            tn.subgoalParent = rootSubGoal;
        }
        return tn;
    }

    public boolean containsSubgoal(AbstractGameState gs)
    {
        //Iterate over all subgoal states
        for (MacroAction macroAction : subGoalChildren.keySet())
            //If the current state is a subgoal state
            if (macroAction.getFinalStateHash() == gs.hashCode())
                return true;

        return false;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private double ucb1Value(BasicTreeNode node)
    {
        // Find child value
        double hvVal = node.totValue;
        double childValue = hvVal / (node.nVisits + player.params.epsilon);

        // default to standard UCB
        double explorationTerm = player.params.K * Math.sqrt(Math.log(this.nVisits + 1) / (node.nVisits + player.params.epsilon));
        // unless we are using a variant

        // Find 'UCB' value
        // If 'we' are taking a turn we use classic UCB
        // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
        boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
        double uctValue = iAmMoving ? childValue : -childValue;
        uctValue += explorationTerm;

        // Apply small noise to break ties randomly
        uctValue = noise(uctValue, player.params.epsilon, player.rnd.nextDouble());

        return uctValue;
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        //Normal children of the node.
        double bias;
        if(player.params.useBiasDecay) {
            //Goes from 1 to 0. Reaches 0 when the number of visits of this node is equal to the biasDecayValue param
            bias = Math.max(0, (player.params.biasDecayValue - nVisits) / player.params.biasDecayValue);
        }else if (player.params.useSubgoalBias){
            bias = 1 - player.params.subgoalBias;
        }else
            bias = 1;

        for (AbstractAction action : children.keySet()) {
            BasicTreeNode node = children.get(action);
            if (node == null)
                throw new AssertionError("Should not be here");
            if (bestAction == null)
                bestAction = action;

            double uctValue = bias * ucb1Value(node);

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        //Subgoal children of the node
        if (player.params.useSubgoalBias || player.params.useBiasDecay)
            bias = 1 - bias;
        else bias = 1;

        for (MacroAction action : subGoalChildren.keySet()) {
            BasicTreeNode node = subGoalChildren.get(action);
            if (node == null)
                throw new AssertionError("Should not be here");

            double uctValue = bias * ucb1Value(node);

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        root.fmCallsCount++;  // log one iteration complete
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut() {
        // TODO subgoals could be here too (issue: what's the natural parent of the subgoal?)
        int rolloutDepth = 0; // counting from end of tree

        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        if (player.params.rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
                AbstractAction next = randomPlayer.getAction(rolloutState, randomPlayer.getForwardModel().computeAvailableActions(rolloutState, randomPlayer.parameters.actionSpace));
                advance(rolloutState, next);
                rolloutDepth++;
            }
        }
        // Evaluate final state and return normalised score
        double value = player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Illegal heuristic value - should be a number");
        return value;
    }

    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= player.params.rolloutLength)
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        // TODO: should we favour shorter macro-actions?
        BasicTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            if(player.params.backUpPolicy == MCTSParams.BackUpPolicy.SUBGOAL_PARENT){
                n = (n.subgoalParent != null) ? n.subgoalParent : n.parent;
            }else if(player.params.backUpPolicy == MCTSParams.BackUpPolicy.NATURAL_PARENT){
                n = n.parent;
            }else if(player.params.backUpPolicy == MCTSParams.BackUpPolicy.BOTH) {
                if(n.subgoalParent != null)
                    n.subgoalParent.backUp(result);
                n = n.parent;
            }
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        if(player.params.recommendationPolicy == MCTSParams.RecommendationPolicy.STANDARD || subGoalChildren.size() == 0)
            for (AbstractAction action : children.keySet()) {
                if (children.get(action) != null) {
                    BasicTreeNode node = children.get(action);
                    double childValue = node.nVisits;

                    // Apply small noise to break ties randomly
                    childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                    // Save best value (highest visit count)
                    if (childValue > bestValue) {
                        bestValue = childValue;
                        bestAction = action;
                    }
                }
            }

        if(player.params.recommendationPolicy == MCTSParams.RecommendationPolicy.SUBGOALS)
            for (MacroAction action : subGoalChildren.keySet()) {
                if (subGoalChildren.get(action) != null) {
                    BasicTreeNode node = subGoalChildren.get(action);
                    double childValue = node.nVisits;

                    // Apply small noise to break ties randomly
                    childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                    // Save best value (highest visit count)
                    if (childValue > bestValue) {
                        bestValue = childValue;

                        AbstractAction atomicAction = action.getActions().get(0);
                        while (atomicAction instanceof MacroAction)
                            atomicAction = ((MacroAction) atomicAction).getActions().get(0);
                        bestAction = atomicAction;
                    }
                }
            }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

}
