package games.poker.metrics;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IActionFeatureVector;
import games.poker.actions.*;
import org.checkerframework.checker.units.qual.A;

public class PokerActionFeatures implements IActionFeatureVector {

    String[] names = new String[]{"IsFold", "IsBid", "IsCall", "IsAllIn", "IsCheck", "IsRaise"};

    @Override
    public String[] names() {
        return names;
    }

    @Override
    public double[] doubleVector(AbstractAction action, AbstractGameState state, int playerID) {
        double[] retValue = new double[names.length];
        switch (action) {
            case Fold ignored:
                retValue[0] = 1;
                break;
            case Bet ignored:
                retValue[1] = 1;
                break;
            case Call ignored:
                retValue[2] = 1;
                break;
            case AllIn ignored:
                retValue[3] = 1;
                break;
            case Check ignored:
                retValue[4] = 1;
                break;
            case Raise ignored:
                retValue[5] = 1;
                break;
            default:
        }

        return retValue;
    }

}
