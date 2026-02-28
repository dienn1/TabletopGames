package evaluation.tournaments;

import com.fasterxml.jackson.annotation.JsonIgnore;
import core.AbstractPlayer;
import core.CoreConstants;
import core.Game;

import java.beans.Transient;
import java.util.*;

/**
 * Class to store the core results of a tournament.
 * This includes:
 * Agent_Name1, Agent_Name2, NumberOfGames, VictoriesOfPlayer1, OrdinalDeltaOf1Over2
 * Map from AgentName to the AbstractPlayer
 */
public class TournamentResults {

    public static class Result {
        public final double points;
        public final int ordinal;
        public final double score;
        public final int win; // 1 for win, 0 otherwise

        public Result(double points, int ordinal, double score, int win) {
            this.points = points;
            this.ordinal = ordinal;
            this.score = score;
            this.win = win;
        }
    }

    Map<String, AbstractPlayer> agentsByName = new HashMap<>();
    Map<String, Map<String, Integer>> nGamesPlayedPerOpponent = new HashMap<>();
    Map<String, Map<String, Integer>> winsPerPlayerPerOpponent = new HashMap<>();
    Map<String, Map<String, Integer>> ordinalDeltaPerOpponent = new HashMap<>();
    Map<String, List<Result>> playerResults = new HashMap<>();

    public void record(Game game) {
        if (game.getGameState().isNotTerminal())
            throw new IllegalArgumentException("Game has not finished yet");

        boolean byTeam = game.getGameState().getNTeams() != game.getGameState().getNPlayers();
        // TODO: Not sure what I need to do differently here for Team games after refactor - to be revisited
        int winningPlayerCount = game.getGameState().getWinners().size();
        for (int j = 0; j < game.getPlayers().size(); j++) {
            // Firstly we record the raw result for each player
            boolean playerIsWinner = game.getGameState().getOrdinalPosition(j) == 1;
            addResult(
                    game.getPlayers().get(j),
                    playerIsWinner ? 1.0 / winningPlayerCount : 0.0,
                    game.getGameState().getOrdinalPosition(j),
                    game.getGameState().getGameScore(j),
                    playerIsWinner ? 1 : 0
            );

            // Then we record the 1:1 comparisons against all other agents in the game
            AbstractPlayer playerJ = game.getPlayers().get(j);
            for (int k = 0; k < game.getPlayers().size(); k++) {
                AbstractPlayer playerK = game.getPlayers().get(k);
                if (k != j && !playerJ.toString().equals(playerK.toString())) {
                    updateGamePlayed(playerJ, playerK);
                    updateOrdinalResults(playerJ, playerK,
                            game.getGameState().getOrdinalPosition(k) - game.getGameState().getOrdinalPosition(j));
                    // negative is good (we came lower than them in ordinal order)
                    if (playerIsWinner && game.getGameState().getOrdinalPosition(k) != game.getGameState().getOrdinalPosition(j)) {
                        updateWins(playerJ, playerK, 1);
                    }
                }
            }
        }
    }

    public AbstractPlayer getAgent(String name) {
        return agentsByName.get(name);
    }

    @JsonIgnore
    public List<String> getAllAgentNames() {
        return agentsByName.keySet().stream().toList();
    }

    public void addResult(AbstractPlayer player, double points, int ordinal, double score, int win) {
        String name = player.toString();
        agentsByName.putIfAbsent(name, player);
        playerResults.computeIfAbsent(name, k -> new ArrayList<>()).add(new Result(points, ordinal, score, win));
    }

    public List<Result> getPlayerResults(String name) {
        return playerResults.getOrDefault(name, Collections.emptyList());
    }

    public List<Double> getPlayerPoints(String name) {
        return getPlayerResults(name).stream().map(r -> r.points).toList();
    }

    public List<Integer> getPlayerOrdinals(String name) {
        return getPlayerResults(name).stream().map(r -> r.ordinal).toList();
    }

    public List<Double> getPlayerScores(String name) {
        return getPlayerResults(name).stream().map(r -> r.score).toList();
    }

    public List<Integer> getPlayerWins(String name) {
        return getPlayerResults(name).stream().map(r -> r.win).toList();
    }

    public void updateGamePlayed(AbstractPlayer one, AbstractPlayer two) {
        increment(nGamesPlayedPerOpponent, one, two, 1);
    }

    public void updateOrdinalResults(AbstractPlayer one, AbstractPlayer two, int ordinalDelta) {
        increment(ordinalDeltaPerOpponent, one, two, ordinalDelta);
    }

    public void updateWins(AbstractPlayer one, AbstractPlayer two, int wins) {
        increment(winsPerPlayerPerOpponent, one, two, wins);
    }

    private void increment(Map<String, Map<String, Integer>> map, AbstractPlayer one, AbstractPlayer two, int delta) {
        String key1 = one.toString();
        String key2 = two.toString();
        agentsByName.putIfAbsent(key1, one);
        agentsByName.putIfAbsent(key2, two);
        map.computeIfAbsent(key1, k -> new HashMap<>())
                .merge(key2, delta, Integer::sum);
    }

    public int getGamesPlayed(String agentName1, String agentName2) {
        return mapGet(nGamesPlayedPerOpponent, agentName1, agentName2);
    }

    public int getWins(String winner, String loser) {
        return mapGet(winsPerPlayerPerOpponent, winner, loser);
    }

    public int getOrdinalDelta(String agentName1, String agentName2) {
        return mapGet(ordinalDeltaPerOpponent, agentName1, agentName2);
    }

    private int mapGet(Map<String, Map<String, Integer>> map, String key1, String key2) {
        Map<String, Integer> subMap = map.get(key1);
        if (subMap != null) {
            return subMap.getOrDefault(key2, 0);
        }
        return 0;
    }
}
