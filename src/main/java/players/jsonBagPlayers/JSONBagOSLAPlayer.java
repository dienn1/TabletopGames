package players.jsonBagPlayers;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import players.simple.OSLAPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JSONBagOSLAPlayer extends OSLAPlayer {

    public JSONBagOSLAPlayer(Random random) {
        super(random);
        setName("JSONBagOSLAPlayer");
    }

    public JSONBagOSLAPlayer() {
        super();
        setName("JSONBagOSLAPlayer");
    }
}
