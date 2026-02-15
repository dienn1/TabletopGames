package games.virus.metrics;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IActionFeatureVector;
import games.virus.actions.*;

public class VirusActionFeatures implements IActionFeatureVector {

    static String[] allNames = new String[]{"AddOrgan", "ApplyMedicine", "ApplyVirus", "DrawNewPlayerHand",
            "PlayLatexGlove", "PlayMedicalError", "PlayOrganThief", "PlaySpreading", "PlayTransplant",
            "ReplaceAllCards", "ReplaceCard", "ReplaceOneCard",
            "OpponentScore",};

    @Override
    public String[] names() {
        return allNames;
    }

    @Override
    public double[] doubleVector(AbstractAction action, AbstractGameState state, int playerID) {
        double[] retValue = new double[allNames.length];
        int opponent = -1;

        switch (action) {
            case AddOrgan ignored -> retValue[0] = 1.0;
            case ApplyMedicine ignored -> retValue[1] = 1.0;
            case ApplyVirus av -> {
                retValue[2] = 1.0;
                opponent = av.otherPlayerId;
            }
            case DrawNewPlayerHand ignored -> retValue[3] = 1.0;
            case PlayLatexGlove a -> {
                retValue[4] = 1.0;
                opponent = a.otherPlayerId;
            }
            case PlayMedicalError a -> {
                retValue[5] = 1.0;
                opponent = a.otherPlayerId;
            }
            case PlayOrganThief a -> {
                retValue[6] = 1.0;
                opponent = a.otherPlayerId;
            }
            case PlaySpreading a -> {
                retValue[7] = 1.0;
                opponent = a.otherPlayerId;
            }
            case PlayTransplant a -> {
                retValue[8] = 1.0;
                opponent = a.otherPlayerId;
            }
            case ReplaceAllCards ignored -> retValue[9] = 1.0;
            case ReplaceCards ignored -> retValue[10] = 1.0;
            case ReplaceOneCard ignored -> retValue[11] = 1.0;
            default -> {
            } // do nothing
        }
        if (opponent > -1)
            retValue[12] = state.getGameScore(opponent);

        return retValue;
    }


}
