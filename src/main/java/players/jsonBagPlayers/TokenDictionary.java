package players.jsonBagPlayers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Intern table that maps a token (a JSON path plus its atomic value) to a small integer id.
 * <p>
 * The tokeniser builds token names in a reusable char buffer with a running 64-bit hash (FNV-1a), so the hot path
 * never allocates a String: a token that has been seen before is found by probing its hash in place, and only a
 * genuinely new token is turned into a String. Counts and distances are then integer-array work instead of hashing
 * long keys.
 * <p>
 * Identity is the 64-bit hash plus the length. Comparing a 50-character token character by character cost more than
 * the probe itself (measured: about 35 ns of the 80 ns per token), so it is dropped: two distinct tokens can only be
 * confused if their 64-bit FNV-1a hashes and lengths agree, which for a 45k-token vocabulary has a probability around
 * 1e-10. Set {@code -Djsonbag.verifyTokens=true} to compare characters as well; the equivalence harness runs with it
 * on, which is how the token digests are shown to be exact.
 * <p>
 * The optional token filter (whitelist or blacklist) is evaluated once per distinct token and cached, so a filtered
 * token costs the same as an accepted one after the first sighting. {@link #setFilter} bumps a generation stamp and
 * invalidates the cached decisions without dropping the ids, so a bag built under one filter stays readable when the
 * filter changes.
 * <p>
 * Not thread-safe: one dictionary belongs to one tokeniser or one heuristic.
 */
public final class TokenDictionary {

    /** Returned by lookups that are filtered out; the receiving bag ignores it. */
    public static final int DROPPED = -1;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final boolean VERIFY = Boolean.getBoolean("jsonbag.verifyTokens");

    private String[] tokens = new String[64];
    private long[] hashes = new long[64];
    private int[] lengths = new int[64];
    /** Per-token filter verdict: 0 unknown, {@link #YES} allowed, {@link #NO} dropped. Cleared when the filter changes. */
    private byte[] accepted = new byte[64];
    private static final byte YES = 1, NO = 2;
    private int[] table = new int[128];                 // open addressing; stores id + 1, 0 means empty
    private int mask = 127;
    private int size;



    private Set<String> filterSet;
    private boolean whitelist;
    private int generation = 1;

    // ------------------------------------------------------------------ whitelist prefix table
    // A token that no whitelisted token can reach is rejected however it is built, so the tokeniser may skip the
    // subtree that leads to it. The test is one bit in this bitmap: every whitelisted token and every prefix of one
    // that ends at a position the path can be extended from sets a bit derived from its 32-bit hash. Linear probing
    // in a 16 kB slot table was the expensive part of pruning (39% of `evaluateState` on Dominion when measured), and
    // a bit test is one load with no compare loop. A hash collision makes the test say "live" where it is not, which
    // costs one token built and rejected as before - it can never drop a token the filter would have kept - and at
    // these whitelist sizes that happens on about one dead probe in sixty.
    private long[] prefixBits;
    private int prefixEntries;
    private boolean pruning;

    /** 2^17 bits = 16 kB, which holds these whitelists at a false-positive rate under 2%. */
    private static final int PREFIX_BITS_LOG2 = 17;
    private static final int PREFIX_BITS = 1 << PREFIX_BITS_LOG2;
    private static final int PREFIX_INDEX_MASK = PREFIX_BITS - 1;
    /** Above this many prefixes the bitmap saturates and pruning costs more than it saves. */
    private static final int MAX_PREFIX_ENTRIES = 8192;

    /** {@code -Djsonbag.prune=false} disables path pruning, so its effect can be ablated. */
    private static final boolean PRUNE = !"false".equals(System.getProperty("jsonbag.prune"));

    /** Number of distinct prefixes in the bitmap; 0 when pruning is off. Diagnostics only. */
    public int prefixCount() {
        return pruning ? prefixEntries : 0;
    }


    public int size() {
        return size;
    }

    /** The token for an id, or null. */
    public String token(int id) {
        return id >= 0 && id < size ? tokens[id] : null;
    }

    /** Id of an existing token, or {@link #DROPPED} if it has never been seen. No filter is consulted. */
    public int idOf(String token) {
        long h = hashOf(token);
        int i = start(h, mask);
        while (true) {
            int slot = table[i];
            if (slot == 0) return DROPPED;
            int id = slot - 1;
            if (hashes[id] == h && lengths[id] == token.length() && (!VERIFY || tokens[id].equals(token))) return id;
            i = (i + 1) & mask;
        }
    }

    /** Interns a token regardless of the filter (used to compile prototypes). */
    public int idOfRaw(String token) {
        int id = idOf(token);
        return id != DROPPED ? id : insert(token, hashOf(token), -1);
    }

    /** Id of the token held in {@code buf[0..len)}, interned on first sight. Honours the configured filter. */
    public int idOf(char[] buf, int len) {
        return idOf(buf, len, hashOf(buf, len));
    }

    /**
     * Same as {@link #idOf(char[], int)} with the token's hash already computed by the caller (the tokeniser carries
     * a running hash as it builds the path).
     */
    public int idOf(char[] buf, int len, long hash) {
        int i = start(hash, mask);
        while (true) {
            int entry = table[i];
            if (entry == 0) break;
            int id = entry - 1;
            if (hashes[id] == hash && (!VERIFY || regionEquals(tokens[id], buf, len))) {
                return accepted(id) ? id : DROPPED;
            }
            i = (i + 1) & mask;
        }
        String token = new String(buf, 0, len);
        int newId = insert(token, hash, isAllowed(token) ? 1 : 0);
        return accepted[newId] == YES ? newId : DROPPED;
    }

    public void setFilter(Set<String> filterSet, boolean whitelist) {
        boolean same = whitelist == this.whitelist
                && (filterSet == null ? this.filterSet == null || this.filterSet.isEmpty()
                                      : filterSet.equals(this.filterSet));
        if (same) return;
        this.filterSet = filterSet;
        this.whitelist = whitelist;
        this.generation++;
        if (accepted.length > 0) Arrays.fill(accepted, (byte) 0);   // every verdict belongs to the old filter
        rebuildPrefixTable();
    }

    /** True when the tokeniser may skip anything that cannot reach a whitelisted token. */
    public boolean pruning() {
        return pruning;
    }

    /**
     * True when the path with this running hash is either a whitelisted token or a prefix of one that ends where the
     * tokeniser can extend it, i.e. when some whitelisted token could still be produced below this path. False means
     * every token that path can produce is rejected, so the subtree that leads to it cannot change the bag. Returns
     * true (no pruning) whenever no whitelist is in force or its prefix set saturated the bitmap.
     */
    public boolean live(long hash) {
        if (!pruning) return true;
        int bit = prefixBit(hash);
        return (prefixBits[bit >>> 6] & (1L << bit)) != 0;
    }

    /** The bit a 32-bit token hash selects. Mixing the high half in stops FNV's low bits clustering on short tokens. */
    private static int prefixBit(long hash) {
        int h = (int) (hash ^ (hash >>> 32));
        return (h ^ (h >>> 16)) & PREFIX_INDEX_MASK;
    }

    /**
     * Builds the prefix bitmap from the current whitelist. Every whitelisted token sets a bit, and so does every
     * prefix of one that ends where the tokeniser can extend a path: immediately before a `.` or a `[`, and (so that
     * one probe can answer "can any indexed token live under this array?") immediately after a `[`. A whitelist whose
     * prefix set would saturate the bitmap switches pruning off instead.
     */
    private void rebuildPrefixTable() {
        pruning = false;
        prefixBits = null;
        prefixEntries = 0;
        if (!PRUNE || !whitelist || filterSet == null || filterSet.isEmpty()) return;

        Set<Integer> distinct = new HashSet<>();
        for (String token : filterSet) {
            if (token == null || token.length() < 2) continue;
            distinct.add(prefixBit(hashOf(token)));
            for (int i = 1; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == '.' || c == '[') distinct.add(prefixBit(hashOf(token, i)));
            }
        }
        if (distinct.size() > MAX_PREFIX_ENTRIES) return;      // too saturated to pay for itself
        prefixBits = new long[PREFIX_BITS >>> 6];
        for (int bit : distinct) prefixBits[bit >>> 6] |= 1L << bit;
        prefixEntries = distinct.size();
        pruning = true;
    }


    public boolean hasFilter() {
        return filterSet != null && !filterSet.isEmpty();
    }


    private static int start(long hash, int mask) {
        long mixed = hash ^ (hash >>> 32);
        return (int) (mixed ^ (mixed >>> 16)) & mask;
    }

    private boolean accepted(int id) {
        if (!hasFilter()) return true;
        byte verdict = accepted[id];
        if (verdict == 0) {                       // not decided under the current filter
            verdict = isAllowed(tokens[id]) ? YES : NO;
            accepted[id] = verdict;
        }
        return verdict == YES;
    }

    private boolean isAllowed(String token) {
        if (filterSet == null || filterSet.isEmpty()) return true;
        return whitelist == filterSet.contains(token);
    }

    private int insert(String token, long hash, int acceptedFlag) {
        if (size == tokens.length) grow();
        int id = size++;
        tokens[id] = token;
        hashes[id] = hash;
        lengths[id] = token.length();
        accepted[id] = acceptedFlag > 0 ? YES : acceptedFlag == 0 ? NO : 0;   // raw inserts (-1) carry no verdict yet
        int i = start(hash, mask);
        while (table[i] != 0) i = (i + 1) & mask;
        table[i] = id + 1;
        return id;
    }

    private void grow() {
        int newLen = tokens.length * 2;
        tokens = Arrays.copyOf(tokens, newLen);
        hashes = Arrays.copyOf(hashes, newLen);
        lengths = Arrays.copyOf(lengths, newLen);
        accepted = Arrays.copyOf(accepted, newLen);
        if (size * 2 >= table.length) rehash();
    }

    private void rehash() {
        table = new int[table.length * 2];
        mask = table.length - 1;
        for (int id = 0; id < size; id++) {
            int i = start(hashes[id], mask);
            while (table[i] != 0) i = (i + 1) & mask;
            table[i] = id + 1;
        }
    }

    /** FNV-1a over a char buffer. */
    static long hashOf(char[] buf, int len) {
        long h = FNV_OFFSET;
        for (int i = 0; i < len; i++) {
            h ^= buf[i];
            h *= FNV_PRIME;
        }
        return h;
    }

    /** FNV-1a over a String; the same function the tokeniser applies to its buffer. */
    static long hashOf(String s) {
        long h = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    /** FNV-1a over the first {@code len} characters of a String, i.e. the hash of a path prefix. */
    static long hashOf(String s, int len) {
        long h = FNV_OFFSET;
        for (int i = 0; i < len; i++) {
            h ^= s.charAt(i);
            h *= FNV_PRIME;
        }
        return h;
    }

    private static boolean regionEquals(String s, char[] buf, int len) {
        if (s.length() != len) return false;
        for (int i = 0; i < len; i++) if (s.charAt(i) != buf[i]) return false;
        return true;
    }
}
