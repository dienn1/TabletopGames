package players.simple;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import evaluation.metrics.Tokenizer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JSONBagHeuristic implements IStateHeuristic {

    Map<String, Integer> currentJSONBag;

    public JSONBagHeuristic() {
        currentJSONBag = new LinkedHashMap<String, Integer>();
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        return 0;
    }
}
