package evaluation.tournaments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.AbstractPlayer;
import core.Game;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
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

        @JsonCreator
        public Result(@JsonProperty("points") double points,
                      @JsonProperty("ordinal") int ordinal,
                      @JsonProperty("score") double score,
                      @JsonProperty("win") int win) {
            this.points = points;
            this.ordinal = ordinal;
            this.score = score;
            this.win = win;
        }
    }

    @JsonIgnore
    Map<String, AbstractPlayer> agentsByName = new HashMap<>();
    Map<String, Map<String, Integer>> nGamesPlayedPerOpponent = new HashMap<>();
    Map<String, Map<String, Integer>> winsPerPlayerPerOpponent = new HashMap<>();
    Map<String, Map<String, Integer>> ordinalDeltaPerOpponent = new HashMap<>();
    Map<String, List<Result>> playerResults = new HashMap<>();

    public void record(Game game) {
        if (game.getGameState().isNotTerminal())
            throw new IllegalArgumentException("Game has not finished yet");

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

    public int totalResults() {
        return playerResults.values().stream().mapToInt(List::size).sum();
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

    // ------------------ Jackson (de)serialisation helpers ------------------

    /**
     * DTO used to safely serialise/deserialise the parts of TournamentResults that are JSON-friendly.
     * We intentionally exclude the transient agentsByName map (AbstractPlayer instances) from JSON.
     */
    public static class TournamentResultsDTO {
        public Map<String, Map<String, Integer>> nGamesPlayedPerOpponent;
        public Map<String, Map<String, Integer>> winsPerPlayerPerOpponent;
        public Map<String, Map<String, Integer>> ordinalDeltaPerOpponent;
        public Map<String, List<Result>> playerResults;

        // no-arg constructor for Jackson
        public TournamentResultsDTO() {
        }
    }

    // Expose DTO for Jackson serialization
    @JsonValue
    public TournamentResultsDTO toDTO() {
        TournamentResultsDTO dto = new TournamentResultsDTO();
        dto.nGamesPlayedPerOpponent = this.nGamesPlayedPerOpponent;
        dto.winsPerPlayerPerOpponent = this.winsPerPlayerPerOpponent;
        dto.ordinalDeltaPerOpponent = this.ordinalDeltaPerOpponent;
        dto.playerResults = this.playerResults;
        return dto;
    }

    @JsonCreator
    public static TournamentResults getTournamentResults(TournamentResultsDTO dto) {
        return extractFromDTO(dto);
    }

    /**
     * Convert this TournamentResults to a JSON string (only includes JSON-friendly fields).
     */
    public String toJson() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        TournamentResultsDTO dto = new TournamentResultsDTO();
        dto.nGamesPlayedPerOpponent = this.nGamesPlayedPerOpponent;
        dto.winsPerPlayerPerOpponent = this.winsPerPlayerPerOpponent;
        dto.ordinalDeltaPerOpponent = this.ordinalDeltaPerOpponent;
        dto.playerResults = this.playerResults;
        // Use pretty printer for readable JSON
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(dto);
    }

    /**
     * Recreate TournamentResults from JSON. Note: agentsByName will be empty after this; use
     * {@link #fromJson(String, Map)} if you have a mapping of names to AbstractPlayer instances to restore.
     */
    public static TournamentResults fromJson(String json) throws IOException {
        ObjectMapper om = new ObjectMapper();
        TournamentResultsDTO dto = om.readValue(json, TournamentResultsDTO.class);
        return getTournamentResults(dto);
    }

    /**
     * Recreate TournamentResults from JSON and attach provided AbstractPlayer instances by name.
     * Any names in the provided map that don't appear in the JSON will still be added to agentsByName.
     */
    public static TournamentResults fromJson(String json, Map<String, AbstractPlayer> agentsByName) throws IOException {
        ObjectMapper om = new ObjectMapper();
        TournamentResultsDTO dto = om.readValue(json, TournamentResultsDTO.class);
        TournamentResults tr = extractFromDTO(dto);
        if (agentsByName != null) tr.agentsByName.putAll(agentsByName);
        return tr;
    }

    private static TournamentResults extractFromDTO(TournamentResultsDTO dto) {
        TournamentResults tr = new TournamentResults();
        if (dto.nGamesPlayedPerOpponent != null) tr.nGamesPlayedPerOpponent = dto.nGamesPlayedPerOpponent;
        if (dto.winsPerPlayerPerOpponent != null) tr.winsPerPlayerPerOpponent = dto.winsPerPlayerPerOpponent;
        if (dto.ordinalDeltaPerOpponent != null) tr.ordinalDeltaPerOpponent = dto.ordinalDeltaPerOpponent;
        if (dto.playerResults != null) tr.playerResults = dto.playerResults;
        return tr;
    }

}
