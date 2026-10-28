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

    final int count;
    /** {@code 2 * count} doubles per token id. */
    final int stride;
    final double[] packed;
    /** Sum of the normalised values of each prototype (1.0 unless the prototype is degenerate). */
    final double[] totals;
    /** Number of entries in each prototype map, zero counts included: the map version tests emptiness on this. */
    final int[] mapSize;

    private PrototypeSet(int count, int stride, double[] packed, double[] totals, int[] mapSize) {
        this.count = count;
        this.stride = stride;
        this.packed = packed;
        this.totals = totals;
        this.mapSize = mapSize;
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
        for (int p = 0; p < n; p++) {
            for (int k = 0; k < ids[p].length; k++) {
                int index = ids[p][k] * stride + 2 * p;
                packed[index] = values[p][k];
                packed[index + 1] = values[p][k] * JensenShannonDistance.log2(values[p][k]);
            }
        }
        return new PrototypeSet(n, stride, packed, totals, mapSize);
    }
}
