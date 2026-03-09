package evaluation.tournaments;

import utilities.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ParetoAnalysis implements IResultsAnalysis {

    @Override
    public LinkedHashMap<String, Pair<Double, Double>> getRanking(TournamentResults results) {
        List<String> paretoFront = getParetoFront(results);
        // we then remove the first pareto front, to find the second pareto front, and so on until we have no agents left

        LinkedHashMap<String, Pair<Double, Double>> ranking = new LinkedHashMap<>();
        int rank = 1;
        while (!paretoFront.isEmpty()) {
            for (String agent : paretoFront) {
                double winRate = results.getPlayerResults(agent).stream()
                        .mapToInt(r -> r.win).sum() / (double) results.getPlayerResults(agent).size();
                ranking.put(agent, new Pair<>(rank + 0.0, winRate));
            }
            rank++;
            TournamentResults copy = results.shallowCopy();
            for (String agent : paretoFront) {
                copy.filterPlayer(agent);
            }
            paretoFront = getParetoFront(copy);
        }

        return ranking;
    }

    private List<String> getParetoFront(TournamentResults results) {
        List<String> paretoFront = new ArrayList<>(results.getAllAgentNames());
        for (String agent : results.getAllAgentNames()) {
            // if any other agent has a better win rate against every other agent, then agent cannot be in the Pareto front
            for (String otherAgent : results.getAllAgentNames()) {
                if (otherAgent.equals(agent)) continue;
                if (!results.getAllAgentNames().stream()
                        .filter(a -> !(a.equals(otherAgent) || a.equals(agent)))
                        .allMatch(a -> results.getWins(otherAgent, a) > results.getWins(agent, a))) {
                    paretoFront.remove(agent); // another agent dominates this agent, so remove it from the Pareto front
                    break;
                }
            }
        }
        return paretoFront;
    }
}
