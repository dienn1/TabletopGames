package games.cantstop;

import core.AbstractParameters;
import core.Game;
import evaluation.optimisation.TunableParameters;
import games.GameType;
import games.dominion.DominionForwardModel;
import games.dominion.DominionGameState;

import java.util.Arrays;
import java.util.Objects;

public class CantStopParameters extends TunableParameters {

    /*
    This could have been implemented as an array, but this more verbose set up makes it easier for
    any future conversion of this to extend TunableParameters

    Ideas for other changes are to implement the variants described at https://www.yucata.de/en/Rules/CantStop
    - Varying numbers of capped columns based on player count
    - Not able to stop if on the same square as an opponent
    - Moving onto an occupied space instead means you jump to the next unoccupied one
     */

    public int TWO_MAX = 2;
    public int THREE_MAX = 4;
    public int FOUR_MAX = 6;
    public int FIVE_MAX = 8;
    public int SIX_MAX = 10;
    public int SEVEN_MAX = 12;
    public int EIGHT_MAX = 10;
    public int NINE_MAX = 8;
    public int TEN_MAX = 6;
    public int ELEVEN_MAX = 4;
    public int TWELVE_MAX = 2;

    public final int DICE_NUMBER = 4; // If you change this, then you'll need to also update code in ForwardModel._computeAvailableActions()
    public final int DICE_SIDES = 6;
    public final int COLUMNS_TO_WIN = 3;
    public final int MARKERS = 3; // number of temporary markers


    public CantStopParameters() {
        addTunableParameter("TWO_MAX", 2, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("THREE_MAX", 4, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("FOUR_MAX", 6, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("FIVE_MAX", 8, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("SIX_MAX", 10, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("SEVEN_MAX", 12, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("EIGHT_MAX", 10, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("NINE_MAX", 8, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("TEN_MAX", 6, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("ELEVEN_MAX", 4, Arrays.asList(2, 4, 6, 8, 10, 12));
        addTunableParameter("TWELVE_MAX", 2, Arrays.asList(2, 4, 6, 8, 10, 12));
    }

    @Override
    public void _reset() {
        TWO_MAX = (int) getParameterValue("TWO_MAX");
        THREE_MAX = (int) getParameterValue("THREE_MAX");
        FOUR_MAX = (int) getParameterValue("FOUR_MAX");
        FIVE_MAX = (int) getParameterValue("FIVE_MAX");
        SIX_MAX = (int) getParameterValue("SIX_MAX");
        SEVEN_MAX = (int) getParameterValue("SEVEN_MAX");
        EIGHT_MAX = (int) getParameterValue("EIGHT_MAX");
        NINE_MAX = (int) getParameterValue("NINE_MAX");
        TEN_MAX = (int) getParameterValue("TEN_MAX");
        ELEVEN_MAX = (int) getParameterValue("ELEVEN_MAX");
        TWELVE_MAX = (int) getParameterValue("TWELVE_MAX");

    }

    public int maxValue(int number) {
        switch (number) {
            case 2:
                return TWO_MAX;
            case 3:
                return THREE_MAX;
            case 4:
                return FOUR_MAX;
            case 5:
                return FIVE_MAX;
            case 6:
                return SIX_MAX;
            case 7:
                return SEVEN_MAX;
            case 8:
                return EIGHT_MAX;
            case 9:
                return NINE_MAX;
            case 10:
                return TEN_MAX;
            case 11:
                return ELEVEN_MAX;
            case 12:
                return TWELVE_MAX;
            default:
                throw new IllegalArgumentException(number + " is not supported");
        }
    }

    @Override
    protected AbstractParameters _copy() {
        CantStopParameters ret = new CantStopParameters();
        ret.TWO_MAX = TWO_MAX;
        ret.THREE_MAX = THREE_MAX;
        ret.FOUR_MAX = FOUR_MAX;
        ret.FIVE_MAX = FIVE_MAX;
        ret.SIX_MAX = SIX_MAX;
        ret.SEVEN_MAX = SEVEN_MAX;
        ret.EIGHT_MAX = EIGHT_MAX;
        ret.NINE_MAX = NINE_MAX;
        ret.TEN_MAX = TEN_MAX;
        ret.ELEVEN_MAX = ELEVEN_MAX;
        ret.TWELVE_MAX = TWELVE_MAX;
        return ret;
    }

    @Override
    public boolean _equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CantStopParameters that = (CantStopParameters) o;
        return TWO_MAX == that.TWO_MAX && THREE_MAX == that.THREE_MAX && FOUR_MAX == that.FOUR_MAX && FIVE_MAX == that.FIVE_MAX && SIX_MAX == that.SIX_MAX && SEVEN_MAX == that.SEVEN_MAX && EIGHT_MAX == that.EIGHT_MAX && NINE_MAX == that.NINE_MAX && TEN_MAX == that.TEN_MAX && ELEVEN_MAX == that.ELEVEN_MAX && TWELVE_MAX == that.TWELVE_MAX && DICE_NUMBER == that.DICE_NUMBER && DICE_SIDES == that.DICE_SIDES && COLUMNS_TO_WIN == that.COLUMNS_TO_WIN && MARKERS == that.MARKERS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), TWO_MAX, THREE_MAX, FOUR_MAX, FIVE_MAX, SIX_MAX, SEVEN_MAX, EIGHT_MAX, NINE_MAX, TEN_MAX, ELEVEN_MAX, TWELVE_MAX, DICE_NUMBER, DICE_SIDES, COLUMNS_TO_WIN, MARKERS);
    }

    @Override
    public Object instantiate() {
        return new Game(GameType.CantStop, new CantStopForwardModel(), new CantStopGameState(this, GameType.CantStop.getMinPlayers()));
    }
}
