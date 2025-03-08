package players;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.ITunableParameters;
import org.json.simple.JSONObject;
import utilities.JSONUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static utilities.JSONUtils.loadClassFromJSON;
import static utilities.JSONUtils.loadJSONObjectsFromDirectory;

public class ComparisonPlayer extends AbstractPlayer {

    List<AbstractPlayer> playersCompared;
    List<String> playerNames;

    String outputPath;

    float[] agreementMatrix;

    String csv_name = "AgreementMatrix.csv";

    int playerCount;
    int decisionCount = 0;

    public ComparisonPlayer(List<AbstractPlayer> playersCompared, String outputPath) {
        super(new PlayerParameters(), "ComparisonPlayer");
        this.playersCompared = new ArrayList<>();
        for (AbstractPlayer p : playersCompared) {
            this.playersCompared.add(p.copy());
        }
        this.outputPath = outputPath;
        this.playerCount = playersCompared.size();
    }

    public ComparisonPlayer(String playersDir, String outputPath) {
        super(new PlayerParameters(), "ComparisonPlayer");
        playerNames = new ArrayList<>();
        Map<String, JSONObject> playersJSON = loadJSONObjectsFromDirectory(playersDir, false, "");
        List<AbstractPlayer> players = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : playersJSON.entrySet()) {
            Object thing = loadClassFromJSON(entry.getValue());
            if (thing instanceof PlayerParameters playerParams) {
                players.add(playerParams.instantiate());
                playerNames.add(entry.getKey());
            }
            else if (thing instanceof ITunableParameters<?> params) {
                Object instance = params.instantiate();
                if (instance instanceof AbstractPlayer p) {
                    players.add(p.copy());
                    playerNames.add(entry.getKey());
                }
            }
            else if (thing instanceof AbstractPlayer p) {
                players.add(p.copy());
                playerNames.add(entry.getKey());
            }
        }
        this.playersCompared = new ArrayList<>();
        for (AbstractPlayer p : players) {
            this.playersCompared.add(p.copy());
        }
        this.outputPath = outputPath;
        this.playerCount = players.size();
        System.out.println(playerNames);
    }

    @Override
    public void initializePlayer(AbstractGameState gameState) {
        File csvFile = new File(outputPath, csv_name);
        if (csvFile.exists()) {
            agreementMatrix = fromCVS();
        }
        else {
            agreementMatrix = new float[playerCount * playerCount];
            Arrays.fill(agreementMatrix, 0);
        }
        for (AbstractPlayer p : playersCompared) {
            p.initializePlayer(gameState);
        }
    }

    @Override
    public void finalizePlayer(AbstractGameState gameState) {
//        for (int i = 0; i < agreementMatrix.length; i++) {
//            agreementMatrix[i] = agreementMatrix[i] / decisionCount;
//        }
        toCSV();
        for (AbstractPlayer p : playersCompared) {
            p.finalizePlayer(gameState);
        }
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> possibleActions) {
        if (possibleActions.size() == 1) {
            return possibleActions.get(0);
        }
        decisionCount++;
        List<AbstractAction> actions = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            playersCompared.get(i).setForwardModel(this.getForwardModel());
            List<AbstractAction> possibleActionsCopy = new ArrayList<>();
            for (AbstractAction a : possibleActions) {
                possibleActionsCopy.add(a.copy());
            }
            AbstractAction a = playersCompared.get(i).getAction(gameState.copy(-1), possibleActionsCopy);
            if (!possibleActions.contains(a)) {
                throw new AssertionError("Action: " + a.toString() + " played by " + playerNames.get(i) + " that was not in the list of available actions: " + possibleActions);
            }
            actions.add(a);
        }
        for (int i = 0; i < playerCount; i++) {
            for (int j = 0; j < playerCount; j++) {
                if (actions.get(i).equals(actions.get(j))) {
                    agreementMatrix[i * playerCount + j] += 1;
                }
            }
        }

        return actions.get(getRnd().nextInt(actions.size()));
    }

    @Override
    public ComparisonPlayer copy() {
        ComparisonPlayer ret = new ComparisonPlayer(playersCompared, outputPath);
        ret.decisionCount = decisionCount;
        if (agreementMatrix != null) {
            ret.agreementMatrix = agreementMatrix.clone();
        }
        return ret;
    }

    private void toCSV() {
        File csvFile = new File(outputPath, csv_name);
        try (FileWriter writer = new FileWriter(csvFile)) {
            if (playerNames != null) {
                for (int i = 0; i < playerCount; i++) {
                    writer.append(playerNames.get(i));
                    if (i < playerCount - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }
            for (int i = 0; i < playerCount; i++) {
                for (int j = 0; j < playerCount; j++) {
                    writer.append(String.valueOf(agreementMatrix[i * playerCount + j]));
                    if (j < playerCount - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private float[] fromCVS() {
        File csvFile = new File(outputPath, csv_name);
        float[] matrix = new float[playerCount * playerCount];
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                try {
                    float t = Float.parseFloat(values[0]);
                }
                catch (NumberFormatException e) {
                    continue;
                }
                for (int col = 0; col < values.length; col++) {
                    matrix[row * playerCount + col] = Float.parseFloat(values[col]);
                }
                row++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return matrix;
    }
}
