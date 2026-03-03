package players.jsonBagPlayers;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.interfaces.IStateHeuristic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import players.jsonBagPlayers.Tokenizer;

public class JSONBagHeuristic implements IStateHeuristic {

    Map<String, Integer> currentJSONBag;
    List<String> filterList;

    public JSONBagHeuristic() {
        currentJSONBag = new LinkedHashMap<String, Integer>();
    }
    public JSONBagHeuristic(List<String> filterList_) {
        filterList = filterList_;
        currentJSONBag = new LinkedHashMap<String, Integer>();
    }

    public void setFilterList(List<String> filterList_) {
        filterList = filterList_;
    }

    public void updateJSONBag(AbstractGameState gs) {
        AbstractGameStateContainer gsContainer = GameStateContainerFactory.createContainer(gs);
        Map<String, Integer> tokenizedGameState = Tokenizer.tokenize(gsContainer);
        Tokenizer.merge(currentJSONBag, tokenizedGameState);
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        return 0;
    }
}
