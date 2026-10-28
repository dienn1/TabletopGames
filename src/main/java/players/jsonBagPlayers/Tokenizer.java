package players.jsonBagPlayers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import core.AbstractGameState;
import core.AbstractGameStateContainer;
import org.apache.commons.lang3.NotImplementedException;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Turns an object, a JSON string or a game state into a JSON-Bag: a bag of tokens, where a token is the path to an
 * atomic value plus that value (for example {@code .playerHands[0].cards[3].cardType.COPPER}).
 * <p>
 * The tokeniser writes into a {@link TokenBag}: token names are built in a reusable char buffer, the running hash is
 * carried along with the buffer, and tokens are interned to small integer ids - so a repeated token costs no
 * allocation, no string building and no second pass to hash it. The {@code Map<String,Integer>} returned by the
 * public entry points is a view over the same dense counts.
 * <p>
 * Two behaviours are worth knowing about:
 * <ul>
 *   <li>With {@code mode=both} each atomic value is emitted twice - once with array indices and once without.</li>
 *   <li>With {@code numSuffixFrequency} a positive integer value also adds its bare key as a token, counted with the
 *       value as its frequency.</li>
 * </ul>
 */
public class Tokenizer {
    private static final Gson gson = new Gson();

    /** Per-thread walker: the char buffer, its running hash and the recursion state are reused across calls. */
    private static final ThreadLocal<Walk> WALK = ThreadLocal.withInitial(Walk::new);

    /**
     * Tokenizes a JSON string using the default configuration.
     * Numeric atomic values are also counted under their base prefix when possible.
     */
    public static Map<String, Integer> tokenize(String json) {
        return tokenize(json, "", false, "both", true, false, false, false, null, false);
    }

    /**
     * Tokenizes a JSON string with optional token filtering.
     * Numeric atomic values are also counted under their base prefix when possible.
     */
    public static Map<String, Integer> tokenize(String json, Set<String> filterSet, boolean whitelist) {
        return tokenize(json, "", false, "both", true, false, false, false, filterSet, whitelist);
    }

    /**
     * Tokenizes an arbitrary object using the default configuration.
     * Numeric atomic values are also counted under their base prefix when possible.
     */
    public static Map<String, Integer> tokenize(Object obj) {
        return tokenize(obj, "", true, "both", true, false, false, false, null, false);
    }

    /**
     * Tokenizes an arbitrary object with optional token filtering.
     * Numeric atomic values are also counted under their base prefix when possible.
     */
    public static Map<String, Integer> tokenize(Object obj, Set<String> filterSet, boolean whitelist) {
        return tokenize(obj, "", true, "both", true, false, false, false, filterSet, whitelist);
    }

    public static Map<String, Integer> tokenize(AbstractGameState gs) {
        AbstractGameStateContainer gsContainer = GameStateContainerFactory.createContainer(gs);
        return Tokenizer.tokenize(gsContainer);
    }

    public static Map<String, Integer> tokenize(AbstractGameState gs, Set<String> filterSet, boolean whitelist) {
        AbstractGameStateContainer gsContainer = GameStateContainerFactory.createContainer(gs);
        return Tokenizer.tokenize(gsContainer, filterSet, whitelist);
    }

    /**
     * Tokenizes an arbitrary object with full control over prefixing, ordering, and filtering.
     *
     * @param obj the object to tokenize
     * @param prefix prefix applied to generated tokens
     * @param ordered whether ordered array positions should be included
     * @param mode tokenization mode: both, ordered, unordered, or char
     * @param numSuffixFrequency when true, numeric atomic values also contribute the base prefix token as a frequency
     * @param filterPlayer whether to skip objects with player > 0
     * @param binning whether to bin numeric x/y values
     * @param pairXY whether to combine x/y into a single token
     * @param filterSet whitelist or blacklist token set
     * @param whitelist true for whitelist filtering, false for blacklist filtering
     * @return token frequency map
     */
    public static Map<String, Integer> tokenize(Object obj, String prefix, boolean ordered,
                                                String mode, boolean numSuffixFrequency, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        JsonElement el = gson.toJsonTree(obj);
        return tokenize(el, prefix, ordered, mode, numSuffixFrequency, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    /**
     * Tokenizes an arbitrary object with the default full-option behavior.
     */
    public static Map<String, Integer> tokenize(Object obj, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        return tokenize(obj, prefix, ordered, mode, false, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    /**
     * Tokenizes a JSON string with full control over prefixing, ordering, and filtering.
     * See {@link #tokenize(JsonElement, String, boolean, String, boolean, boolean, boolean, boolean, Set, boolean)}
     * for the tokenization behavior.
     */
    public static Map<String, Integer> tokenize(String json, String prefix, boolean ordered,
                                                String mode, boolean numSuffixFrequency, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            // treat as primitive string if not valid JSON
            root = new JsonPrimitive(json);
        }
        return tokenize(root, prefix, ordered, mode, numSuffixFrequency, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    /**
     * Tokenizes a JSON string using the default full-option behavior.
     */
    public static Map<String, Integer> tokenize(String json, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        return tokenize(json, prefix, ordered, mode, true, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    /**
     * Tokenizes a JSON element into a token frequency map.
     *
     * <p>Arrays can be tokenized with ordered and/or unordered prefixes, objects are walked
     * recursively, and atomic numeric values can optionally contribute their base prefix as a
     * frequency count when {@code numSuffixFrequency} is enabled.</p>
     *
     * @param collection the JSON element to tokenize
     * @param prefix prefix applied to generated tokens
     * @param ordered whether ordered array positions should be included (e.g., set this to {@code false} when tokenizing a list of JSON objects to ignore their ordering)
     * @param mode tokenization mode: {@code both}, {@code ordered}, {@code unordered}, or {@code char}
     * @param numSuffixFrequency when true, numeric atomic values also add the base prefix token using the numeric value as its count
     * @param filterPlayer whether to skip objects with {@code player > 0}
     * @param binning whether to bin numeric {@code x}/{@code y} values
     * @param pairXY whether to combine {@code x}/{@code y} into a single token
     * @param filterSet whitelist or blacklist token set
     * @param whitelist true for whitelist filtering, false for blacklist filtering
     * @return token frequency map
     */
    public static Map<String, Integer> tokenize(JsonElement collection, String prefix, boolean ordered,
                                                String mode, boolean numSuffixFrequency, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        if ("char".equals(mode)) {
            throw new NotImplementedException("Tokenize mode 'char' is not supported");
        }
        TokenDictionary dict = new TokenDictionary();
        dict.setFilter(filterSet, whitelist);
        TokenBag bag = new TokenBag(dict);
        tokenizeInto(collection, bag, prefix, ordered, mode, numSuffixFrequency, filterPlayer, binning, pairXY);
        return bag;
    }

    // ------------------------------------------------------------------ fast entry points

    /**
     * Adds the tokens of a game state to {@code bag}, using the heuristic defaults: ordered array positions, both
     * ordered and unordered tokens, numeric frequency tokens, and whatever filter the bag's dictionary carries.
     */
    public static void tokenizeInto(AbstractGameState gs, TokenBag bag) {
        AbstractGameStateContainer container = GameStateContainerFactory.createContainer(gs);
        tokenizeInto(container, bag, "", true, "both", true, false, false, false);
    }

    /** Adds the tokens of {@code obj} to {@code bag}; same options as the public {@code tokenize} overloads. */
    public static void tokenizeInto(Object obj, TokenBag bag, String prefix, boolean ordered,
                                    String mode, boolean numSuffixFrequency, boolean filterPlayer, boolean binning,
                                    boolean pairXY) {
        JsonElement el = gson.toJsonTree(obj);
        tokenizeInto(el, bag, prefix, ordered, mode, numSuffixFrequency, filterPlayer, binning, pairXY);
    }

    /** Adds the tokens of a JSON element to {@code bag}. */
    public static void tokenizeInto(JsonElement el, TokenBag bag, String prefix, boolean ordered, String mode,
                                    boolean numSuffixFrequency, boolean filterPlayer, boolean binning, boolean pairXY) {
        if ("char".equals(mode)) {
            throw new NotImplementedException("Tokenize mode 'char' is not supported");
        }
        if (el == null || el instanceof JsonNull) return;
        Walk walk = WALK.get();
        walk.start(bag, numSuffixFrequency, filterPlayer, binning, pairXY, mode);
        walk.len = 0;
        walk.hash = 0xcbf29ce484222325L;
        walk.hashAt[0] = walk.hash;
        walk.append(prefix);
        walk.tokenize(el, walk.len, ordered);
    }

    // ------------------------------------------------------------------ walker

    /**
     * Recursive walk over a Gson tree that writes straight into one {@link TokenBag}. The current path lives in
     * {@code buf[0..len)} with its running hash in {@code hashAt[0..len]}; recursion saves and restores the length
     * instead of building intermediate maps or strings.
     */
    private static final class Walk {
        char[] buf = new char[512];
        long[] hashAt = new long[513];
        int len;
        long hash;
        TokenBag bag;
        TokenDictionary dict;
        boolean numSuffixFrequency;
        boolean filterPlayer;
        boolean binning;
        boolean pairXY;
        boolean modeOrdered;
        boolean modeUnordered;

        void start(TokenBag bag, boolean numSuffixFrequency, boolean filterPlayer, boolean binning, boolean pairXY,
                   String mode) {
            this.bag = bag;
            this.dict = bag.dictionary();
            this.numSuffixFrequency = numSuffixFrequency;
            this.filterPlayer = filterPlayer;
            this.binning = binning;
            this.pairXY = pairXY;
            this.modeOrdered = "ordered".equals(mode);
            this.modeUnordered = "unordered".equals(mode);
        }

        /** Moves the cursor to {@code len} and restores the running hash at that position. */
        private void seek(int prefixLen) {
            len = prefixLen;
            hash = hashAt[prefixLen];
        }

        void tokenize(JsonElement el, int prefixLen, boolean ordered) {
            seek(prefixLen);
            if (el == null || el.isJsonNull()) {
                emitValue(prefixLen);
                return;
            }
            if (el.isJsonArray()) {
                tokenizeArray(el.getAsJsonArray(), prefixLen, ordered);
            } else if (el.isJsonObject()) {
                tokenizeObject(el.getAsJsonObject(), prefixLen, ordered);
            } else {
                emitPrimitiveValue(prefixLen, el.getAsJsonPrimitive());
            }
        }

        private void tokenizeArray(JsonArray arr, int prefixLen, boolean ordered) {
            int n = arr.size();
            boolean emitOrdered = ordered && !modeUnordered;
            boolean emitUnordered = !modeOrdered || !ordered;
            for (int i = 0; i < n; i++) {
                JsonElement item = arr.get(i);
                if (isAtomic(item)) {
                    if (emitOrdered) {
                        seek(prefixLen);
                        append('[');
                        appendInt(i);
                        append(']');
                        if (item == null || item.isJsonNull()) emitValue(len);
                        else emitPrimitiveValue(len, item.getAsJsonPrimitive());
                        numericFrequency(len, item);
                    }
                    if (emitUnordered) {
                        seek(prefixLen);
                        if (item == null || item.isJsonNull()) emitValue(prefixLen);
                        else emitPrimitiveValue(prefixLen, item.getAsJsonPrimitive());
                        numericFrequency(prefixLen, item);
                    }
                } else {
                    if (emitOrdered) {
                        seek(prefixLen);
                        append('[');
                        appendInt(i);
                        append(']');
                        tokenize(item, len, ordered);
                    }
                    if (emitUnordered) {
                        tokenize(item, prefixLen, !modeUnordered);
                    }
                }
            }
            seek(prefixLen);
        }

        private void tokenizeObject(JsonObject obj, int prefixLen, boolean ordered) {
            if (filterPlayer && obj.has("player")) {
                JsonElement p = obj.get("player");
                if (p.isJsonPrimitive() && p.getAsInt() > 0) {
                    seek(prefixLen);
                    return;
                }
            }
            int pairX = -99, pairY = -99;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                JsonElement value = e.getValue();
                seek(prefixLen);
                append('.');
                append(key);
                int keyLen = len;
                if (isAtomic(value)) {
                    JsonElement v = value;
                    if (binning && ("x".equals(key) || "y".equals(key))) {
                        double dv = v.getAsDouble();
                        v = new JsonPrimitive(((int) Math.floor(dv / 2)) * 2);
                    }
                    if ("x".equals(key)) {
                        pairX = v.getAsInt();
                        if (pairXY) continue;
                    }
                    if ("y".equals(key)) {
                        pairY = v.getAsInt();
                        if (pairXY) continue;
                    }
                    if (v == null || v.isJsonNull()) emitValue(keyLen);
                    else emitPrimitiveValue(keyLen, v.getAsJsonPrimitive());
                    numericFrequency(keyLen, v);
                } else {
                    tokenize(value, keyLen, ordered);
                }
            }
            if (pairXY && pairX >= 0) {
                seek(prefixLen);
                append(".x.");
                appendInt(pairX);
                append(".y.");
                appendInt(pairY);
                bag.add(dict.idOf(buf, len, hash), 1);
            }
            seek(prefixLen);
        }

        /** Adds {@code buf[0..prefixLen) + ".null"}. */
        private void emitValue(int prefixLen) {
            seek(prefixLen);
            append('.');
            append("null");
            bag.add(dict.idOf(buf, len, hash), 1);
            seek(prefixLen);
        }

        /** Adds {@code buf[0..prefixLen) + "." + value} for a string, number or boolean primitive. */
        private void emitPrimitiveValue(int prefixLen, JsonPrimitive value) {
            seek(prefixLen);
            append('.');
            if (value.isString()) {
                append(value.getAsString());
            } else if (value.isNumber()) {
                Number number = value.getAsNumber();
                if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
                    appendLong(number.longValue());
                } else {
                    append(number.toString());     // Double/Float/BigDecimal: keep their own textual form
                }
            } else if (value.isBoolean()) {
                append(value.getAsBoolean() ? "true" : "false");
            } else {
                append(value.toString());
            }
            bag.add(dict.idOf(buf, len, hash), 1);
            seek(prefixLen);
        }

        /** Adds the bare prefix as a token, counted with the numeric value, for positive integers. */
        private void numericFrequency(int prefixLen, JsonElement item) {
            if (!numSuffixFrequency || !isPositiveInteger(item)) return;
            // A frequency is a count, so it must not be negative. Reading the number as an int truncates anything past
            // the int range (2147483648 comes out as -2147483648), and a negative count poisons the bag's totals; such
            // a token is dropped instead, which leaves every value that fits an int bit-identical to the old build.
            int count = item.getAsInt();
            if (count <= 0) return;
            bag.add(dict.idOf(buf, prefixLen, hashAt[prefixLen]), count);
        }

        void append(char c) {
            if (len == buf.length) grow(len + 1);
            buf[len] = c;
            hash = (hash ^ c) * 0x100000001b3L;
            hashAt[len + 1] = hash;
            len++;
        }

        void append(String s) {
            int n = s.length();
            if (len + n > buf.length) grow(len + n);
            s.getChars(0, n, buf, len);
            long h = hash;
            for (int i = 0; i < n; i++) h = (h ^ buf[len + i]) * 0x100000001b3L;
            hash = h;
            hashAt[len + n] = h;
            len += n;
        }

        void appendInt(int v) {
            appendDigits(v);
        }

        void appendLong(long v) {
            appendDigits(v);
        }

        /** Writes a whole number digit by digit; works in long so that MIN_VALUE cannot overflow on negation. */
        private void appendDigits(long v) {
            if (len + 20 > buf.length) grow(len + 20);
            if (v == Long.MIN_VALUE) {          // the one value where -v overflows
                append("-9223372036854775808");
                return;
            }
            if (v < 0) {
                append('-');
                v = -v;
            }
            if (v < 10) {
                append((char) ('0' + v));
                return;
            }
            int start = len;
            long h = hash;
            int digits = 0;
            while (v > 0) {
                buf[len + digits] = (char) ('0' + (int) (v % 10));
                digits++;
                v /= 10;
            }
            for (int i = 0; i < digits / 2; i++) {
                char tmp = buf[start + i];
                buf[start + i] = buf[start + digits - 1 - i];
                buf[start + digits - 1 - i] = tmp;
            }
            for (int i = 0; i < digits; i++) h = (h ^ buf[start + i]) * 0x100000001b3L;
            hash = h;
            hashAt[start + digits] = h;
            len += digits;
        }

        private void grow(int min) {
            int newLen = Math.max(min, buf.length * 2);
            buf = Arrays.copyOf(buf, newLen);
            hashAt = Arrays.copyOf(hashAt, newLen + 1);
        }
    }

    private static boolean isAtomic(JsonElement el) {
        return el == null || el.isJsonNull() || el.isJsonPrimitive();
    }

    private static boolean isPositiveInteger(JsonElement el) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) return false;
        Number number = el.getAsNumber();
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return number.longValue() > 0;
        }
        double d = number.doubleValue();
        return d > 0 && d == Math.rint(d);
    }

    // ------------------------------------------------------------------ map utilities

    private static boolean isValidToken(String token, Set<String> filterSet, boolean whitelist) {
        if (token == null) return false;
        if (filterSet == null || filterSet.isEmpty()) return true;
        if (!whitelist) {   // Blacklist filtering
            // TODO MAKE THIS BLACKLIST FILTER WITH REGEX INSTEAD (as to also include substrings filtering)
            return !filterSet.contains(token);
        }
        else {  // Whitelist filtering
            return filterSet.contains(token);
        }
    }

    public static void addToken(Map<String, Integer> map, String token) {
        if (token == null) return;
        Integer v = map.get(token);
        map.put(token, v == null ? 1 : v + 1);
    }

    // With filterList
    public static void addToken(Map<String, Integer> map, String token, Set<String> filterSet, boolean whitelist) {
        if (!isValidToken(token, filterSet, whitelist)) return;
        Integer v = map.get(token);
        map.put(token, v == null ? 1 : v + 1);
    }

    public static void merge(Map<String, Integer> dest, Map<String, Integer> src) {
        if (dest instanceof TokenBag destBag && src instanceof TokenBag srcBag && destBag.dictionary() == srcBag.dictionary()) {
            destBag.addAll(srcBag);
            return;
        }
        for (Map.Entry<String, Integer> e : src.entrySet()) {
            dest.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    public static void filter(Map<String, Integer> freq, Set<String> filterSet, boolean whitelist) {
        if (filterSet == null || filterSet.isEmpty()) return;
        if (whitelist) {
            freq.keySet().retainAll(filterSet);
        } else {
            filterSet.forEach(freq.keySet()::remove);
        }
    }

    /**
     * Loads n_prototypes of type Map<String, Integer> from JSON files.
     * Files are expected to be named "prototype{i}.json" where i ranges from 0 to n_prototypes-1.
     *
     * @param path         - the directory path containing the prototype JSON files
     * @param n_prototypes - the number of prototypes to load
     * @return a list of Map<String, Integer> loaded from the JSON files
     */
    public static List<Map<String, Integer>> loadPrototypes(String path, int n_prototypes, boolean full) {
        List<Map<String, Integer>> prototypes = new ArrayList<>();
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Integer>>() {}.getType();
        String fullString = full ? "full_" : "";
        for (int i = 0; i < n_prototypes; i++) {
            String filePath = path + "/" + fullString + "prototype" + i + ".json";
            try (FileReader reader = new FileReader(filePath)) {
                Map<String, Integer> prototype = gson.fromJson(reader, mapType);
                if (prototype != null) {
                    prototypes.add(prototype);
                } else {
                    System.err.println("Warning: prototype" + i + ".json was empty or invalid");
                    prototypes.add(new HashMap<>());
                }
            } catch (IOException e) {
                System.err.println("Error loading prototype" + i + ".json: " + e.getMessage());
                prototypes.add(new HashMap<>());
            }
        }
        return prototypes;
    }

    /**
     * Loads a list of strings from a JSON file.
     * The JSON file should contain an array of strings, e.g., ["item1", "item2", "item3"]
     *
     * @param filePath - the path to the JSON file
     * @return a list of strings loaded from the JSON file, or an empty list if loading fails
     */
    public static List<String> loadStringList(String filePath) {
        Type listType = new TypeToken<List<String>>() {}.getType();
        try (FileReader reader = new FileReader(filePath)) {
            List<String> result = gson.fromJson(reader, listType);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Error loading string list from " + filePath + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
