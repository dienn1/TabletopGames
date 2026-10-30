package players.jsonBagPlayers;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.GameType;
import players.simple.OSLAPlayer;
import players.simple.RandomPlayer;

import java.util.*;

/**
 * Development harness for measuring JSON-Bag performance. Not a JUnit test: it has a main method and
 * is never picked up by surefire. Run it from the project root with the test classpath:
 *
 * <pre>
 * java -cp "target/classes;target/test-classes;target/dependency/*" players.jsonBagPlayers.JSONBagBench timing
 * </pre>
 *
 * Modes:
 * <ul>
 *   <li>{@code timing} - microseconds per tokenise / jsd / evaluateState call, per game.</li>
 *   <li>{@code game} - wall-clock milliseconds for a full game with JSON-Bag OSLA and with vanilla OSLA.</li>
 * </ul>
 * Prototypes are read from the experiment folders under {@code Experiments/valueFuncTest2}; they are not part of
 * the repository, so the harness is only usable where those folders exist.
 */
public final class JSONBagBench {

    static final String ROOT = "Experiments/valueFuncTest2";

    static final class Config {
        final GameType game;
        final int nPlayers;
        final String folder;

        Config(GameType game, int nPlayers, String folder) {
            this.game = game;
            this.nPlayers = nPlayers;
            this.folder = folder;
        }
    }

    static final List<Config> CONFIGS = List.of(
            new Config(GameType.CantStop, 4, "CantStop/prototype100_0_allSegFS_seg4_cum"),
            new Config(GameType.Connect4, 2, "Connect4/prototype100_0_allSegFS_seg4_cum"),
            new Config(GameType.DotsAndBoxes, 2, "DotsAndBoxes/prototype100_0_allSegFS_seg4_cum"),
            new Config(GameType.Dominion, 4, "Dominion/prototype100_0_allSegFS_seg4_cum"),
            new Config(GameType.SeaSaltPaper, 4, "SeaSaltPaper/prototype100_0_allSegFS_seg4_cum"),
            new Config(GameType.Wonders7, 4, "Wonders7/prototype100_0_allSegFS_seg4_cum"));

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "timing";
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        switch (mode) {
            case "timing" -> timing();
            case "game" -> gameTiming(games);
            case "split" -> split();
            case "build" -> heuristicBuild();
            case "player" -> playerSplit();
            case "evalsplit" -> evalSplit();
            case "bagstats" -> bagStats();
            case "levers" -> levers(500);
            case "warm" -> warm();
            case "legacy" -> legacyTiming();
            case "legacy-filtered" -> legacyTimingFiltered(games > 0 ? games : 500);
            case "game-filtered" -> gameTimingFiltered(games > 0 ? games : 500, 3);
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // ------------------------------------------------------------------ corpus

    /** Walks random trajectories and collects states at decision points. */
    static List<AbstractGameState> corpus(Config cfg, int maxStates, long... seeds) {
        List<AbstractGameState> states = new ArrayList<>();
        for (long seed : seeds) {
            GameType gt = cfg.game;
            var game = gt.createGameInstance(cfg.nPlayers, seed);
            List<core.AbstractPlayer> players = new ArrayList<>();
            Random rnd = new Random(seed);
            for (int i = 0; i < cfg.nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
            game.reset(players);
            AbstractGameState gs = game.getGameState();
            int guard = 0;
            while (gs.isNotTerminal() && states.size() < maxStates && guard++ < 5000) {
                List<AbstractAction> actions = JSONBagEquivalence.deterministic(
                        game.getForwardModel().computeAvailableActions(gs));
                if (actions.isEmpty()) break;
                states.add(gs.copy());
                game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())).copy());
            }
            if (states.size() >= maxStates) break;
        }
        return states;
    }

    // ------------------------------------------------------------------ timing

    static void timing() {
        System.out.printf("%-14s %8s %12s %12s %12s %12s%n",
                "game", "states", "tokenize_us", "into_us", "eval_us", "bag_tokens");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;

            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            Map<String, Integer> historyBag = heuristic.currentJSONBag;
            TokenBag scratch = new TokenBag(((TokenBag) historyBag).dictionary());

            Set<String> empty = Collections.emptySet();
            double tokUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) Tokenizer.tokenize(gs, empty, true);
            }, states.size());

            double intoUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) {
                    scratch.clear();
                    Tokenizer.tokenizeInto(gs, scratch);
                }
            }, states.size());

            double evalUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());

            System.out.printf("%-14s %8d %12.2f %12.2f %12.2f %12d%n",
                    cfg.game.name(), states.size(), tokUs, intoUs, evalUs, historyBag.size());
        }
    }

    /**
     * Tokenise and evaluate timings that only use the API both the old and the new implementation expose, so the
     * same harness can measure a baseline build and a candidate build.
     */
    static void legacyTiming() {
        System.out.printf("%-14s %8s %12s %12s %12s%n", "game", "states", "tokenize_us", "eval_us", "bag_tokens");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            Set<String> empty = Collections.emptySet();
            double tokUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) Tokenizer.tokenize(gs, empty, true);
            }, states.size());
            double evalUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            System.out.printf("%-14s %8d %12.2f %12.2f %12d%n",
                    cfg.game.name(), states.size(), tokUs, evalUs, heuristic.currentJSONBag.size());
        }
    }

    /**
     * Same as {@link #legacyTiming()}, but with the feature-selected configuration the paper's experiments use: the
     * top-feature whitelist from {@code features-top500.json} is applied to the prototypes and to every bag.
     */
    static void legacyTimingFiltered(int topK) {
        System.out.printf("%-14s %8s %12s %12s %12s%n", "game", "states", "tokenize_us", "eval_us", "bag_tokens");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, topK));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            double tokUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) Tokenizer.tokenize(gs, topFeatures, true);
            }, states.size());
            double evalUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            System.out.printf("%-14s %8d %12.2f %12.2f %12d%n",
                    cfg.game.name(), states.size(), tokUs, evalUs, heuristic.currentJSONBag.size());
        }
    }

    /**
     * Where one JSON-Bag OSLA decision goes: the state copy per candidate action, the forward-model step, and the
     * value-function evaluation. Measured over a real playout so the numbers include the growing trajectory bag.
     */
    static void playerSplit() {
        System.out.printf("%-14s %10s %10s %10s %10s %8s%n", "game", "decisions", "copy_us", "step_us", "eval_us", "cands");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            var game = cfg.game.createGameInstance(cfg.nPlayers, 7L);
            Random rnd = new Random(7L);
            List<core.AbstractPlayer> players = new ArrayList<>();
            for (int i = 0; i < cfg.nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
            game.reset(players);
            AbstractGameState gs = game.getGameState();
            heuristic.resetCurrentBag(gs);
            long copyNs = 0, stepNs = 0, evalNs = 0, cands = 0, decisions = 0;
            while (gs.isNotTerminal() && decisions < 120) {
                List<AbstractAction> actions = JSONBagEquivalence.deterministic(
                        game.getForwardModel().computeAvailableActions(gs));
                if (actions.isEmpty()) break;
                heuristic.updateJSONBag(gs);
                for (int i = 0; i < Math.min(actions.size(), 12); i++) {
                    long t0 = System.nanoTime();
                    AbstractGameState copy = gs.copy();
                    long t1 = System.nanoTime();
                    game.getForwardModel().next(copy, actions.get(i).copy());
                    long t2 = System.nanoTime();
                    heuristic.evaluateState(copy, 0);
                    long t3 = System.nanoTime();
                    copyNs += t1 - t0; stepNs += t2 - t1; evalNs += t3 - t2; cands++;
                }
                decisions++;
                game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())));
            }
            System.out.printf("%-14s %10d %10.1f %10.1f %10.1f %8.1f  bag=%d protos=%d%n", cfg.game.name(), decisions,
                    copyNs / 1000.0 / Math.max(1, cands), stepNs / 1000.0 / Math.max(1, cands),
                    evalNs / 1000.0 / Math.max(1, cands), cands / (double) Math.max(1, decisions),
                    heuristic.currentJSONBag.size(), prototypes.size());
        }
    }

    /**
     * How the accumulated trajectory bag distributes over the prototype support: tokens no prototype has ever seen
     * cost four dead loads each in the distance loop, and tokens every prototype has cost a logarithm each.
     */
    static void bagStats() {
        System.out.printf("%-14s %10s %10s %10s %10s %10s%n", "game", "bagtok", "unsupported", "all", "partial", "massOutside");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            var game = cfg.game.createGameInstance(cfg.nPlayers, 7L);
            Random rnd = new Random(7L);
            List<core.AbstractPlayer> players = new ArrayList<>();
            for (int i = 0; i < cfg.nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
            game.reset(players);
            AbstractGameState gs = game.getGameState();
            heuristic.resetCurrentBag(gs);
            PrototypeSet compiled = heuristic.compiledPrototypes();
            for (int step = 0; step < 120 && gs.isNotTerminal(); step++) {
                List<AbstractAction> actions = JSONBagEquivalence.deterministic(
                        game.getForwardModel().computeAvailableActions(gs));
                if (actions.isEmpty()) break;
                heuristic.updateJSONBag(gs);
                game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())));
            }
            TokenBag bag = (TokenBag) heuristic.currentJSONBag;
            int[] ids = bag.nonZeroIds();
            int n = bag.nonZeroCount();
            int unsupported = 0, all = 0, partial = 0;
            long massOutside = 0, massTotal = 0;
            int stride = compiled.stride;
            double[] packed = compiled.packed;
            for (int i = 0; i < n; i++) {
                int id = ids[i];
                int count = bag.count(id);
                massTotal += count;
                int base = id * stride;
                if (base >= packed.length) { unsupported++; massOutside += count; continue; }
                int positive = 0;
                for (int p = 0; p < compiled.count; p++) if (packed[base + 2 * p] != 0.0) positive++;
                if (positive == 0) { unsupported++; massOutside += count; }
                else if (positive == compiled.count) all++;
                else partial++;
            }
            System.out.printf("%-14s %10d %10d %10d %10d %10s%n", cfg.game.name(), n, unsupported, all, partial,
                    String.format("%.1f%%", 100.0 * massOutside / Math.max(1, massTotal)));
        }
    }

    /** Inside evaluateState: tokenising the candidate against the accumulated bag, versus the distance loop. */
    static void evalSplit() {
        System.out.printf("%-14s %10s %10s %10s %10s %10s%n", "game", "decisions", "tok_us", "dist_us", "bagtok", "shared");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            var game = cfg.game.createGameInstance(cfg.nPlayers, 7L);
            Random rnd = new Random(7L);
            List<core.AbstractPlayer> players = new ArrayList<>();
            for (int i = 0; i < cfg.nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
            game.reset(players);
            AbstractGameState gs = game.getGameState();
            heuristic.resetCurrentBag(gs);
            long tokNs = 0, distNs = 0, n = 0; int bagTokens = 0;
            while (gs.isNotTerminal() && n < 120) {
                List<AbstractAction> actions = JSONBagEquivalence.deterministic(
                        game.getForwardModel().computeAvailableActions(gs));
                if (actions.isEmpty()) break;
                AbstractGameState copy = gs.copy();
                game.getForwardModel().next(copy, actions.get(0).copy());
                heuristic.currentJSONBag.getClass();                       // keep the field reachable
                TokenBag bag = (TokenBag) heuristic.currentJSONBag;
                bag.startRecording();
                long t0 = System.nanoTime();
                Tokenizer.tokenizeInto(copy, bag);
                long t1 = System.nanoTime();
                double[] out = new double[prototypes.size()];
                core.AbstractGameState guard = copy;
                heuristic.evaluateState(guard, 0);                          // warm the same path the player uses
                long t2 = System.nanoTime();
                bag.rollback();
                tokNs += t1 - t0;
                distNs += t2 - t1;
                bagTokens = bag.size();
                n++;
                game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())));
            }
            System.out.printf("%-14s %10d %10.1f %10.1f %10d %10s%n", cfg.game.name(), n,
                    tokNs / 1000.0 / Math.max(1, n), distNs / 1000.0 / Math.max(1, n), bagTokens, "-");
        }
    }

    /**
     * Phase split in the feature-selected configuration: tokenising a state, the distance loop over the accumulated
     * bag, and the container extraction that feeds tokenising. Steers which phase the next round should attack.
     */
    static void levers(int topK) {
        System.out.printf("%-14s %8s %12s %12s %12s %12s %8s%n", "game", "states", "tokenize_us", "dist_us", "tree_us", "container_us", "bag");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, topK));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            TokenBag bag = (TokenBag) heuristic.currentJSONBag;
            PrototypeSet compiled = heuristic.compiledPrototypes();
            int[] count = {0};
            double tokUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) Tokenizer.tokenize(gs, topFeatures, true);
            }, states.size());
            double distUs = microsPerOp(10, 21, () -> {
                double[] out = new double[prototypes.size()];
                for (AbstractGameState gs : states) JensenShannonDistance.distances(compiled, bag, out);
                count[0] += out.length;
            }, states.size());
            double contUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) GameStateContainerFactory.createContainer(gs);
            }, states.size());
            com.google.gson.Gson gson = new com.google.gson.Gson();
            double treeUs = microsPerOp(10, 21, () -> {
                for (AbstractGameState gs : states) gson.toJsonTree(GameStateContainerFactory.createContainer(gs));
            }, states.size()) - contUs;
            System.out.printf("%-14s %8d %12.2f %12.2f %12.2f %12.2f %8d%n",
                    cfg.game.name(), states.size(), tokUs, distUs, treeUs, contUs, bag.size());
        }
    }

    /**
     * Average warm `evaluateState` over a fixed corpus (the trajectory bag is warmed first, as the player's would be).
     * Averaging over 220 states makes this far less noisy than timing whole games, so it is the metric the iteration
     * rounds use to decide keep-or-revert.
     */
    static void warm() {
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, 500));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            double us = microsPerOp(15, 31, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            System.out.printf("%-14s %10s %12.2f%n", cfg.game.name(), "eval_us", us);
        }
    }

    /** Cost of building a value function from loaded prototypes: dictionary growth and prototype compilation. */
    static void heuristicBuild() {
        System.out.printf("%-14s %10s %10s %10s%n", "game", "tokens", "build_us", "us_per_ktok");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            int tokens = 0;
            for (Map<String, Integer> prototype : prototypes) tokens += prototype.size();
            double us = microsPerOp(5, 9, () -> new JSONBagHeuristic(prototypes), 1);
            System.out.printf("%-14s %10d %10.1f %10.1f%n", cfg.game.name(), tokens, us, us / Math.max(1, tokens / 1000.0));
        }
    }

    /** How much of a tokenise call is Gson tree building, and how much is walking it into a bag? */
    static void split() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        System.out.printf("%-14s %12s %12s %12s %10s %8s%n", "game", "totree_us", "walk_us", "full_us", "emissions", "ns_emit");
        for (Config cfg : CONFIGS) {
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            List<core.AbstractGameStateContainer> containers = new ArrayList<>();
            for (AbstractGameState gs : states) containers.add(GameStateContainerFactory.createContainer(gs));
            List<com.google.gson.JsonElement> trees = new ArrayList<>();
            for (core.AbstractGameStateContainer c : containers) trees.add(gson.toJsonTree(c));

            double treeUs = microsPerOp(3, 5, () -> {
                for (core.AbstractGameStateContainer c : containers) gson.toJsonTree(c);
            }, containers.size());
            double fullUs = microsPerOp(3, 5, () -> {
                for (AbstractGameState gs : states) Tokenizer.tokenize(gs);
            }, states.size());
            TokenDictionary dict = new TokenDictionary();
            TokenBag bag = new TokenBag(dict);
            double walkUs = microsPerOp(3, 5, () -> {
                for (com.google.gson.JsonElement tree : trees) {
                    bag.clear();
                    Tokenizer.tokenizeInto(tree, bag, "", true, "both", true, false, false, false);
                }
            }, trees.size());
            long emissions = 0;
            for (AbstractGameState gs : states) emissions += ((TokenBag) Tokenizer.tokenize(gs)).total();
            double perState = emissions / (double) states.size();
            System.out.printf("%-14s %12.2f %12.2f %12.2f %10.0f %8.1f%n",
                    cfg.game.name(), treeUs, walkUs, fullUs, perState, walkUs * 1000.0 / Math.max(1, perState));
        }
    }

    /** Min of {@code reps} timed passes: the minimum is the least noisy statistic when the OS steals cycles. */
    static double microsPerOp(int warmup, int reps, Runnable body, int opsPerRep) {
        for (int i = 0; i < warmup; i++) body.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            body.run();
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best / 1000.0 / opsPerRep;
    }

    // ------------------------------------------------------------------ full games

    static void gameTiming(int nGames) {
        System.out.printf("%-14s %10s %10s %10s %10s%n", "game", "jsonbag_ms", "osla_ms", "ratio", "actions");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            long bagBest = Long.MAX_VALUE, oslaBest = Long.MAX_VALUE;
            int nActions = 0;
            for (long seed = 1; seed <= nGames; seed++) {
                nActions = 0;
                long bagMs = timeGame(cfg, prototypes, true, seed);
                if (bagMs < 0) continue;
                nActions = lastActions;
                long oslaMs = timeGame(cfg, prototypes, false, seed);
                bagBest = Math.min(bagBest, bagMs);
                oslaBest = Math.min(oslaBest, oslaMs);
            }
            System.out.printf("%-14s %10d %10d %10.1f %10d%n",
                    cfg.game.name(), bagBest, oslaBest, bagBest / (double) Math.max(1, oslaBest), nActions);
        }
    }

    static int lastActions = 0;

    /** Returns milliseconds for one game, or -1 if the game could not be set up. */
    /**
     * The whitelist for a configuration: the game's own {@code features-topK.json} when it exists (only Dominion ships
     * a top-100 file), otherwise the first K entries of {@code features-top500.json}. The fallback is an
     * approximation - the real top-K lists are selected per game stage, so they are not prefixes of the top-500 list.
     */
    static List<String> topFeatures(Config cfg, int topK) {
        String folder = ROOT + "/" + cfg.folder;
        String exact = folder + "/features-top" + topK + ".json";
        if (new java.io.File(exact).isFile()) return Tokenizer.loadStringList(exact);
        List<String> all = Tokenizer.loadStringList(folder + "/features-top500.json");
        return all.size() <= topK ? all : new ArrayList<>(all.subList(0, topK));
    }

    static boolean hasExactTopK(Config cfg, int topK) {
        return new java.io.File(ROOT + "/" + cfg.folder + "/features-top" + topK + ".json").isFile();
    }

    /** Whole games with a feature-selected value function, against vanilla OSLA. */
    static void gameTimingFiltered(int topK, int nGames) {
        System.out.printf("%-14s %10s %10s %10s %10s %8s%n", "game", "jsonbag_ms", "osla_ms", "ratio", "actions", "features");
        for (Config cfg : CONFIGS) {
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            List<String> features = topFeatures(cfg, topK);
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, new HashSet<>(features), true);
            long bagBest = Long.MAX_VALUE, oslaBest = Long.MAX_VALUE;
            int nActions = 0;
            for (long seed = 1; seed <= nGames; seed++) {
                long bagMs = timeGameFiltered(cfg, prototypes, features, true, seed);
                if (bagMs < 0) continue;
                nActions = lastActions;
                long oslaMs = timeGameFiltered(cfg, prototypes, features, false, seed);
                bagBest = Math.min(bagBest, bagMs);
                oslaBest = Math.min(oslaBest, oslaMs);
            }
            System.out.printf("%-14s %10d %10d %10.1f %10d %8s%n", cfg.game.name(), bagBest, oslaBest,
                    bagBest / (double) Math.max(1, oslaBest), nActions,
                    hasExactTopK(cfg, topK) ? "exact" : "derived");
        }
    }

    static long timeGameFiltered(Config cfg, List<Map<String, Integer>> prototypes, List<String> features,
                                 boolean jsonBag, long seed) {
        GameType gt = cfg.game;
        List<core.AbstractPlayer> players = new ArrayList<>();
        for (int i = 0; i < cfg.nPlayers; i++) {
            if (i == 0 && jsonBag) {
                JSONBagOSLAPlayer p = new JSONBagOSLAPlayer(prototypes, features, new Random(seed * 31 + 7));
                if (gt == GameType.Wonders7) p.setSimultaneousMode(true);
                players.add(p);
            } else if (i == 0) {
                players.add(new OSLAPlayer(new Random(seed * 31 + 7)));
            } else {
                players.add(new OSLAPlayer(new Random(seed * 31 + 100 + i)));
            }
        }
        var game = gt.createGameInstance(cfg.nPlayers, seed);
        game.reset(players);
        long t0 = System.nanoTime();
        game.run();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        lastActions = game.getGameState().getGameTick();
        return ms;
    }

    static long timeGame(Config cfg, List<Map<String, Integer>> prototypes, boolean jsonBag, long seed) {
        GameType gt = cfg.game;
        List<core.AbstractPlayer> players = new ArrayList<>();
        for (int i = 0; i < cfg.nPlayers; i++) {
            if (i == 0) {
                if (jsonBag) {
                    JSONBagOSLAPlayer p = new JSONBagOSLAPlayer(prototypes, Collections.emptyList(), new Random(seed * 31 + 7));
                    if (gt == GameType.Wonders7) p.setSimultaneousMode(true);
                    players.add(p);
                } else {
                    players.add(new OSLAPlayer(new Random(seed * 31 + 7)));
                }
            } else {
                players.add(new OSLAPlayer(new Random(seed * 31 + 100 + i)));
            }
        }
        var game = gt.createGameInstance(cfg.nPlayers, seed);
        game.reset(players);
        long t0 = System.nanoTime();
        game.run();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        lastActions = game.getGameState().getGameTick();
        return ms;
    }
}
