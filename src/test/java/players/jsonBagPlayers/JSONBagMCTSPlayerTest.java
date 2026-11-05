package players.jsonBagPlayers;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.CoreConstants;
import core.Game;
import games.GameType;
import org.json.simple.JSONObject;
import org.junit.Test;
import players.PlayerConstants;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.simple.RandomPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * JSON-Bag MCTS is stock MCTSPlayer + MCTSParams + a JSONBagHeuristic: the player class is not specialised.
 * These tests pin that, across the MCTS variants, and against the prototypes shipped on disk.
 */
public class JSONBagMCTSPlayerTest {

    private static List<Map<String, Integer>> prototypesFor(GameType gameType, int nPlayers, long seed) {
        AbstractGameState gs = gameType.createGameInstance(nPlayers, seed).getGameState();
        Map<String, Integer> prototype = Tokenizer.tokenize(gs);
        List<Map<String, Integer>> prototypes = new ArrayList<>();
        for (int i = 0; i < nPlayers; i++)
            prototypes.add(new HashMap<>(prototype));
        return prototypes;
    }

    private static MCTSParams params(String information, String opponentTreePolicy, int determinisations, int budget) {
        MCTSParams params = new MCTSParams();
        params.setParameterValue("randomSeed", 1234);
        params.setParameterValue("rolloutLength", 0);
        params.setParameterValue("information", information);
        params.setParameterValue("opponentTreePolicy", opponentTreePolicy);
        params.setParameterValue("numDeterminizations", determinisations);
        params.setParameterValue("budgetType", PlayerConstants.BUDGET_ITERATIONS);
        params.setParameterValue("budget", budget);
        return params;
    }

    private static Game play(GameType gameType, MCTSPlayer player, long seed) {
        List<AbstractPlayer> players = new ArrayList<>();
        players.add(player);
        players.add(new RandomPlayer(new Random(7)));
        return Game.runOne(gameType, null, players, seed, false, null, null, 0);
    }

    /** Counts evaluations, to prove the search consults the value function rather than merely holding it. */
    static class CountingBagHeuristic extends JSONBagHeuristic {
        int evaluations;

        CountingBagHeuristic(List<Map<String, Integer>> prototypes) {
            super(prototypes);
        }

        @Override
        public double[] evaluateAll(AbstractGameState gs) {
            evaluations++;
            return super.evaluateAll(gs);
        }
    }

    /** The number of distinct tokens in one game's opening state: the floor a trajectory bag starts from. */
    private static int openingTokens(GameType gameType, long seed) {
        return Tokenizer.tokenize(gameType.createGameInstance(2, seed).getGameState()).size();
    }

    @Test
    public void stockMctsWithAJsonBagHeuristicPlaysAFullGame() {
        CountingBagHeuristic heuristic = new CountingBagHeuristic(prototypesFor(GameType.Connect4, 2, 11L));
        MCTSParams params = params("Information_Set", "OneTree", 1, 40);
        params.setParameterValue("heuristic", heuristic);

        Game game = play(GameType.Connect4, new MCTSPlayer(params), 1234L);

        assertNotEquals(CoreConstants.GameResult.GAME_ONGOING, game.getGameState().getGameStatus());
        assertTrue("the search must consult the value function, evaluations=" + heuristic.evaluations,
                heuristic.evaluations > 0);
        assertTrue("the trajectory bag must grow past the opening state, was " + heuristic.currentJSONBag.size(),
                heuristic.currentJSONBag.size() > openingTokens(GameType.Connect4, 1234L));
    }

    @Test
    public void everyMctsVariantRuns() {
        Object[][] variants = {
                {"Information_Set", "OneTree", 1},
                {"Open_Loop", "OneTree", 1},
                {"Information_Set", "SelfOnly", 1},
                {"Information_Set", "MultiTree", 1},
                {"Information_Set", "OneTree", 10},
        };
        for (Object[] variant : variants) {
            MCTSParams params = params((String) variant[0], (String) variant[1], (Integer) variant[2], 30);
            params.setParameterValue("heuristic", new JSONBagHeuristic(prototypesFor(GameType.Connect4, 2, 11L)));

            Game game = play(GameType.Connect4, new MCTSPlayer(params), 99L);

            assertNotEquals("variant " + Arrays.toString(variant) + " did not finish",
                    CoreConstants.GameResult.GAME_ONGOING, game.getGameState().getGameStatus());
        }
    }

    @Test
    public void playsAFullGameWithTheShippedPrototypes() {
        Path prototypes = Path.of("Experiments", "valueFuncTest2", "Connect4", "prototype100_0_allSegFS_seg4_cum");
        Path filter = prototypes.resolve("features-top100.json");
        assumeTrue("shipped prototypes are not present", Files.isRegularFile(prototypes.resolve("full_prototype1.json")));
        assumeTrue("feature filter is not present", Files.isRegularFile(filter));

        JSONObject descriptor = new JSONObject();
        descriptor.put("class", "players.jsonBagPlayers.JSONBagHeuristic");
        descriptor.put("prototypeDir", prototypes.toAbsolutePath().toString());
        descriptor.put("nPrototypes", 2);
        descriptor.put("fullPrototypes", true);
        descriptor.put("filterFile", filter.toAbsolutePath().toString());

        JSONBagHeuristic heuristic = new JSONBagHeuristic(descriptor);
        MCTSParams params = params("Information_Set", "OneTree", 1, 50);
        params.setParameterValue("heuristic", heuristic);

        Game game = play(GameType.Connect4, new MCTSPlayer(params), 2026L);

        assertNotEquals(CoreConstants.GameResult.GAME_ONGOING, game.getGameState().getGameStatus());
        // the bag is filtered by the descriptor's whitelist, so compare against a filtered opening state
        Set<String> whitelist = new HashSet<>(Tokenizer.loadStringList(filter.toAbsolutePath().toString()));
        int opening = Tokenizer.tokenize(GameType.Connect4.createGameInstance(2, 2026L).getGameState(), whitelist, true).size();
        assertTrue("the trajectory bag must grow past the filtered opening state (" + opening + "), was "
                        + heuristic.currentJSONBag.size(),
                heuristic.currentJSONBag.size() > opening);
    }
}
