package games.poker;

import core.components.FrenchCard;
import games.poker.metrics.PokerHandFeatures;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestHandFeatures {

    public PokerGameState state;
    public PokerForwardModel fm;
    public PokerGameParameters params;

    @Before
    public void setup() {
        params = new PokerGameParameters();
        fm = new PokerForwardModel();
        state = new PokerGameState(params, 3);
        fm.setup(state);
    }

    @Test
    public void testRoyalFlush() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Ace, FrenchCard.Suite.Hearts));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Hearts));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Queen, FrenchCard.Suite.Hearts));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Jack, FrenchCard.Suite.Hearts));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 10));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[0], 0.001); // RoyalFlush
        assertEquals(14.0, vector[10], 0.001); // HighCardValue (Ace)
    }

    @Test
    public void testStraightFlush() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 9));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 7));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 5));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[1], 0.001); // StraightFlush
        assertEquals(9.0, vector[10], 0.001); // HighCardValue
    }

    @Test
    public void testFourOfAKind() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 8));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 4));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[2], 0.001); // FourOfAKind
        assertEquals(8.0, vector[10], 0.001); // HighCardValue (8)
        assertEquals(8.0, vector[11], 0.001); // TripleValue (8s)
        assertEquals(8.0, vector[12], 0.001); // HighestPairValue (8s)
    }

    @Test
    public void testFullHouse() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 2));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 2));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 3));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 3));

        double[] vector = features.doubleVector(state, 0);

        assertEquals(1.0, vector[3], 0.001); // FullHouse
        assertEquals(0.0, vector[6], 0.001); // ThreeOfAKind should be 0 (one-hot)
        assertEquals(0.0, vector[8], 0.001); // OnePair should be 0 (one-hot)
        assertEquals(0.0, vector[9], 0.001); // HighCard should be 0 (one-hot)

        assertEquals(3.0, vector[10], 0.001); // HighCardValue (3 is highest)
        assertEquals(2.0, vector[11], 0.001); // TripleValue (2s)
        assertEquals(3.0, vector[12], 0.001); // HighestPairValue (3s)
    }

    @Test
    public void testFlush() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 10));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 4));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 2));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[4], 0.001); // Flush
        assertEquals(10.0, vector[10], 0.001); // HighCardValue
    }

    @Test
    public void testStraight() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 9));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 7));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 5));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[5], 0.001); // Straight
        assertEquals(9.0, vector[10], 0.001); // HighCardValue
    }

    @Test
    public void testThreeOfAKind() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 6));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 4));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[6], 0.001); // ThreeOfAKind
        assertEquals(6.0, vector[10], 0.001); // HighCardValue
        assertEquals(6.0, vector[11], 0.001); // TripleValue
    }

    @Test
    public void testTwoPair() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 5));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 5));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 4));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 4));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[7], 0.001); // TwoPair
        assertEquals(5.0, vector[10], 0.001); // HighCardValue
        assertEquals(5.0, vector[12], 0.001); // HighestPairValue
    }

    @Test
    public void testOnePair() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 10));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 10));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 7));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 5));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[8], 0.001); // OnePair
        assertEquals(10.0, vector[10], 0.001); // HighCardValue
        assertEquals(10.0, vector[12], 0.001); // HighestPairValue
    }

    @Test
    public void testOnePairNoCommunity() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getPlayerDecks().get(2).clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Hearts));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Diamonds));

        state.getPlayerDecks().get(2).add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Spades));
        state.getPlayerDecks().get(2).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 9));

        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[8], 0.001); // OnePair
        assertEquals(13.0, vector[10], 0.001); // HighCardValue
        assertEquals(13.0, vector[12], 0.001); // HighestPairValue

        vector = features.doubleVector(state, 2);
        assertEquals(0.0, vector[8], 0.001); // OnePair
        assertEquals(13.0, vector[10], 0.001); // HighCardValue
        assertEquals(0.0, vector[12], 0.001); // HighestPairValue
    }

    @Test
    public void testHighCard() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 13));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Diamonds, 10));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Clubs, 7));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 5));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Hearts, 2));
        double[] vector = features.doubleVector(state, 0);
        assertEquals(1.0, vector[9], 0.001); // HighCard
        assertEquals(13.0, vector[10], 0.001); // HighCardValue
    }

    @Test
    public void testBestHand7Cards() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();

        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 10));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 8));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 6));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 4));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Number, FrenchCard.Suite.Spades, 2));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Ace, FrenchCard.Suite.Hearts)); // Ace of Hearts
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Hearts)); // King of Hearts
        double[] vector = features.doubleVector(state, 0);

        assertEquals(1.0, vector[4], 0.001); // Flush (Spades) wins over Ace High
        assertEquals(14.0, vector[10], 0.001); // HighCardValue (14) - this is absolute highest in all 7 cards
        assertEquals(0.0, vector[11], 0.001); // TripleValue
        assertEquals(0.0, vector[12], 0.001); // HighestPairValue
    }

    @Test
    public void testValues() {
        PokerHandFeatures features = new PokerHandFeatures();
        
        state.getPlayerDecks().get(0).clear();
        state.getCommunityCards().clear();
        
        // Ace and King
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.Ace, FrenchCard.Suite.Hearts));
        state.getPlayerDecks().get(0).add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Clubs));
        
        double[] vector = features.doubleVector(state, 0);
        // We accept whatever the system says is the best hand, but it must be ONE-HOT in the first 10 slots.
        int count = 0;
        for (int i=0; i<10; i++) if (vector[i] == 1.0) count++;
        assertEquals(1, count);
        
        assertEquals(14.0, vector[10], 0.001); // HighCardValue (Ace = 14)
        
        // Pair of Kings
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.King, FrenchCard.Suite.Diamonds));
        vector = features.doubleVector(state, 0);
        count = 0;
        for (int i=0; i<10; i++) if (vector[i] == 1.0) count++;
        assertEquals(1, count);
        assertEquals(14.0, vector[10], 0.001); // HighCardValue (Ace = 14)
        assertEquals(13.0, vector[12], 0.001); // HighestPairValue (King = 13)
        
        // Three Aces
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Ace, FrenchCard.Suite.Diamonds));
        state.getCommunityCards().add(new FrenchCard(FrenchCard.FrenchCardType.Ace, FrenchCard.Suite.Clubs));
        vector = features.doubleVector(state, 0);
        count = 0;
        for (int i=0; i<10; i++) if (vector[i] == 1.0) count++;
        assertEquals(1, count);
        assertEquals(14.0, vector[10], 0.001); // HighCardValue (Ace = 14)
        assertEquals(14.0, vector[11], 0.001); // TripleValue (Ace = 14)
    }

    @Test
    public void testStateFeatures() {
        PokerHandFeatures features = new PokerHandFeatures();
        state.getPlayerBet()[0].setValue(20);
        state.getPlayerBet()[1].setValue(30);
        // Turn and Round are 0 by default in setup
        
        double[] vector = features.doubleVector(state, 0);
        assertEquals(0.0, vector[13], 0.001); // Round
        assertEquals(0.0, vector[14], 0.001); // Turn
        assertEquals(20.0 / 50.0, vector[15], 0.001); // OwnBid
        assertEquals(30.0 / 50.0, vector[16], 0.001); // OpponentBid
        assertEquals(-10.0 /50.0, vector[17], 0.001); // BidDiff
    }
}
