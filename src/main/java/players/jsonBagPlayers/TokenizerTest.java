package players.jsonBagPlayers;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class TokenizerTest {
    public static void main(String[] args) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get("src/main/java/evaluation/metrics/test.txt")));;
        Map<String, Integer> jsonBag;

        jsonBag = Tokenizer.tokenize(jsonString);
        System.out.println(jsonBag.size());
        jsonBag.forEach((key, value) -> System.out.println(key + " : " + value));
    }
}
