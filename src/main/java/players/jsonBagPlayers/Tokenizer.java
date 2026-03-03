package players.jsonBagPlayers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Tokenizer {
    private static final Gson gson = new Gson();

    public static Map<String, Integer> tokenize(String json) {
        return tokenize(json, "", false, "both", false, false, false);
    }

    /**
     * Convenience overload: accept any object, serialize with Gson, then tokenize.
     */
    public static Map<String, Integer> tokenize(Object obj) {
        return tokenize(obj, "", true, "both", false, false, false);
    }

    /**
     * Convenience overload: accept any object with full options, serialize with Gson, then tokenize.
     */
    public static Map<String, Integer> tokenize(Object obj, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning,
                                                boolean pairXY) {
        JsonElement el = gson.toJsonTree(obj);
        return tokenize(el, prefix, ordered, mode, filterPlayer, binning, pairXY);
    }

    public static Map<String, Integer> tokenize(String json, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning,
                                                boolean pairXY) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            // treat as primitive string if not valid JSON
            root = new JsonPrimitive(json);
        }
        return tokenize(root, prefix, ordered, mode, filterPlayer, binning, pairXY);
    }

    public static Map<String, Integer> tokenize(JsonElement collection, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning,
                                                boolean pairXY) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        if ("char".equals(mode)) {
            addToken(freq, collection.toString());
            return freq;
        }

        if (collection == null || collection instanceof JsonNull) return freq;

        if (collection.isJsonArray()) {
            JsonArray arr = collection.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                String orderedPrefix = prefix + "[" + i + "]";
                JsonElement item = loadJsonIfString(arr.get(i));
                if (isAtomic(item)) {
                    String s = primitiveToString(item);
                    if (ordered && !"unordered".equals(mode)) {
                        addToken(freq, orderedPrefix + "." + s);
                    }
                    if (!"ordered".equals(mode) || !ordered) {
                        addToken(freq, prefix + "." + s);
                    }
                } else {
                    if (ordered && !"unordered".equals(mode)) {
                        merge(freq, tokenize(item, orderedPrefix, ordered, mode, filterPlayer, binning, pairXY));
                    }
                    if (!"ordered".equals(mode) || !ordered) {
                        // Set subsequent call ordered = True if mode is not unordered
                        merge(freq, tokenize(item, prefix, !"unordered".equals(mode), mode, filterPlayer, binning, pairXY));
                    }
                }
            }
        } else if (collection.isJsonObject()) {
            JsonObject obj = collection.getAsJsonObject();
            // filter player (if present and > 0)
            if (filterPlayer && obj.has("player") && obj.get("player").isJsonPrimitive()) {
                try {
                    int p = obj.get("player").getAsInt();
                    if (p > 0) return freq;
                } catch (NumberFormatException ignored) {
                }
            }

            int pairX = -99, pairY = -99;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                String keyPrefix = prefix + "." + key;
                JsonElement value = loadJsonIfString(e.getValue());
                if (isAtomic(value)) {
                    // binning numerical value
                    if (binning && ("x".equals(key) || "y".equals(key))) {
                        int n = 2;
                        try {
                            double dv = value.getAsDouble();
                            int b = ((int) Math.floor(dv / n)) * n;
                            value = new JsonPrimitive(b);
                        } catch (Exception ignored) {
                        }
                    }
                    if ("x".equals(key)) {
                        try { pairX = value.getAsInt(); } catch (Exception ignored) {}
                        if (pairXY) continue;
                    }
                    if ("y".equals(key)) {
                        try { pairY = value.getAsInt(); } catch (Exception ignored) {}
                        if (pairXY) continue;
                    }
                    addToken(freq, keyPrefix + "." + primitiveToString(value));
                } else {
                    merge(freq, tokenize(value, keyPrefix, ordered, mode, filterPlayer, binning, pairXY));
                }
            }
            if (pairXY && pairX >= 0) {
                addToken(freq, prefix + ".x." + pairX + ".y." + pairY);
            }
        } else { // primitive
            addToken(freq, prefix + "." + primitiveToString(collection));
        }

        return freq;
    }

    private static JsonElement loadJsonIfString(JsonElement el) {
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            try {
                JsonElement parsed = JsonParser.parseString(s);
                return parsed;
            } catch (JsonSyntaxException ex) {
                return el;
            }
        }
        return el;
    }

    private static boolean isAtomic(JsonElement el) {
        return el == null || el.isJsonNull() || el.isJsonPrimitive();
    }

    private static String primitiveToString(JsonElement el) {
        if (el == null || el instanceof JsonNull) return "null";
        if (!el.isJsonPrimitive()) return el.toString();
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isString()) return p.getAsString();
        if (p.isNumber()) return p.getAsNumber().toString();
        if (p.isBoolean()) return Boolean.toString(p.getAsBoolean());
        return p.toString();
    }

    public static void addToken(Map<String, Integer> map, String token) {
        if (token == null) return;
        Integer v = map.get(token);
        map.put(token, v == null ? 1 : v + 1);
    }

    public static void merge(Map<String, Integer> dest, Map<String, Integer> src) {
        for (Map.Entry<String, Integer> e : src.entrySet()) {
            dest.put(e.getKey(), dest.getOrDefault(e.getKey(), 0) + e.getValue());
        }
    }

    public static void filter(Map<String, Integer> freq, List<String> filterList, boolean whitelist) {
        if (whitelist) {
            freq.keySet().retainAll(filterList);
        } else {
            filterList.forEach(freq.keySet()::remove);
        }
    }
}
