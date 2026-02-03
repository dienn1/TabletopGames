package games.poker;

import core.components.FrenchCard;
import games.poker.metrics.PokerHandHeuristic;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestHandHeuristic {

    public PokerGameState state;
    public PokerForwardModel fm;
    public PokerGameParameters params;

    @Before
    public void setup() {
        params = new PokerGameParameters();
        fm = new PokerForwardModel();
        state = new PokerGameState(params, 2);
        fm.setup(state);
    }

    @Test
    public void testHeuristicScores() {
        PokerHandHeuristic heuristic = new PokerHandHeuristic();
        
        // Clear hands and community cards
        state.getPlayerDecks().get(0).clear();
        state.getPlayerDecks().get(1).clear();
        state.getCommunityCards().clear();
        
        // Give player 0 a High Card (2 of Hearts)
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        double highCardScore = heuristic.evaluateState(state, 0);
        assertEquals(0.1, highCardScore, 0.001); // HighCard rank is 10
        
        // Give player 0 a Pair (Two 2s)
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 2));
        double pairScore = heuristic.evaluateState(state, 0);
        assertEquals(0.2, pairScore, 0.001); // OnePair rank is 9
        
        // Give player 0 Three of a Kind (Three 2s)
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 2));
        double tripsScore = heuristic.evaluateState(state, 0);
        assertEquals(0.4, tripsScore, 0.001); // ThreeOfAKind rank is 7
        
        // Give player 0 Four of a Kind (Four 2s)
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 2));
        double quadsScore = heuristic.evaluateState(state, 0);
        assertEquals(0.8, quadsScore, 0.001); // FourOfAKind rank is 3
        
        assertTrue(quadsScore > tripsScore);
        assertTrue(tripsScore > pairScore);
        assertTrue(pairScore > highCardScore);
    }

    @Test
    public void testFeatureVector() {
        PokerHandHeuristic heuristic = new PokerHandHeuristic();
        
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();
        
        // Pair of 2s
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 2));
        
        double[] features = heuristic.doubleVector(state, 0);
        // names: RoyalFlush(0), StraightFlush(1), FourOfAKind(2), FullHouse(3), Flush(4), Straight(5), ThreeOfAKind(6), TwoPair(7), OnePair(8), HighCard(9)
        // plus other names: HighCardValue(10), TripleValue(11), HighestPairValue(12), etc. (total 18)
        
        assertEquals(21, features.length);
        assertEquals(0.0, features[6], 0.001); // ThreeOfAKind
        assertEquals(1.0, features[8], 0.001); // OnePair (one-hot)
        assertEquals(2.0, features[10], 0.001); // HighCardValue
        assertEquals(2.0, features[12], 0.001); // HighestPairValue
        
        // Three 2s
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 2));
        features = heuristic.doubleVector(state, 0);
        assertEquals(1.0, features[6], 0.001); // ThreeOfAKind (one-hot)
        assertEquals(0.0, features[8], 0.001); // OnePair (one-hot)
        assertEquals(2.0, features[11], 0.001); // TripleValue
    }
}
