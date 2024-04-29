package games.toads.metrics;

import core.AbstractGameState;
import core.interfaces.IStateFeatureVector;
import core.interfaces.IStateKey;
import games.toads.ToadGameState;

public class ToadFeatures001 implements IStateFeatureVector, IStateKey {

    @Override
    public String[] names() {
        return new String[]{
                "ROUND",
                "HAND_1", "HAND_2", "HAND_3", "HAND_4",
                "SCORE_US", "SCORE_THEM",
                "FIELD_CARD", "FLANK_CARD",
                "THEIR_FIELD",
                "KNOWN_HAND_1", "KNOWN_HAND_2",
                "TIEBREAK_US", "TIEBREAK_THEM",
                "DISCARD_US", "DISCARD_THEM"
        };
    }

    @Override
    public double[] featureVector(AbstractGameState gs, int playerID) {
        double[] features = new double[16];
        ToadGameState state = (ToadGameState) gs;
        features[0] = state.getRoundCounter();
        int handSize = state.getPlayerHand(playerID).getSize();
        for (int i = 0; i < handSize; i++) {
            features[i + 1] = state.getPlayerHand(playerID).get(i).value;
        }
        features[5] = state.getGameScore(playerID);
        features[6] = state.getGameScore(1 - playerID);
        features[7] = state.getFieldCard(playerID) == null ? 0 : state.getFieldCard(playerID).value;
        features[8] = state.getHiddenFlankCard(playerID) == null ? 0 : state.getHiddenFlankCard(playerID).value;
        features[9] = state.getFieldCard(1 - playerID) == null ? 0 : state.getFieldCard(1 - playerID).value;
        int visibleCount = 0;
        for (int i = 0; i < state.getPlayerHand(1 - playerID).getSize(); i++) {
            if (state.getPlayerHand(1 - playerID).getVisibilityForPlayer(i, playerID)) {
                features[10 + visibleCount] = state.getPlayerHand(1 - playerID).get(i).value;
            }
            visibleCount++;
        }
        features[12] = state.getTieBreaker(playerID) == null ? 0 : state.getTieBreaker(playerID).value;
        features[13] = state.getTieBreaker(1 - playerID) == null ? 0 : state.getTieBreaker(1 - playerID).value;
        for (int i = 0; i < state.getDiscards(playerID).getSize(); i++) {
            features[14] += state.getDiscards(playerID).get(i).value * (i + 11);
        }
        for (int i = 0; i < state.getDiscards(1 - playerID).getSize(); i++) {
            features[15] += state.getDiscards(1 - playerID).get(i).value * (i + 11);
        }

        return features;
    }

    @Override
    public Integer getKey(AbstractGameState state) {
        int retValue = state.getCurrentPlayer();
        double[] features = featureVector(state, state.getCurrentPlayer());
        for (int i = 0; i < features.length; i++) {
            retValue += (int) (features[i] * ((i+1) * 31));
        }
        return retValue;
    }
}
