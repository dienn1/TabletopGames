package evaluation.tournaments;

import core.AbstractPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to store the core results of a tournament.
 * This includes:
 * Agent_Name1, Agent_Name2, NumberOfGames, VictoriesOfPlayer1, OrdinalDeltaOf1Over2
 * Map from AgentName to the AbstractPlayer
 */
public class TournamentResults {
    private final Map<String, AbstractPlayer> agentsByName = new HashMap<>();
    private final Map<String, Map<String, Integer>> nGamesPlayedPerOpponent = new HashMap<>();
    private final Map<String, Map<String, Integer>> winsPerPlayerPerOpponent = new HashMap<>();
    private final Map<String, Map<String, Integer>> ordinalDeltaPerOpponent = new HashMap<>();

    public void addAgent(AbstractPlayer agent) {
        agentsByName.put(agent.toString(), agent);
    }

    public AbstractPlayer getAgent(String name) {
        return agentsByName.get(name);
    }

    public List<String> getAllAgentNames() {
        return agentsByName.keySet().stream().toList();
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
