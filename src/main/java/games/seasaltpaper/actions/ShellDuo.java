package games.seasaltpaper.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.actions.ActionSpace;
import core.actions.DrawCard;
import core.components.Deck;
import core.components.PartialObservableDeck;
import core.interfaces.IExtendedSequence;
import games.seasaltpaper.SeaSaltPaperGameState;
import games.seasaltpaper.cards.SeaSaltPaperCard;

import java.util.ArrayList;
import java.util.List;

public class ShellDuo extends PlayDuo implements IExtendedSequence {

    enum Step {
        CHOOSE_PILE,
        CHOOSE_CARD,
        DONE;
    }

    private Step currentStep;

    private int discardPileId;

    public ShellDuo(int playerId, int[] cardsIdx) {
        super(playerId, cardsIdx);
    }

    @Override
    public boolean execute(AbstractGameState gs) {
        super.execute(gs);
        SeaSaltPaperGameState sspg = (SeaSaltPaperGameState) gs;
        if (sspg.getDiscardPile1().getSize() == 0 && sspg.getDiscardPile2().getSize() == 0) {
            System.out.println("BOTH PILES GONE BRUH");
            return false;
        }
        currentStep = Step.CHOOSE_PILE;
        gs.setActionInProgress(this);
        return true;
    }

    @Override
    public List<AbstractAction> _computeAvailableActions(AbstractGameState state) {
        ArrayList<AbstractAction> actions = new ArrayList<>();
        SeaSaltPaperGameState sspg = (SeaSaltPaperGameState) state;
        if (currentStep == Step.CHOOSE_PILE) {
            if (sspg.getDiscardPile1().getSize() > 0){
                actions.add(new ChoosePile(sspg.getDiscardPile1().getComponentID(), playerId));
            }
            if (sspg.getDiscardPile2().getSize() > 0) {
                actions.add(new ChoosePile(sspg.getDiscardPile2().getComponentID(), playerId));
            }
        }
        if (currentStep == Step.CHOOSE_CARD)
        {
            Deck<SeaSaltPaperCard> discardPile = (Deck<SeaSaltPaperCard>) sspg.getComponentById(discardPileId);
            int playerHandId = sspg.getPlayerHands().get(playerId).getComponentID();
            for (int i = 0; i < discardPile.getSize(); i++) {
                actions.add(new DrawCard(discardPileId, playerHandId, i));
            }
        }
        return actions;
    }

    @Override
    public int getCurrentPlayer(AbstractGameState state) {
        return playerId;
    }

    @Override
    public void _afterAction(AbstractGameState state, AbstractAction action) {
        if (currentStep == Step.CHOOSE_PILE) { currentStep = Step.CHOOSE_CARD; }
        else if (currentStep == Step.CHOOSE_CARD) { currentStep = Step.DONE; }
    }

    @Override
    public boolean executionComplete(AbstractGameState state) {
        return currentStep == Step.DONE;
    }

    @Override
    public void printToConsole(AbstractGameState gameState) {
        super.printToConsole(gameState);
    }

    @Override
    public ShellDuo copy() { return this; }

    @Override
    public String getString(AbstractGameState gameState) {
        return "Shell Duo Action: Choose a discard pile to look at then draw a card from that pile";
    }
}
