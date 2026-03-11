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

public class Tokenizer {
    private static final Gson gson = new Gson();

    public static Map<String, Integer> tokenize(String json) {
        return tokenize(json, "", false, "both", false, false, false, null, false);
    }

    public static Map<String, Integer> tokenize(String json, Set<String> filterSet, boolean whitelist) {
        return tokenize(json, "", false, "both", false, false, false, filterSet, whitelist);
    }

    /**
     * Convenience overload: accept any object, serialize with Gson, then tokenize.
     */
    public static Map<String, Integer> tokenize(Object obj) {
        return tokenize(obj, "", true, "both", false, false, false, null, false);
    }

    public static Map<String, Integer> tokenize(Object obj, Set<String> filterSet, boolean whitelist) {
        return tokenize(obj, "", true, "both", false, false, false, filterSet, whitelist);
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
     * Convenience overload: accept any object with full options, serialize with Gson, then tokenize.
     */
    public static Map<String, Integer> tokenize(Object obj, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        JsonElement el = gson.toJsonTree(obj);
        return tokenize(el, prefix, ordered, mode, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    public static Map<String, Integer> tokenize(String json, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            // treat as primitive string if not valid JSON
            root = new JsonPrimitive(json);
        }
        return tokenize(root, prefix, ordered, mode, filterPlayer, binning, pairXY, filterSet, whitelist);
    }

    public static Map<String, Integer> tokenize(JsonElement collection, String prefix, boolean ordered,
                                                String mode, boolean filterPlayer, boolean binning, boolean pairXY,
                                                Set<String> filterSet, boolean whitelist) {
        Map<String, Integer> freq = new HashMap<>();
        if ("char".equals(mode)) {
//            addToken(freq, collection.toString());
//            return freq;
            throw new NotImplementedException("Tokenize mode 'char' is not supported");
        }

        if (collection == null || collection instanceof JsonNull) return freq;

        if (collection.isJsonArray()) {
            JsonArray arr = collection.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                String orderedPrefix = prefix + "[" + i + "]";
                JsonElement item = arr.get(i);
                if (isAtomic(item)) {
                    String s = primitiveToString(item);
                    if (ordered && !"unordered".equals(mode)) {
                        addToken(freq, orderedPrefix + "." + s, filterSet, whitelist);
                    }
                    if (!"ordered".equals(mode) || !ordered) {
                        addToken(freq, prefix + "." + s, filterSet, whitelist);
                    }
                } else {
                    if (ordered && !"unordered".equals(mode)) {
                        merge(freq, tokenize(item, orderedPrefix, ordered, mode, filterPlayer, binning, pairXY, filterSet, whitelist));
                    }
                    if (!"ordered".equals(mode) || !ordered) {
                        // Set subsequent call ordered = True if mode is not unordered
                        merge(freq, tokenize(item, prefix, !"unordered".equals(mode), mode, filterPlayer, binning, pairXY, filterSet, whitelist));
                    }
                }
            }
        } else if (collection.isJsonObject()) {
            JsonObject obj = collection.getAsJsonObject();
            // filter player (if present and > 0)
            if (filterPlayer && obj.has("player") && obj.get("player").isJsonPrimitive()) {
                int p = obj.get("player").getAsInt();
                if (p > 0) return freq;
            }

            int pairX = -99, pairY = -99;
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                String keyPrefix = prefix + "." + key;
                JsonElement value = e.getValue();
                if (isAtomic(value)) {
                    // binning numerical value
                    if (binning && ("x".equals(key) || "y".equals(key))) {
                        int n = 2;
                        double dv = value.getAsDouble();
                        int b = ((int) Math.floor(dv / n)) * n;
                        value = new JsonPrimitive(b);
                    }
                    if ("x".equals(key)) {
                        pairX = value.getAsInt();
                        if (pairXY) continue;
                    }
                    if ("y".equals(key)) {
                        pairY = value.getAsInt();
                        if (pairXY) continue;
                    }
                    addToken(freq, keyPrefix + "." + primitiveToString(value), filterSet, whitelist);
                } else {
                    merge(freq, tokenize(value, keyPrefix, ordered, mode, filterPlayer, binning, pairXY, filterSet, whitelist));
                }
            }
            if (pairXY && pairX >= 0) {
                addToken(freq, prefix + ".x." + pairX + ".y." + pairY, filterSet, whitelist);
            }
        } else { // primitive
            addToken(freq, prefix + "." + primitiveToString(collection), filterSet, whitelist);
        }

        return freq;
    }

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

    // With filterList
    public static void addToken(Map<String, Integer> map, String token, Set<String> filterSet, boolean whitelist) {
        if (!isValidToken(token, filterSet, whitelist)) return;
        Integer v = map.get(token);
        map.put(token, v == null ? 1 : v + 1);
    }

    public static void merge(Map<String, Integer> dest, Map<String, Integer> src) {
        for (Map.Entry<String, Integer> e : src.entrySet()) {
//            dest.put(e.getKey(), dest.getOrDefault(e.getKey(), 0) + e.getValue());
            dest.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    public static void filter(Map<String, Integer> freq, Set<String> filterList, boolean whitelist) {
        if (whitelist) {
            freq.keySet().retainAll(filterList);
        } else {
            filterList.forEach(freq.keySet()::remove);
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
    public static List<Map<String, Integer>> loadPrototypes(String path, int n_prototypes) {
        List<Map<String, Integer>> prototypes = new ArrayList<>();
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Integer>>() {}.getType();

        for (int i = 0; i < n_prototypes; i++) {
            String filePath = path + "/prototype" + i + ".json";
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
