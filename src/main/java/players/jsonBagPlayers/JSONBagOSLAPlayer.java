package players.jsonBagPlayers;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import players.simple.RandomPlayer;

import java.util.*;

public class JSONBagOSLAPlayer extends AbstractPlayer {

    JSONBagHeuristic heuristic;

    public JSONBagOSLAPlayer(List<Map<String, Integer>> prototypes, Collection<String> filterList, Random random) {
        super(null, "JSONBagOSLAPlayer");
        this.heuristic = new JSONBagHeuristic(prototypes, filterList);
//        this.heuristic.filterSet = Set.copyOf(filterList);
        this.rnd = random;
    }

    public JSONBagOSLAPlayer(List<Map<String, Integer>> prototypes, List<String> filterList) {
       this(prototypes, filterList, new Random());
    }

    // For copying only
    private JSONBagOSLAPlayer(Random random) {
        super(null, "JSONBagOSLAPlayer");
        this.rnd = random;
    }

    public void setFilterList(Collection<String> filterList) {
        this.heuristic.setFilterSet(filterList);
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gs, List<AbstractAction> actions) {

        // Update current JSON Bag
        this.heuristic.updateJSONBag(gs);

        double maxQ = Double.NEGATIVE_INFINITY;
        AbstractAction bestAction = null;
        double[] valState = new double[actions.size()];
        int playerID = gs.getCurrentPlayer();

        for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
            AbstractAction action = actions.get(actionIndex);
            AbstractGameState gsCopy = gs.copy();
            rollNextGameState(gsCopy, action.copy());

            // JSON-bag eval here
            valState[actionIndex] = heuristic.evaluateState(gsCopy, playerID);

//            double Q = noise(valState[actionIndex], getParameters().noiseEpsilon, rnd.nextDouble());
            double Q = valState[actionIndex];

            if (Q > maxQ || bestAction == null) {
                maxQ = Q;
                bestAction = action;
            }
        }

        return bestAction;
    }

    // Override this when need to roll toward endRound or endTurn (not just one step)
    protected void rollNextGameState(AbstractGameState gs, AbstractAction a) {
        getForwardModel().next(gs, a);

        // for simultaneous turn game
//        int currentRound = gs.getRoundCounter();
//        getForwardModel().next(gs, a);
//        RandomPlayer randomPlayer = new RandomPlayer(new Random(rnd.nextInt()));
//        List<AbstractAction> actions;
//        while (gs.getRoundCounter() == currentRound && gs.isNotTerminal()) {
//            actions = getForwardModel().computeAvailableActions(gs);
//            if (actions.isEmpty()) {
//                break;
//            }
//            getForwardModel().next(gs, randomPlayer.getAction(gs, actions));
//        }
    }

    @Override
    public void initializePlayer(AbstractGameState gameState) {
        heuristic.resetCurrentBag(gameState);
    }

    @Override
    public JSONBagOSLAPlayer copy() {
        JSONBagOSLAPlayer retValue = new JSONBagOSLAPlayer(new Random(rnd.nextInt()));
        retValue.heuristic = this.heuristic.copy();
        retValue.setForwardModel(getForwardModel());
        return retValue;
    }
}
