package evaluation.tournaments;

import utilities.Pair;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analysis implementation that sorts agents by their ordinal position.
 * Lower mean ordinal position is considered better (1st place is 1).
 */
public class OrdinalAnalysis implements IResultsAnalysis {
    @Override
    public LinkedHashMap<String, Pair<Double, Double>> getRanking(TournamentResults results) {
        LinkedHashMap<String, Pair<Double, Double>> ranking = new LinkedHashMap<>();
        for (String name : results.getAllAgentNames()) {
            List<Integer> ordinals = results.getPlayerOrdinals(name);
            if (ordinals.isEmpty()) continue;

            double n = ordinals.size();
            double sum = 0;
            double sumSq = 0;
            for (int r : ordinals) {
                sum += r;
                sumSq += (double) r * r;
            }

            double mean = sum / n;
            double stdDev = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
            double stdError = stdDev / Math.sqrt(n);

            ranking.put(name, new Pair<>(mean, stdError));
        }

        // Sort by mean ordinal position ascending (lowest/best first)
        return ranking.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(o -> o.a)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
                        LinkedHashMap::new));
    }
}
