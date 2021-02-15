package core.rules.rulenodes;

import core.AbstractGameState;
import core.rules.nodetypes.RuleNode;

/**
 * Executes an action requested by a player, if the action was set. Interrupts the game loop if no action was provided,
 * but not if the action fails execution.
 */
public class PlayerAction extends RuleNode {

    public PlayerAction() {
        super(true);
    }

    @Override
    protected boolean run(AbstractGameState gs) {
        if (action != null) {
            action._execute(gs);
            return true;
        }
        return false;
    }

}
