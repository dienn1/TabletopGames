package players.jsonBagPlayers;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the tokeniser to the implementation that predates the performance rewrite.
 * <p>
 * The expected values were produced by that build: run {@code TokenizerGoldenDump} against it to regenerate them.
 * They cover nested objects and arrays, every number shape (including the extremes of {@code int} and {@code long}),
 * booleans, nulls, empty containers, unicode, escapes and deep nesting, each under six tokeniser configurations.
 * A failure here means a token changed, which is a behaviour change however fast it is.
 * <p>
 * Four of the 84 digests were regenerated after the fix described in {@code numericFrequency}: the original build
 * stored a negative count for an integer past the int range (9223372036854775807 truncates to -1), and this build
 * drops such a token instead, because a negative count poisons the bag's totals. Document 7 is the only one affected.
 */
public class TokenizerGoldenTest {

    private static final String[] DOCUMENTS = {
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


    private static final String[][] CONFIGS = {
            {"ordered-both", "true", "both", "true"},
            {"unordered-both", "false", "both", "true"},
            {"ordered-only", "true", "ordered", "true"},
            {"unordered-only", "false", "unordered", "true"},
            {"ordered-noNumFreq", "true", "both", "false"},
            {"unordered-noNumFreq", "false", "both", "false"},
    };


    /** One digest per document per configuration, in the order the loops below visit them. */
    private static final String[] EXPECTED = {
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "e3b0c44298fc1c14/0",
            "4772afc13f5124d8/2",
            "4772afc13f5124d8/2",
            "4772afc13f5124d8/2",
            "4772afc13f5124d8/2",
            "f16fa82ba6f80acc/1",
            "f16fa82ba6f80acc/1",
            "566394bef6191515/6",
            "566394bef6191515/6",
            "566394bef6191515/6",
            "566394bef6191515/6",
            "68808c4725420128/5",
            "68808c4725420128/5",
            "15d1d76efc7b6dea/11",
            "f17dcd9a0e3a010f/5",
            "6cf94b3045935e8c/7",
            "f17dcd9a0e3a010f/5",
            "a8b81dc35b32bbf1/7",
            "004d5ada6fe4af61/4",
            "7081afd82e6ecff1/5",
            "7081afd82e6ecff1/5",
            "7081afd82e6ecff1/5",
            "7081afd82e6ecff1/5",
            "f263a51f0a94abf8/4",
            "f263a51f0a94abf8/4",
            "dc3171a6f78d7ab5/14",
            "c61c716b842fdaf8/7",
            "07ac81d81158a86f/7",
            "c61c716b842fdaf8/7",
            "331d5e2f65a83017/12",
            "069963358eda0b16/6",
            "7355196d91466aba/1",
            "7355196d91466aba/1",
            "7355196d91466aba/1",
            "7355196d91466aba/1",
            "7355196d91466aba/1",
            "7355196d91466aba/1",
            "49226b758e392a47/9",
            "6c3844f5001a3bb9/3",
            "68e4afe99fdbb3d3/6",
            "6c3844f5001a3bb9/3",
            "743530b57bab66d6/5",
            "6e29043458c2d043/2",
            "f65b3863946ab2b3/2",
            "f65b3863946ab2b3/2",
            "f65b3863946ab2b3/2",
            "f65b3863946ab2b3/2",
            "f65b3863946ab2b3/2",
            "f65b3863946ab2b3/2",
            "ecb55ac7de35f72a/22",
            "33f605b85bbdf16e/20",
            "f6e8b84dcfeff2f7/2",
            "aae2785016419eb2/2",
            "69bb371dba968a7f/11",
            "13a9a1ec792fe45f/10",
            "0561b6e38315c8e3/3",
            "0561b6e38315c8e3/3",
            "0561b6e38315c8e3/3",
            "0561b6e38315c8e3/3",
            "0561b6e38315c8e3/3",
            "0561b6e38315c8e3/3",
            "35fc141025d92df3/4",
            "35fc141025d92df3/4",
            "35fc141025d92df3/4",
            "35fc141025d92df3/4",
            "2e0797217659d4a7/3",
            "2e0797217659d4a7/3",
    };

    @Test
    public void tokenMapsMatchTheOriginalImplementation() {
        int i = 0;
        for (String document : DOCUMENTS) {
            for (String[] config : CONFIGS) {
                Map<String, Integer> tokens = Tokenizer.tokenize(document, "", Boolean.parseBoolean(config[1]),
                        config[2], Boolean.parseBoolean(config[3]), false, false, false, null, false);
                assertEquals(document + " under " + config[0], EXPECTED[i], TokenizerGoldenDump.digest(tokens));
                i++;
            }
        }
        assertEquals(EXPECTED.length, i);
    }

    @Test
    public void everyExpectedValueIsUsed() {
        for (String expected : EXPECTED) assertTrue(expected.matches("[0-9a-f]{16}/\\d+"));
    }
}
