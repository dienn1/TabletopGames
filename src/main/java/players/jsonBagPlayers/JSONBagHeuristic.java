package players.jsonBagPlayers;

import core.AbstractGameState;
import core.AbstractGameStateContainer;
import core.CoreConstants;
import core.interfaces.IStateHeuristic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import players.jsonBagPlayers.Tokenizer;

import static players.jsonBagPlayers.JensenShannonDistance.jsd;

public class JSONBagHeuristic implements IStateHeuristic {

    Map<String, Integer> currentJSONBag;
    List<String> filterList;

    final List<Map<String, Integer>> prototypes;

    public JSONBagHeuristic(List<Map<String, Integer>> prototypes_) {
        this(prototypes_, new ArrayList<>());
    }
    public JSONBagHeuristic(List<Map<String, Integer>> prototypes_, List<String> filterList_) {
        prototypes = prototypes_;
        filterList = filterList_;
        currentJSONBag = new LinkedHashMap<String, Integer>();
    }

    public void setFilterList(List<String> filterList_) {
        filterList = filterList_;
    }

    public void updateJSONBag(AbstractGameState gs) {
        Map<String, Integer> tokenizedGameState = Tokenizer.tokenize(gs);
        Tokenizer.filter(tokenizedGameState, filterList,true);
        Tokenizer.merge(currentJSONBag, tokenizedGameState);
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        if (gs.getNPlayers() != prototypes.size()) {
            throw new RuntimeException("PLAYER COUNT AND PROTOTYPE COUNT MISMATCHED: " + gs.getNPlayers() + " vs. " + prototypes.size());
        }
        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.LOSE_GAME)
            return 0;
        if (gs.getPlayerResults()[playerId] == CoreConstants.GameResult.WIN_GAME)
            return 1.0;
        Map<String, Integer> newJSONBag = new LinkedHashMap<>(currentJSONBag);
        Map<String, Integer> newGameStateTokenized = Tokenizer.tokenize(gs);
        Tokenizer.filter(newGameStateTokenized, filterList,true);
        Tokenizer.merge(newJSONBag, newGameStateTokenized);

        double[] distances = new double[prototypes.size()];
        for (int i = 0; i < prototypes.size(); i++) {
            distances[i] = jsd(prototypes.get(i), newJSONBag);
        }
        double[] values = invertNormalized(distances);
//        values = softmax(values, 1.0);
        return values[playerId];
    }

    private double[] invertNormalized(double[] distancesArray) {
        double[] ret = new double[distancesArray.length];
        double sum = 0.0;
        for (int i = 0; i < distancesArray.length; i++) {
            ret[i] = 1.0 / distancesArray[i];
            sum += ret[i];
        }
        for (int i = 0; i < ret.length; i++) {
            ret[i] /= sum;
        }
        return ret;
    }

    private double[] softmax(double[] values, double temperature) {
        double[] ret = new double[values.length];
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            if (v > max) max = v;
        }
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            ret[i] = Math.exp((values[i] - max) / temperature);
            sum += ret[i];
        }
        for (int i = 0; i < ret.length; i++) {
            ret[i] /= sum;
        }
        return ret;
    }

    public JSONBagHeuristic copy() {
        JSONBagHeuristic copy = new JSONBagHeuristic(prototypes, new ArrayList<>(filterList));
        copy.currentJSONBag = new LinkedHashMap<>(currentJSONBag);
        return copy;
    }

    @Override
    public double minValue() {
        return 0;
    }
}
