package players.jsonBagPlayers;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IStateHeuristic;

import java.util.LinkedHashMap;
import java.util.Map;

public class JSONBagHeuristic implements IStateHeuristic {

    Map<String, Integer> currentJSONBag;

    public JSONBagHeuristic() {
        currentJSONBag = new LinkedHashMap<String, Integer>();
    }

    public void updateJSONBag(AbstractGameStateContainer gsContainter) {

    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        return 0;
    }
}
