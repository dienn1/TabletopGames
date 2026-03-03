package players.jsonBagPlayers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JensenShannonDistance {

    /**
     * Normalize a map so that the sum of its values is 1.
     * @param p The map to normalize
     * @return A normalized map with double values
     */
    public static Map<String, Double> normalize(Map<String, Integer> p) {
        Map<String, Double> result = new HashMap<>();
        double total = 0;
        for (int value : p.values()) {
            total += value;
        }
        if (total == 0) {
            total = 1;
        }
        for (Map.Entry<String, Integer> entry : p.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / total);
        }
        return result;
    }

    /**
     * Calculate the Jensen-Shannon distance between two probability distributions.
     * @param p First distribution as a map of string keys to integer counts
     * @param q Second distribution as a map of string keys to integer counts
     * @return The Jensen-Shannon distance (between 0 and 1)
     */
    public static double jensenShannonDistance(Map<String, Integer> p, Map<String, Integer> q) {
        // Handle empty cases
        if ((p == null || p.isEmpty()) && (q == null || q.isEmpty())) {
            return 0;
        }
        if (p == null || p.isEmpty() || q == null || q.isEmpty()) {
            return 1;
        }

        // Normalize the distributions
        Map<String, Double> pNorm = normalize(p);
        Map<String, Double> qNorm = normalize(q);

        // Get all keys from both distributions
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(pNorm.keySet());
        allKeys.addAll(qNorm.keySet());

        double jsDist = 0;
        for (String k : allKeys) {
            double pK = pNorm.getOrDefault(k, 0.0);
            double qK = qNorm.getOrDefault(k, 0.0);
            double mK = (pK + qK) / 2;

            if (pK != 0) {
                jsDist += pK * log2(pK / mK);
            }
            if (qK != 0) {
                jsDist += qK * log2(qK / mK);
            }
        }

        return Math.sqrt(jsDist * 0.5);
    }

    /**
     * Calculate log base 2 of a value.
     * @param x The value
     * @return log2(x)
     */
    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    // Example usage
    public static void main(String[] args) {
        Map<String, Integer> p = new HashMap<>();
        p.put("a", 10);
        p.put("b", 20);
        p.put("c", 30);

        Map<String, Integer> q = new HashMap<>();
        q.put("a", 15);
        q.put("b", 25);
        q.put("d", 10);

        double distance = jensenShannonDistance(p, q);
        System.out.println("Jensen-Shannon Distance: " + distance);
    }
}
