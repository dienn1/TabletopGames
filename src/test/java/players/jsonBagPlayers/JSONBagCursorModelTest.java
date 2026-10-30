package players.jsonBagPlayers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Validates the traversal rule a streaming rewrite of the tokeniser has to reproduce, without touching the hot path.
 * <p>
 * {@link Tokenizer.Walk} emits every array level twice - once keeping the index, once dropping it - and both copies
 * recurse, so a leaf is emitted once per {keep, drop} assignment over the array levels above it. Different
 * assignments can produce the same path, which is why the bag carries multiplicities rather than a set. This class
 * implements that rule directly (paths as strings, so it is obviously a model and not an optimisation) and asserts it
 * reproduces {@link Tokenizer#tokenize(String, String, boolean, String, boolean, boolean, boolean, boolean, Set,
 * boolean)} exactly on nested documents. Round 5's writer is a fast implementation of this model.
 */
public class JSONBagCursorModelTest {

    /** The model: a multiset of path cursors, doubled at every array. */
    private static void walk(JsonElement el, List<String> paths, Map<String, Integer> out) {
        if (el == null || el.isJsonNull()) {
            for (String p : paths) add(out, p + ".null", 1);
            return;
        }
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                List<String> child = new ArrayList<>(paths.size());
                for (String p : paths) child.add(p + "." + e.getKey());
                walk(e.getValue(), child, out);
            }
            return;
        }
        if (el.isJsonArray()) {
            var arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                // keep the index, and drop it: both copies recurse, which is what doubles the paths per level
                List<String> child = new ArrayList<>(paths.size() * 2);
                for (String p : paths) {
                    child.add(p + "[" + i + "]");
                    child.add(p);
                }
                walk(arr.get(i), child, out);
            }
            return;
        }
        var prim = el.getAsJsonPrimitive();
        for (String p : paths) {
            String token = p + "." + text(prim);
            add(out, token, 1);
            if (prim.isNumber()) {
                Number n = prim.getAsNumber();
                if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
                    long v = n.longValue();
                    if (v > 0) add(out, p, n.intValue());
                } else {
                    double d = n.doubleValue();
                    if (d > 0 && d == Math.rint(d)) add(out, p, n.intValue());
                }
            }
        }
    }

    private static String text(com.google.gson.JsonPrimitive p) {
        if (p.isString()) return p.getAsString();
        if (p.isNumber()) {
            Number n = p.getAsNumber();
            if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) return Long.toString(n.longValue());
            return n.toString();
        }
        if (p.isBoolean()) return p.getAsBoolean() ? "true" : "false";
        return p.toString();
    }

    private static void add(Map<String, Integer> m, String k, int c) {
        m.merge(k, c, Integer::sum);
    }

    private static Map<String, Integer> model(String json) {
        Map<String, Integer> out = new HashMap<>();
        walk(JsonParser.parseString(json), List.of(""), out);
        return new TreeMap<>(out);
    }

    private static Map<String, Integer> walker(String json) {
        return new TreeMap<>(Tokenizer.tokenize(json, "", true, "both", true, false, false, false, null, false));
    }

    @Test
    public void modelReproducesTheWalkerOnNestedDocuments() {
        List<String> docs = List.of(
                "{\"a\":1}",
                "{\"a\":[[1,2]]}",
                "{\"a\":[[[7]]]}",
                "{\"a\":[{\"b\":[[1,2]]},3]}",
                "{\"a\":[[1,2],[3,[4]]]}",
                "{\"a\":[[true,null],{\"b\":{\"c\":[[-3]]}}]}",
                "[[1,2],[3,4]]",
                "{\"x\":[[0,2.5]],\"y\":[[[]]]}");
        for (String doc : docs) {
            assertEquals("model diverges on " + doc, walker(doc), model(doc));
        }
        // the case that proves multiplicities are load-bearing rather than incidental
        assertEquals(Integer.valueOf(2), model("{\"a\":[[1,2]]}").get(".a[0].1"));
        assertTrue("a set-based check would not see the multiplicity",
                model("{\"a\":[[1,2]]}").containsKey(".a[0][0].1"));
    }
}
