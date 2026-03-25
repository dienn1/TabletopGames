package evaluation.tournaments;

import utilities.Pair;
import java.util.LinkedHashMap;

/**
 * Interface for analyzing tournament results.
 * This replaces the aggregate information previously stored in RoundRobinTournament.
 */
public interface IResultsAnalysis {
    /**
     * Analyze the results and return a ranking of the agents.
     * The ranking is returned as a LinkedHashMap where the key is the agent name,
     * and the value is a Pair containing the mean value and the standard error.
     * The map should be sorted according to the ranking.
     *
     * @param results The tournament results to analyze.
     * @return A sorted map of agent names to their mean value and standard error.
     */
    LinkedHashMap<String, Pair<Double, Double>> getRanking(TournamentResults results);
}
