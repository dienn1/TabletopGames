package games.seasaltpaper.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.actions.DrawCard;
import core.components.Deck;
import core.interfaces.IExtendedSequence;
import games.seasaltpaper.SeaSaltPaperGameState;
import games.seasaltpaper.cards.HandManager;
import games.seasaltpaper.cards.SeaSaltPaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SwimmerSharkDuo extends PlayDuo implements IExtendedSequence {

    boolean executed = false;

    public SwimmerSharkDuo(int playerId, int[] cardsIdx) {
        super(playerId, cardsIdx);
    }

    @Override
    public boolean execute(AbstractGameState gs) {
        super.execute(gs);
        gs.setActionInProgress(this);
        return true;
    }

    @Override
    public List<AbstractAction> _computeAvailableActions(AbstractGameState state) {
        SeaSaltPaperGameState sspgs = (SeaSaltPaperGameState) state;
        int playerHandId = sspgs.getPlayerHands().get(playerId).getComponentID();
        ArrayList<AbstractAction> actions = new ArrayList<>();
        for (int i = 0; i < sspgs.getNPlayers(); i++)
        {
            if (i == playerId || sspgs.getProtectedHands()[i] || sspgs.getPlayerHands().get(i).getSize() == 0) {
                continue;
            }
            int targetHandId = sspgs.getPlayerHands().get(i).getComponentID();
            int fromIndex = sspgs.getRnd().nextInt(sspgs.getPlayerHands().get(i).getSize()); // randomly choose a card from the target
            actions.add(new DrawCard(targetHandId, playerHandId, fromIndex));
        }
        if (actions.isEmpty()) {
//            actions.add(new DoNothing());
//            System.out.println("NO VALID TARGET FOR SWIMMERSHARK!!"); // SHOULD NEVER GET HERE
            throw new RuntimeException("NO VALID TARGET FOR SWIMMERSHARK!! SHOULD ALREADY BE CHECKED!!");
        }
        return actions;
    }

    @Override
    public int getCurrentPlayer(AbstractGameState state) {
        return playerId;
    }

    @Override
    public void _afterAction(AbstractGameState state, AbstractAction action) {
        // handle player hand visibility of the player and the target
        if (action instanceof DrawCard d) {
            executed = true;
            HandManager.handleAfterDrawDeckVisibility(d, state, playerId);
        }
    }

    @Override
    public boolean executionComplete(AbstractGameState state) {
        return executed;
    }

    @Override
    public SwimmerSharkDuo copy() { return this; }

    @Override
    public String getString(AbstractGameState gameState) {
        return "Sailor-Shark Duo Actions: Pick a player then get a random card from their hand";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SwimmerSharkDuo that = (SwimmerSharkDuo) o;
        return executed == that.executed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), executed);
    }
}
