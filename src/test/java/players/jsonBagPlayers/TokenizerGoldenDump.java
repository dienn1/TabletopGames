package players.jsonBagPlayers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Prints a digest of the token map for a fixed set of JSON documents under a fixed set of tokeniser configurations.
 * <p>
 * Run it against the original build to capture the expected values, then keep them in {@code TokenizerGoldenTest}:
 * the digests were produced by the implementation that predates the rewrite, so the test pins the rewrite to it.
 * <pre>
 * java -cp "target/test-classes;target/classes;target/dependency/*" players.jsonBagPlayers.TokenizerGoldenDump
 * </pre>
 */
public final class TokenizerGoldenDump {

    /** Documents chosen to hit every branch of the walker: nesting, arrays of objects, all number shapes, nulls. */
    static final String[] DOCUMENTS = {
            "{}",
            "[]",
            "null",
            "{\"a\":1}",
            "{\"a\":1,\"b\":\"text\",\"c\":true,\"d\":false,\"e\":null}",
            "{\"list\":[1,2,3],\"nested\":{\"deep\":{\"deeper\":\"value\"}}}",
            "{\"double\":1.5,\"exponent\":1e10,\"negative\":-0.25,\"zero\":0}",
            "{\"ints\":[-2147483648,2147483647,0,-1],\"longs\":[9223372036854775807,-9223372036854775808]}",
            "{\"emptyObject\":{},\"emptyArray\":[],\"emptyString\":\"\"}",
            "{\"objects\":[{\"id\":1},{\"id\":2},{\"id\":1}]}",
            "{\"unicode\":\"caf\u00e9 \u4e2d\u6587\",\"escaped\":\"line\\nbreak\\ttab\"}",
            "{\"deep\":[[[[[[[[[[1]]]]]]]]]]}",
            "{\"same\":\"same\",\"same2\":\"same\",\"AAA\":\"AAA\"}",
            "{\"score\":0.5,\"count\":2,\"ratio\":0.3333333333333333}",
    };

    /** The configurations that reach different code paths in the tokeniser. */
    static final String[][] CONFIGS = {
            {"ordered-both", "true", "both", "true"},
            {"unordered-both", "false", "both", "true"},
            {"ordered-only", "true", "ordered", "true"},
            {"unordered-only", "false", "unordered", "true"},
            {"ordered-noNumFreq", "true", "both", "false"},
            {"unordered-noNumFreq", "false", "both", "false"},
    };

    public static void main(String[] args) throws Exception {
        for (int d = 0; d < DOCUMENTS.length; d++) {
            for (String[] cfg : CONFIGS) {
                Map<String, Integer> tokens = Tokenizer.tokenize(DOCUMENTS[d], "", Boolean.parseBoolean(cfg[1]),
                        cfg[2], Boolean.parseBoolean(cfg[3]), false, false, false, null, false);
                System.out.println("            \"" + digest(tokens) + "\",   // " + d + " " + cfg[0]);
            }
        }
    }

    static String digest(Map<String, Integer> tokens) {
        List<String> keys = new ArrayList<>(tokens.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) sb.append(key).append('=').append(tokens.get(key)).append('\n');
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
            return hex + "/" + keys.size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
