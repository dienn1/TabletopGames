package games.poker.metrics;

import core.AbstractGameState;
import core.components.Deck;
import core.components.FrenchCard;
import core.interfaces.IStateFeatureVector;
import games.poker.PokerGameState;
import utilities.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class PokerHandFeatures implements IStateFeatureVector {

    static String[] handNames = Arrays.stream(PokerGameState.PokerHand.values()).map(Enum::name).toArray(String[]::new);
    static String[] otherNames = new String[]{"HighCardValue", "TripleValue", "HighestPairValue",
            "Round", "Turn", "OwnBid", "OpponentBid", "BidDiff"};
    static String[] allNames;
    static {
        allNames = new String[handNames.length + otherNames.length];
        System.arraycopy(handNames, 0, allNames, 0, handNames.length);
        System.arraycopy(otherNames, 0, allNames, handNames.length, otherNames.length);
    }

    @Override
    public String[] names() {
        return allNames;
    }

    @Override
    public double[] doubleVector(AbstractGameState state, int playerID) {
        double[] data = new double[allNames.length];
        PokerGameState pgs = (PokerGameState) state;
        List<FrenchCard> cards = new ArrayList<>(pgs.getPlayerDecks().get(playerID).getComponents());
        cards.addAll(pgs.getCommunityCards().getComponents());

        Deck<FrenchCard> tempDeck = new Deck<>("Temp", core.CoreConstants.VisibilityMode.HIDDEN_TO_ALL);
        for (FrenchCard card : cards) {
            tempDeck.add(new FrenchCard(card.type, card.suite, card.number));
        }

        Pair<PokerGameState.PokerHand, HashSet<Integer>> bestHand = PokerGameState.PokerHand.translateHand(tempDeck);
        if (bestHand != null) {
            data[bestHand.a.ordinal()] = 1.0;
        } else if (!cards.isEmpty()) {
            data[PokerGameState.PokerHand.HighCard.ordinal()] = 1.0;
        }

        Map<Integer, Long> countByNumber = cards.stream().collect(groupingBy(c -> c.number, counting()));
        List<Integer> sortedNumbers = cards.stream().map(c -> c.number).sorted(Comparator.reverseOrder()).toList();

        // HighCardValue
        data[handNames.length] = sortedNumbers.isEmpty() ? 0 : sortedNumbers.get(0);
        // TripleValue
        data[handNames.length + 1] = countByNumber.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .map(Map.Entry::getKey)
                .max(Integer::compare)
                .orElse(0);
        // HighestPairValue
        data[handNames.length + 2] = countByNumber.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .max(Integer::compare)
                .orElse(0);

        // Round
        data[handNames.length + 3] = pgs.getRoundCounter();
        // Turn
        data[handNames.length + 4] = pgs.getTurnCounter();
        // Own Bid
        data[handNames.length + 5] = pgs.getPlayerBet()[playerID].getValue();
        // Opponent Bid
        double maxOpponentBid = 0;
        for (int i = 0; i < pgs.getNPlayers(); i++) {
            if (i != playerID) {
                maxOpponentBid = Math.max(pgs.getPlayerBet()[i].getValue(), maxOpponentBid);
            }
        }
        data[handNames.length + 6] = maxOpponentBid;
        // Bid difference
        data[handNames.length + 7] = data[handNames.length + 5] - data[handNames.length + 6];

        return data;
    }
}
