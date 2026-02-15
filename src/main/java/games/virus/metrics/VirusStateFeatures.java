package games.virus.metrics;

import core.AbstractGameState;
import core.interfaces.IStateFeatureVector;
import games.virus.VirusGameState;
import games.virus.cards.VirusCard;
import games.virus.cards.VirusTreatmentCard;
import games.virus.components.VirusBody;
import utilities.Utils;

import static games.virus.components.VirusOrgan.VirusOrganState.*;

public class VirusStateFeatures implements IStateFeatureVector {

    static String[] cardNames = Utils.enumNames(VirusCard.VirusCardType.class).toArray(new String[0]);
    static String[] treatmentNames = Utils.enumNames(VirusTreatmentCard.VirusCardType.class).toArray(new String[0]);
    static String[] otherNames = new String[]{"HealthyOrgans", "DiseasedOrgans", "VaccinatedOrgans", "CardsInHand", "RequiredOrgansInHand", "LeaderMargin"};
    static String[] allNames;
    static {
        allNames = new String[cardNames.length + treatmentNames.length + otherNames.length];
        System.arraycopy(cardNames, 0, allNames, 0, cardNames.length);
        System.arraycopy(treatmentNames, 0, allNames, cardNames.length, treatmentNames.length);
        System.arraycopy(otherNames, 0, allNames, cardNames.length + treatmentNames.length, otherNames.length);
    }

    @Override
    public String[] names() {
        return allNames;
    }

    @Override
    public double[] doubleVector(AbstractGameState state, int playerID) {
        double[] retValue =  new double[allNames.length];
        VirusGameState vgs = (VirusGameState) state;
        var playerHand = vgs.getPlayerDecks().get(playerID);

        for (int i = 0; i < cardNames.length; i++) {
            int finalI = i;
            retValue[i] = playerHand.stream().filter(card -> card.type == VirusCard.VirusCardType.values()[finalI]).count();
        }
        for (int i = 0; i < treatmentNames.length; i++) {
            int finalI = i;
            retValue[i + cardNames.length] = playerHand.stream()
                    .filter(card -> card instanceof VirusTreatmentCard &&
                            ((VirusTreatmentCard) card).treatmentType == VirusTreatmentCard.TreatmentType.values()[finalI]).count();
        }
        int offset = cardNames.length + treatmentNames.length;
        VirusBody playerBody = vgs.getPlayerBody(playerID);
        retValue[offset] = vgs.getGameScore(playerID);
        retValue[offset + 1] = playerBody.organs.values().stream().filter(organ -> organ.state == Infected || organ.state == InfectedWild).count();
        retValue[offset + 2] = playerBody.organs.values().stream().filter(organ -> organ.state == Vaccinated || organ.state == VaccinatedWild).count();
        retValue[offset + 3] = vgs.getPlayerDecks().get(playerID).getSize();
        retValue[offset + 4] = vgs.getPlayerDecks().get(playerID).stream()
                .filter(card -> card.type == VirusCard.VirusCardType.Organ && !playerBody.hasOrgan(card.organ)).count();
        double maxOtherScore = Double.NEGATIVE_INFINITY;
        for (int p = 0; p < state.getNPlayers(); p++) {
            if (p == playerID) continue;
            if (vgs.getGameScore(p) >  maxOtherScore) {
                maxOtherScore = vgs.getGameScore(p);
            }
        }
        retValue[offset + 5] = retValue[offset] - maxOtherScore;
        return retValue;
    }


}
