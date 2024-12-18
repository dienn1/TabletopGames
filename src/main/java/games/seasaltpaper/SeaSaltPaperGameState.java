package games.seasaltpaper;

import core.AbstractParameters;
import core.AbstractGameState;
import core.components.Component;
import core.components.Deck;
import core.components.PartialObservableDeck;
import core.interfaces.IPrintable;
import games.GameType;
import games.seasaltpaper.cards.HandManager;
import games.seasaltpaper.cards.SeaSaltPaperCard;

import java.util.*;
import java.util.function.Supplier;

public class SeaSaltPaperGameState extends AbstractGameState implements IPrintable {

    public static final int DISCARD_PILE_COUNT = 2; // TODO move this to parameter?

    public enum TurnPhase {
        START,
        DRAW,
        DUO,
        FINISH;

        public TurnPhase next()
        {
            return next.get();
        }
        private Supplier<TurnPhase> next;
        static {
            START.next = () -> TurnPhase.DRAW;
            DRAW.next = () -> TurnPhase.DUO;
            DUO.next = () -> TurnPhase.FINISH;
            FINISH.next = () -> TurnPhase.DRAW;
        }
    }

    TurnPhase currentPhase = TurnPhase.DRAW;

    //TODO: Make playerHands separate from played cards
    List<PartialObservableDeck<SeaSaltPaperCard>> playerHands;
    List<Deck<SeaSaltPaperCard>> playerDiscards;

    // TODO make this partially observable (only top card visible) and for whoever playing ShellDuo
    Deck<SeaSaltPaperCard> discardPile1, discardPile2;  // TODO make this a list to generalize different number of discard piles
    Deck<SeaSaltPaperCard> drawPile;

    int lastChance = -1; // index of the player that play "lastChance", -1 for no one

    Random redeterminisationRnd = new Random(System.currentTimeMillis());

    boolean[] protectedHands;

    int[] playerPoints; // player points of previous rounds

    public int[] playerCurrentDuoPoints;

    //final int winScore;

    public SeaSaltPaperGameState(AbstractParameters gameParameters, int nPlayers) { super(gameParameters, nPlayers); }

    @Override
    protected GameType _getGameType() { return GameType.SeaSaltPaper; }

    @Override
    protected List<Component> _getAllComponents() {
        List<Component> components = new ArrayList<>();
        components.addAll(playerHands);
        components.addAll(playerDiscards);
        components.add(drawPile);
        components.add(discardPile1);
        components.add(discardPile2);
        return components;
    }

    @Override
    protected AbstractGameState _copy(int playerId) {
        //TODO Redeterminise hidden info
//        System.out.println("COPY WAS USED FOR SOME REASON");
        SeaSaltPaperGameState sspgs = new SeaSaltPaperGameState(gameParameters.copy(), getNPlayers());
        sspgs.drawPile = drawPile.copy();
        sspgs.discardPile1 = discardPile1.copy();
        sspgs.discardPile2 = discardPile2.copy();
        sspgs.playerHands = new ArrayList<>();
        for (int i = 0; i < getNPlayers(); i++) {
            sspgs.playerHands.add(playerHands.get(i).copy());
        }
        sspgs.playerDiscards = new ArrayList<>();
        for (int i = 0; i < getNPlayers(); i++) {
            sspgs.playerDiscards.add(playerDiscards.get(i).copy());
        }
        sspgs.playerPoints = playerPoints.clone();
        sspgs.currentPhase = currentPhase;
        return sspgs;
    }

    @Override
    protected double _getHeuristicScore(int playerId) {
        return HandManager.calculatePoint(this, playerId) + playerPoints[playerId];
    }

    @Override
    public double getGameScore(int playerId) {
        return playerPoints[playerId];
    }

    @Override
    public boolean _equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SeaSaltPaperGameState that = (SeaSaltPaperGameState) o;
        return lastChance == that.lastChance && currentPhase == that.currentPhase && Objects.equals(playerHands, that.playerHands) && Objects.equals(playerDiscards, that.playerDiscards) && Objects.equals(discardPile1, that.discardPile1) && Objects.equals(discardPile2, that.discardPile2) && Objects.equals(drawPile, that.drawPile) && Objects.equals(redeterminisationRnd, that.redeterminisationRnd) && Arrays.equals(playerPoints, that.playerPoints);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), currentPhase, playerHands, playerDiscards, discardPile1, discardPile2, drawPile, lastChance, redeterminisationRnd);
        result = 31 * result + Arrays.hashCode(playerPoints);
        return result;
    }

    /**
     * Updates components after round setup.
     */
    void updateComponents() {
        this.addAllComponents();
    }

    public List<PartialObservableDeck<SeaSaltPaperCard>> getPlayerHands() { return playerHands; }

    public List<Deck<SeaSaltPaperCard>> getPlayerDiscards() { return playerDiscards; }

    public boolean[] getProtectedHands() { return protectedHands; }

    // Check if all hands are protected;
    public boolean allProtected() {
        boolean t = false;
        for (int i=0; i<getNPlayers(); i++) {
            t = t || protectedHands[i];
        }
        return !t;
    }

    public Deck<SeaSaltPaperCard> getDrawPile() { return drawPile; }

    public Deck<SeaSaltPaperCard> getDiscardPile1() { return discardPile1; }
    public Deck<SeaSaltPaperCard> getDiscardPile2() { return discardPile2; }
    public Deck<SeaSaltPaperCard>[] getDiscardPiles() { return new Deck[]{discardPile1, discardPile2};}

    public int[] getPlayerPoints() { return playerPoints; }
    public TurnPhase getCurrentPhase() { return currentPhase;}
    public void resetTurn() { currentPhase = TurnPhase.START; }


}
