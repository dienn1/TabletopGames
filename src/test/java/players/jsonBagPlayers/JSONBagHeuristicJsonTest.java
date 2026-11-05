package players.jsonBagPlayers;

import com.google.gson.Gson;
import core.AbstractPlayer;
import core.interfaces.IStateHeuristic;
import games.GameType;
import org.json.simple.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import players.PlayerFactory;
import players.mcts.MCTSPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * A JSONBagHeuristic can be built from the same JSON object a player descriptor nests under "heuristic",
 * so JSON-Bag MCTS needs no player or params class of its own.
 */
public class JSONBagHeuristicJsonTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File writePrototypes(int n, boolean full) throws IOException {
        File dir = folder.newFolder("prototypes");
        for (int i = 0; i < n; i++) {
            Map<String, Integer> prototype = new LinkedHashMap<>();
            prototype.put(".gameType.DotsAndBoxes", 3);
            prototype.put(".player" + i, 5);
            write(new File(dir, (full ? "full_" : "") + "prototype" + i + ".json"), new Gson().toJson(prototype));
        }
        write(new File(dir, "features-top2.json"), new Gson().toJson(new String[]{".gameType.DotsAndBoxes", ".player0"}));
        return dir;
    }

    private static void write(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static JSONObject descriptor(File dir, int n, boolean full, String filterFile) {
        JSONObject json = new JSONObject();
        json.put("class", "players.jsonBagPlayers.JSONBagHeuristic");
        json.put("prototypeDir", dir.getAbsolutePath());
        json.put("nPrototypes", n);
        json.put("fullPrototypes", full);
        if (filterFile != null)
            json.put("filterFile", filterFile);
        return json;
    }

    @Test
    public void buildsFromADescriptor() throws IOException {
        File dir = writePrototypes(2, true);

        JSONBagHeuristic heuristic = new JSONBagHeuristic(descriptor(dir, 2, true, null));

        assertEquals(2, heuristic.compiledPrototypes().count);
    }

    @Test
    public void theFilterFileKeepsOnlyWhitelistedTokens() throws IOException {
        File dir = writePrototypes(2, true);
        Set<String> whitelist = Set.of(".gameType.DotsAndBoxes", ".player0");

        JSONBagHeuristic heuristic = new JSONBagHeuristic(descriptor(dir, 2, true,
                new File(dir, "features-top2.json").getAbsolutePath()));
        heuristic.reset(GameType.DotsAndBoxes.createGameInstance(2, 5L).getGameState());

        for (String token : heuristic.currentJSONBag.keySet())
            assertTrue("unexpected token " + token, whitelist.contains(token));
        // the prototypes must be filtered before they are compiled: prototype0 keeps both whitelisted tokens,
        // prototype1 only .gameType.DotsAndBoxes, so an unfiltered compile would report {3, 3} here
        assertArrayEquals(new int[]{2, 1}, heuristic.compiledPrototypes().mapSize);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMissingFilterFileFailsLoudly() throws IOException {
        File dir = writePrototypes(2, true);

        new JSONBagHeuristic(descriptor(dir, 2, true, new File(dir, "no-such-filter.json").getAbsolutePath()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMissingPrototypeFailsLoudly() throws IOException {
        File empty = folder.newFolder("empty");

        new JSONBagHeuristic(descriptor(empty, 2, true, null));
    }

    @Test
    public void playerFactoryBuildsStockMctsFromTheDescriptor() throws IOException {
        File dir = writePrototypes(2, true);
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("class", "players.mcts.MCTSParams");
        player.put("rolloutLength", 0);
        player.put("heuristic", descriptor(dir, 2, true, null));
        File json = folder.newFile("jsonbag-mcts.json");
        write(json, new Gson().toJson(player));

        AbstractPlayer created = PlayerFactory.createPlayer(json.getAbsolutePath());

        assertTrue("expected an MCTSPlayer, got " + created.getClass(), created instanceof MCTSPlayer);
        IStateHeuristic heuristic = ((MCTSPlayer) created).getStateHeuristic();
        assertTrue("expected a JSONBagHeuristic, got " + heuristic.getClass(), heuristic instanceof JSONBagHeuristic);
        assertEquals(2, ((JSONBagHeuristic) heuristic).compiledPrototypes().count);
    }
}
