package players.mcts;

import core.actions.AbstractAction;
import utilities.Pair;

import java.util.List;

public class STNWithTestInstrumentation extends SingleTreeNode {

    @Override
    public double[] actionValues(List<AbstractAction> actions) {
        return super.actionValues(actions);
    }


    public ActionStats getActionStats(AbstractAction action) {
        return actionValues.get(action);
    }

    public AbstractAction treePolicyAction(boolean useExploration) {
        return super.treePolicyAction(useExploration);
    }

    public List<Pair<Integer, AbstractAction>> getActionsInRollout() {
        return actionsInRollout;
    }
    public List<Pair<Integer, AbstractAction>> getActionsInTree() {
        return actionsInTree;
    }
}
