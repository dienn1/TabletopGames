package players.jsonBagPlayers;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A JSON-Bag: token counts held as a dense int array indexed by {@link TokenDictionary} id.
 * <p>
 * The tokeniser and the distance function work on the dense array, so a bag costs no hashing and no boxing in the
 * hot path. {@code Map<String,Integer>} is still the public view - {@link #get}, {@link #put} and {@link #entrySet}
 * materialise token strings - so existing callers and experiments keep working unchanged.
 * <p>
 * The non-zero ids are tracked in insertion order, so iterating a bag costs O(distinct tokens) rather than
 * O(vocabulary).
 */
public final class TokenBag extends AbstractMap<String, Integer> {

    private final TokenDictionary dict;
    private int[] counts;
    private int[] nonZeroIds;
    private int nNonZero;
    private long total;      // long: a saturated frequency token can push the sum past the int range

    // optional write log, so a temporary merge can be undone instead of copying the whole bag
    private int[] recordedIds;
    private int[] recordedCounts;
    private int recorded;
    private int[] marks = new int[4];
    private int depth;
    private boolean recording;

    public TokenBag(TokenDictionary dict) {
        this.dict = dict;
        int cap = Math.max(16, dict.size());
        counts = new int[cap];
        nonZeroIds = new int[cap];
    }

    public TokenDictionary dictionary() {
        return dict;
    }

    // ------------------------------------------------------------------ dense API

    public void add(int id, int count) {
        if (id < 0 || count == 0) return;
        if (id >= counts.length) grow(id + 1);
        boolean wasZero = counts[id] == 0;
        int newCount = counts[id] + count;
        if (newCount < 0) {
            if (count < 0) {
                // only rollback reduces a count, and only by what it added, so this is a bug in the caller
                throw new IllegalArgumentException("token count would go negative: " + counts[id] + " + " + count);
            }
            newCount = Integer.MAX_VALUE;       // a frequency past the int range saturates rather than wrapping
            count = newCount - counts[id];
        }
        if (wasZero) {
            if (nNonZero == nonZeroIds.length) nonZeroIds = Arrays.copyOf(nonZeroIds, nonZeroIds.length * 2);
            nonZeroIds[nNonZero++] = id;
        }
        counts[id] = newCount;
        total += count;
        if (newCount == 0) removeNonZero(id);
        if (recording) {
            if (recorded == recordedIds.length) {
                recordedIds = Arrays.copyOf(recordedIds, recorded * 2);
                recordedCounts = Arrays.copyOf(recordedCounts, recorded * 2);
            }
            recordedIds[recorded] = id;
            recordedCounts[recorded] = count;
            recorded++;
        }
    }

    /**
     * Starts logging adds, so {@link #rollback()} can undo them; used to value a state without copying the bag.
     * Nested calls are supported: each {@link #rollback()} undoes the adds made since its own call.
     */
    public void startRecording() {
        if (recordedIds == null) {
            recordedIds = new int[64];
            recordedCounts = new int[64];
        }
        if (depth == marks.length) marks = Arrays.copyOf(marks, depth * 2);
        marks[depth++] = recorded;
        recording = true;
    }

    /** Undoes every {@link #add(int, int)} since the matching {@link #startRecording()}. */
    public void rollback() {
        if (depth == 0) throw new IllegalStateException("rollback() without a matching startRecording()");
        int mark = marks[--depth];
        for (int i = recorded - 1; i >= mark; i--) {
            int id = recordedIds[i];
            int count = recordedCounts[i];
            counts[id] -= count;
            total -= count;
            if (counts[id] == 0) removeNonZero(id);
        }
        recorded = mark;
        if (depth == 0) recording = false;
    }

    public void addAll(TokenBag other) {
        if (other == this || other.nNonZero == 0) return;
        if (other.counts.length > counts.length) grow(other.counts.length);
        for (int i = 0; i < other.nNonZero; i++) {
            int id = other.nonZeroIds[i];
            int c = other.counts[id];
            if (counts[id] == 0) {
                if (nNonZero == nonZeroIds.length) nonZeroIds = Arrays.copyOf(nonZeroIds, nonZeroIds.length * 2);
                nonZeroIds[nNonZero++] = id;
            }
            counts[id] += c;
            total += c;
        }
    }

    /** Copies {@code other} into this bag, translating ids through the token strings (bags may use different dictionaries). */
    void copyTokensFrom(TokenBag other) {
        clear();
        if (other == this) return;
        int[] ids = other.nonZeroIds;
        for (int i = 0; i < other.nNonZero; i++) {
            int id = ids[i];
            add(dict.idOfRaw(other.dict.token(id)), other.counts[id]);
        }
    }

    public int count(int id) {
        return id >= 0 && id < counts.length ? counts[id] : 0;
    }

    /** Sum of all counts (the denominator of the normalised distribution). */
    public long total() {
        return total;
    }

    public int distinct() {
        return nNonZero;
    }

    int[] nonZeroIds() {
        return nonZeroIds;
    }

    int nonZeroCount() {
        return nNonZero;
    }

    public boolean isEmpty() {
        return nNonZero == 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < nNonZero; i++) counts[nonZeroIds[i]] = 0;
        nNonZero = 0;
        total = 0;
    }

    private void grow(int min) {
        int newLen = Math.max(min, counts.length * 2);
        counts = Arrays.copyOf(counts, newLen);
    }

    // ------------------------------------------------------------------ Map view

    @Override
    public Integer get(Object key) {
        if (!(key instanceof String token)) return null;
        int id = dict.idOf(token);
        return id < 0 || count(id) == 0 ? null : counts[id];
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public Integer put(String key, Integer value) {
        int id = dict.idOfRaw(key);
        if (id < 0) return null;
        if (id >= counts.length) grow(id + 1);
        Integer old = counts[id] == 0 ? null : counts[id];
        if (value == 0) {
            if (old != null) removeId(id, old);
            return old;
        }
        if (counts[id] == 0) {
            if (nNonZero == nonZeroIds.length) nonZeroIds = Arrays.copyOf(nonZeroIds, nonZeroIds.length * 2);
            nonZeroIds[nNonZero++] = id;
        }
        total += value - counts[id];
        counts[id] = value;
        return old;
    }

    @Override
    public Integer remove(Object key) {
        if (!(key instanceof String token)) return null;
        Integer old = get(token);
        if (old == null) return null;
        removeId(dict.idOf(token), old);
        return old;
    }

    /** Removes an id from the non-zero list; scanning from the end keeps it O(1) for recently added ids. */
    private void removeNonZero(int id) {
        for (int i = nNonZero - 1; i >= 0; i--) {
            if (nonZeroIds[i] == id) {
                removeNonZeroAt(i);
                return;
            }
        }
    }

    /** Removes the entry at position {@code index} by moving the last entry into its place. */
    private void removeNonZeroAt(int index) {
        if (index < 0 || index >= nNonZero) return;
        nonZeroIds[index] = nonZeroIds[--nNonZero];
    }

    private void removeId(int id, int count) {
        counts[id] = 0;
        total -= count;
        for (int i = 0; i < nNonZero; i++) {
            if (nonZeroIds[i] == id) {
                nonZeroIds[i] = nonZeroIds[--nNonZero];
                return;
            }
        }
    }

    @Override
    public int size() {
        return nNonZero;
    }

    @Override
    public Set<Entry<String, Integer>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<String, Integer>> iterator() {
                return new Iterator<>() {
                    int i = 0;
                    int lastId = -1;
                    int lastCount = 0;

                    @Override
                    public boolean hasNext() {
                        return i < nNonZero;
                    }

                    @Override
                    public Entry<String, Integer> next() {
                        if (i >= nNonZero) throw new NoSuchElementException();
                        lastId = nonZeroIds[i++];
                        lastCount = counts[lastId];
                        return new SimpleImmutableEntry<>(dict.token(lastId), lastCount);
                    }

                    @Override
                    public void remove() {
                        if (lastId < 0) throw new IllegalStateException("next() has not been called");
                        removeId(lastId, counts[lastId]);       // also drops the id from the non-zero list
                        i--;                                   // re-examine the entry swapped into this slot
                        lastId = -1;
                    }
                };
            }

            @Override
            public int size() {
                return nNonZero;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < nNonZero; i++) {
            int id = nonZeroIds[i];
            if (i > 0) sb.append(", ");
            sb.append(dict.token(id)).append('=').append(counts[id]);
        }
        return sb.append('}').toString();
    }
}
