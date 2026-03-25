package games.poker.metrics;

import core.AbstractGameState;
import core.components.FrenchCard;
import evaluation.features.TunableStateFeatures;
import games.poker.PokerGameState;

import java.util.*;

import static java.util.stream.Collectors.*;

public class PokerStateFeatures extends TunableStateFeatures {

    static String[] allNames = new String[]{
            "PAIRS", "TRIPLES", "MAX_RUN", "START_RUN",
            "ACES", "KINGS", "QUEENS", "JACKS", "TENS", "NINES", "EIGHTS", "SEVENS", "SIXES", "FIVES",
            "FOURS", "THREES", "TWOS",
            "MAX_FLUSH", "START_FLUSH",
            "OWN_BID", "OPPONENT_BID", "BID_DIFF", "PLAYERS_FOLDED", "TURN"
    };

    // TODO: At some point I could use Effective Hand Strength from Schaeffer et al, 1998...but that has the
    // disadvantages of being a pain to calculate, and not being directly human interpretable
    public PokerStateFeatures() {
        super(allNames);
    }

    @Override
    protected PokerStateFeatures _copy() {
        return new PokerStateFeatures();
    }

    @Override
    public double[] fullFeatureVector(AbstractGameState state, int playerID) {
        double[] data = new double[allNames.length];
        PokerGameState pgs = (PokerGameState) state;
        List<FrenchCard> cards = new ArrayList<>(pgs.getPlayerDecks().get(playerID).getComponents());
        cards.addAll(pgs.getCommunityCards().getComponents());
        Map<Integer, Long> countByNumber = cards.stream().collect(groupingBy(c -> c.number, counting()));

        // Pairs
        data[0] = countByNumber.values().stream().filter(c -> c == 2).count();
        // Triples
        data[1] = countByNumber.values().stream().filter(c -> c == 3).count();
        // Max run
        boolean inRun= false;
        int currentRun = 0;
        int startRun = 0;
        for (int i = 2; i <= 14; i++) {
            if (countByNumber.containsKey(i)) {
                if (inRun) {
                    currentRun++;
                } else {
                    inRun = true;
                    currentRun = 1;
                    startRun = i;
                }
            } else {
                inRun = false;
                if (currentRun >= data[2]) {
                    data[2] = currentRun;
                    data[3] = startRun;
                }
            }
        }
        // Aces to Twos
        for (int i = 4; i <= 16; i++) {
            data[i] = countByNumber.getOrDefault(17-i, 0L);
        }
        // Own Bid
        data[17] = pgs.getPlayerBet()[playerID].getValue();
        // Opponent Bid
        for (int i = 0; i < pgs.getNPlayers(); i++) {
            if (i != playerID) {
                data[18] = Math.max(pgs.getPlayerBet()[i].getValue(), data[18]);
            }
        }
        // Bid difference
        data[19] = data[17] - data[18];
        // Players folded
        for (int i = 0; i < pgs.getNPlayers(); i++) {
            if (pgs.getPlayerFold()[i]) {
                data[20]++;
            }
        }
        // Turn
        data[21] = pgs.getTurnCounter();

        return data;
    }
}
