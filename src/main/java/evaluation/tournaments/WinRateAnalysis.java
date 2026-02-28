package evaluation.tournaments;

import utilities.Pair;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analysis implementation that sorts agents by their win rate.
 * Win rate is calculated as the mean of points (where wins are 1.0 and draws are fractional).
 */
public class WinRateAnalysis implements IResultsAnalysis {
    @Override
    public LinkedHashMap<String, Pair<Double, Double>> getRanking(TournamentResults results) {
        LinkedHashMap<String, Pair<Double, Double>> ranking = new LinkedHashMap<>();
        for (String name : results.getAllAgentNames()) {
            List<Double> points = results.getPlayerPoints(name);
            if (points.isEmpty()) continue;

            double n = points.size();
            double sum = 0;
            double sumSq = 0;
            for (double p : points) {
                sum += p;
                sumSq += p * p;
            }

            double mean = sum / n;
            double stdDev = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
            double stdError = stdDev / Math.sqrt(n);

            ranking.put(name, new Pair<>(mean, stdError));
        }

        // Sort by mean win rate descending (highest win rate first)
        return ranking.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((o1, o2) -> o2.a.compareTo(o1.a)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
                        LinkedHashMap::new));
    }
}
