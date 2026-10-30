package players.jsonBagPlayers;

import java.util.HashMap;
import java.util.Map;

public class JensenShannonDistance {

    /** 1 / ln(2): log2(x) as one multiply instead of a second logarithm per call. */
    static final double INV_LN2 = 1.4426950408889634;

    /**
     * Normalize a map so that the sum of its values is 1.
     * @param p The map to normalize
     * @return A normalized map with double values
     */
    public static Map<String, Double> normalize(Map<String, ? extends Number> p) {
        Map<String, Double> result = new HashMap<>(p.size());
        if (p.isEmpty()) return result;
        double total = 0.0;
        Number sampleValue = null;
        for (Number value : p.values()) {
            if (value != null) {
                total += value.doubleValue();
                if (sampleValue == null) sampleValue = value;
            }
        }

        // Fast-path: if already normalized and values are known to be homogeneous, one sample check is enough.
        if (Math.abs(total - 1.0) < 0.00001 && sampleValue instanceof Double) {
            @SuppressWarnings("unchecked")
            Map<String, Double> same = (Map<String, Double>) p;
            return same;
        }

        if (Double.compare(total, 0.0) == 0) total = 1.0;

        for (Map.Entry<String, ? extends Number> entry : p.entrySet()) {
            double dv = entry.getValue().doubleValue();
            result.put(entry.getKey(), dv / total);
        }
        return result;
    }

    /**
     * Calculate the Jensen-Shannon distance between two probability distributions.
     * @param p First distribution as a map of string keys to integer counts
     * @param q Second distribution as a map of string keys to integer counts
     * @return The Jensen-Shannon distance (between 0 and 1)
     */
    public static double jsd(Map<String, ? extends Number> p, Map<String, ? extends Number> q) {
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

        double jsDist = 0;
        // Process keys in pNorm, looking up each in qNorm once
        for (Map.Entry<String, Double> entry : pNorm.entrySet()) {
            double pK = entry.getValue();
            Double qVal = qNorm.get(entry.getKey());
            double qK = (qVal != null) ? qVal : 0.0;
            double mK = (pK + qK) / 2;

            if (pK != 0) {
                jsDist += pK * log2(pK / mK);
            }
            if (qK != 0) {
                jsDist += qK * log2(qK / mK);
            }
        }
        // Process keys only in qNorm
        for (Map.Entry<String, Double> entry : qNorm.entrySet()) {
            if (!pNorm.containsKey(entry.getKey())) {
                double qK = entry.getValue();
                if (qK != 0) {
                    double mK = qK / 2;
                    jsDist += qK * log2(qK / mK);
                }
            }
        }

        return Math.sqrt(jsDist * 0.5);
    }

    /**
     * Fills {@code out} with the distance from every prototype to {@code q}, in one pass over the bag. Same value as
     * the map version, but it only walks the bag's non-zero tokens:
     * <ul>
     *   <li>tokens in both distributions contribute the usual two divergence terms;</li>
     *   <li>tokens only in the bag contribute exactly their probability;</li>
     *   <li>tokens only in the prototype contribute exactly their probability, which is
     *       {@code prototypeTotal - sum(shared prototype mass)} and therefore needs no iteration.</li>
     * </ul>
     * The pairwise term is written as
     * {@code (p + q) * (1 - log2(p + q)) + p * log2(p) + q * log2(q)}, so a shared token costs one logarithm per
     * prototype instead of two, {@code p * log2(p)} comes precomputed from the prototype, and the bag's own
     * {@code q * log2(q)} is computed once for all prototypes.
     */
    static void distances(PrototypeSet prototypes, TokenBag q, double[] out) {
        int nPrototypes = prototypes.count;
        if (q == null || q.isEmpty()) {
            // the map version tests Map.isEmpty(), so a prototype that only holds zero counts still counts as present
            for (int p = 0; p < nPrototypes; p++) out[p] = prototypes.mapSize[p] == 0 ? 0 : 1;
            return;
        }
        double[] jsDist = new double[nPrototypes];
        double[] sharedPrototypeMass = new double[nPrototypes];
        long[] support = prototypes.support;
        int supportLength = support.length;
        double outsideMass = 0.0;                     // bag mass on tokens no prototype knows
        double inverseTotal = 1.0 / q.total();
        double[] packed = prototypes.packed;
        int stride = prototypes.stride;
        int packedLength = packed.length;
        int[] ids = q.nonZeroIds();
        int n = q.nonZeroCount();
        for (int i = 0; i < n; i++) {
            int id = ids[i];
            int rawCount = q.count(id);
            if (rawCount <= 0) continue;        // a token held at zero contributes nothing, and 0 * log2(0) is NaN
            double qK = rawCount * inverseTotal;
            int base = id * stride;
            long mask = id < supportLength ? support[id] : 0L;
            if (mask == 0L) {
                // a token no prototype has ever seen: contributes q * log2(q / (q/2)) == q to every prototype
                outsideMass += qK;
                continue;
            }
            double qLogQ = qK * log2(qK);
            for (int p = 0; p < nPrototypes; p++) {
                if ((mask & (1L << p)) == 0L) {
                    jsDist[p] += qK;                  // this prototype has no mass on the token
                    continue;
                }
                double pK = packed[base + 2 * p];
                if (pK == 0.0) {
                    jsDist[p] += qK;
                } else {
                    double sum = pK + qK;
                    jsDist[p] += sum * (1.0 - log2(sum)) + packed[base + 2 * p + 1] + qLogQ;
                    sharedPrototypeMass[p] += pK;
                }
            }
        }
        for (int p = 0; p < nPrototypes; p++) jsDist[p] += outsideMass;
        for (int p = 0; p < nPrototypes; p++) {
            if (prototypes.mapSize[p] == 0) {
                out[p] = 1;                                // an empty distribution is at distance 1, by definition
                continue;
            }
            double divergence = jsDist[p] + prototypes.totals[p] - sharedPrototypeMass[p];
            if (divergence < 0.0) divergence = 0.0;        // guards sqrt against a rounding residue
            out[p] = Math.sqrt(divergence * 0.5);
        }
    }

    /**
     * Calculate log base 2 of a value. {@link Math#log} compiles to a hardware intrinsic on this JVM (about 5 ns), so a
     * table-plus-series replacement was measured to be no faster; only the second logarithm of
     * {@code Math.log(x) / Math.log(2)} is removed, by folding {@code 1/ln(2)} into a constant.
     *
     * @param x The value
     * @return log2(x)
     */
    static double log2(double x) {
        return Math.log(x) * INV_LN2;
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

        double distance = jsd(p, q);
        System.out.println("Jensen-Shannon Distance: " + distance);
    }
}
