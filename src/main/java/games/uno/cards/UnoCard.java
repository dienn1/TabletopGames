package games.uno.cards;

import core.components.Card;
import core.components.Deck;
import games.uno.UnoGameState;

public class UnoCard extends Card {

    public enum UnoCardType {
        Number,
        Skip,
        Reverse,
        Draw,
        Wild,
//        SwapHands
    }

    public final String color;
    public final UnoCardType unoCardType;
    public final int number;
    public final int drawN;

    public UnoCard(UnoCardType unoCardType, String color, int number){
        super(unoCardType.toString());
        this.color = color;
        this.unoCardType = unoCardType;
        if (unoCardType == UnoCardType.Draw) {
            this.drawN = number;
            this.number = -1;
        } else {
            this.number = number;
            this.drawN = -1;
        }
    }

    public UnoCard(UnoCardType unoCardType, String color){
        super(unoCardType.toString());
        this.color = color;
        this.unoCardType = unoCardType;
        this.number = -1;
        this.drawN = -1;
    }

    public UnoCard(UnoCardType unoCardType, String color, int number, int drawN) {
        super(unoCardType.toString());
        this.color = color;
        this.unoCardType = unoCardType;
        this.number = number;
        this.drawN = drawN;
    }

    private UnoCard(UnoCardType unoCardType, String color, int number, int drawN, int componentID) {
        super(unoCardType.toString(), componentID);
        this.color = color;
        this.unoCardType = unoCardType;
        this.number = number;
        this.drawN = drawN;
    }

    @Override
    public Card copy() {
        return new UnoCard(unoCardType, color, number, drawN, componentID);
    }

    public boolean isPlayable(UnoGameState gameState) {
        switch (unoCardType) {
            case Number:
                return this.number == gameState.getCurrentCard().number || this.color.equals(gameState.getCurrentColor());
            case Skip:
            case Reverse:
            case Draw:
                return this.color.equals(gameState.getCurrentColor());
            case Wild:
                if (this.drawN >= 1) {
                    int playerID = gameState.getCurrentPlayer();
                    Deck<UnoCard> playerHand = gameState.getPlayerDecks().get(playerID);
                    for (UnoCard card : playerHand.getComponents()) {
                        if (card.color.equals(gameState.getCurrentColor()))
                            return false;
                    }
                }
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        switch (unoCardType) {
            case Number:
                return unoCardType + "{" + color + " " + number + "}";
            case Skip:
            case Reverse:
                return unoCardType + "{" + color + "}";
            case Draw:
                return unoCardType + "{" + drawN + " " + color + "}";
            case Wild:
                if (drawN < 1) {
                    return unoCardType.toString();
                } else {
                    return unoCardType.toString() + "{draw " + drawN + "}";
                }
        }
        return null;
    }
}
