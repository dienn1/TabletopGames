package players.jsonBagPlayers;

import java.util.List;
import java.util.Map;

/**
 * All prototype bags of one value function, compiled into a single dense array laid out so that the distance loop
 * touches one cache line per token.
 * <p>
 * For token id {@code i} the block at {@code i * stride} holds, for every prototype {@code p},
 * {@code packed[i * stride + 2p] = p_k} and {@code packed[i * stride + 2p + 1] = p_k * log2(p_k)}. Comparing a bag
 * against four prototypes therefore reads four values from one contiguous 64-byte block instead of four random
 * locations in four separate arrays.
 */
final class PrototypeSet {

    /** 1 / ln(2): log2(x) as one multiply instead of a second logarithm per call. */
    static final double INV_LN2 = 1.4426950408889634;

    final int count;
    /** {@code 2 * count} doubles per token id. */
    final int stride;
    final double[] packed;
    /** Sum of the normalised values of each prototype (1.0 unless the prototype is degenerate). */
    final double[] totals;
    /** Number of entries in each prototype map, zero counts included: the map version tests emptiness on this. */
    final int[] mapSize;
    /**
     * Per token id, a bit per prototype that gives the token a non-zero probability. A token known to no prototype
     * contributes its own probability to every divergence, so the distance loop can add it once instead of reading
     * four dead blocks - which is 24% of the tokens on Dominion's accumulated trajectory.
     */
    final long[] support;

    private PrototypeSet(int count, int stride, double[] packed, double[] totals, int[] mapSize, long[] support) {
        this.count = count;
        this.stride = stride;
        this.packed = packed;
        this.totals = totals;
        this.mapSize = mapSize;
        this.support = support;
    }

    static PrototypeSet compile(List<? extends Map<String, ? extends Number>> prototypes, TokenDictionary dict) {
        int n = prototypes.size();
        int capacity = 0;
        // first pass: normalise and find the widest id
        int[][] ids = new int[n][];
        double[][] values = new double[n][];
        double[] totals = new double[n];
        int[] mapSize = new int[n];
        for (int p = 0; p < n; p++) {
            Map<String, ? extends Number> prototype = prototypes.get(p);
            double sum = 0.0;
            int nonZero = 0;
            for (Map.Entry<String, ? extends Number> e : prototype.entrySet()) {
                Number v = e.getValue();
                if (v == null) continue;
                sum += v.doubleValue();
                if (v.doubleValue() != 0.0) nonZero++;
            }
            double divisor = sum == 0.0 ? 1.0 : sum;
            ids[p] = new int[nonZero];
            values[p] = new double[nonZero];
            int k = 0;
            double total = 0.0;
            for (Map.Entry<String, ? extends Number> e : prototype.entrySet()) {
                Number v = e.getValue();
                if (v == null) continue;
                double value = v.doubleValue() / divisor;
                if (value == 0.0) continue;
                int id = dict.idOfRaw(e.getKey());
                ids[p][k] = id;
                values[p][k] = value;
                total += value;
                k++;
                capacity = Math.max(capacity, id + 1);
            }
            totals[p] = total;
            mapSize[p] = prototype.size();
        }
        int stride = 2 * n;
        double[] packed = new double[stride * capacity];
        long[] support = new long[capacity];
        for (int p = 0; p < n; p++) {
            for (int k = 0; k < ids[p].length; k++) {
                int index = ids[p][k] * stride + 2 * p;
                packed[index] = values[p][k];
                packed[index + 1] = values[p][k] * log2(values[p][k]);
                support[ids[p][k]] |= 1L << p;
            }
        }
        return new PrototypeSet(n, stride, packed, totals, mapSize, support);
    }

    /**
     * Fills {@code out} with the distance from every prototype to {@code q}, in one pass over the bag. Same value as
     * {@link utilities.Utils#jensenShannonDistance(Map, Map)}, but it only walks the bag's non-zero tokens:
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
     * <p>
     * The inputs the keyed API rejects are rejected here too: a buffer shorter than the prototype count and a bag
     * carrying negative mass throw instead of producing a value.
     */
    void distances(TokenBag q, double[] out) {
        if (out.length < count)
            throw new IllegalArgumentException("need one distance per prototype: " + count + ", got " + out.length);
        if (q == null || q.isEmpty()) {
            // the map version tests Map.isEmpty(), so a prototype that only holds zero counts still counts as present
            for (int p = 0; p < count; p++) out[p] = mapSize[p] == 0 ? 0 : 1;
            return;
        }
        double[] jsDist = new double[count];
        double[] sharedPrototypeMass = new double[count];
        int supportLength = support.length;
        double outsideMass = 0.0;                     // bag mass on tokens no prototype knows
        double inverseTotal = 1.0 / q.total();
        int[] ids = q.nonZeroIds();
        int n = q.nonZeroCount();
        for (int i = 0; i < n; i++) {
            int id = ids[i];
            int rawCount = q.count(id);
            if (rawCount < 0)
                throw new IllegalArgumentException("negative mass at token id " + id + ": " + rawCount);
            if (rawCount == 0) continue;        // a token held at zero contributes nothing, and 0 * log2(0) is NaN
            double qK = rawCount * inverseTotal;
            int base = id * stride;
            long mask = id < supportLength ? support[id] : 0L;
            if (mask == 0L) {
                // a token no prototype has ever seen: contributes q * log2(q / (q/2)) == q to every prototype
                outsideMass += qK;
                continue;
            }
            double qLogQ = qK * log2(qK);
            for (int p = 0; p < count; p++) {
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
        for (int p = 0; p < count; p++) jsDist[p] += outsideMass;
        for (int p = 0; p < count; p++) {
            if (mapSize[p] == 0) {
                out[p] = 1;                                // an empty distribution is at distance 1, by definition
                continue;
            }
            double divergence = jsDist[p] + totals[p] - sharedPrototypeMass[p];
            if (divergence < 0.0) divergence = 0.0;        // guards sqrt against a rounding residue
            out[p] = Math.sqrt(divergence * 0.5);
        }
    }

    /**
     * Calculate log base 2 of a value. {@link Math#log} compiles to a hardware intrinsic on this JVM (about 5 ns), so a
     * table-plus-series replacement was measured to be no faster; only the second logarithm of
     * {@code Math.log(x) / Math.log(2)} is removed, by folding {@code 1/ln(2)} into a constant.
     */
    static double log2(double x) {
        return Math.log(x) * INV_LN2;
    }
}
