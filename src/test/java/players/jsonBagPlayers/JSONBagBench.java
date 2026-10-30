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
        int games = args.length > 1 && Character.isDigit(args[1].charAt(0)) ? Integer.parseInt(args[1]) : 3;
        switch (mode) {
            case "timing" -> timing();
            case "game" -> gameTiming(games);
            case "split" -> split();
            case "build" -> heuristicBuild();
            case "player" -> playerSplit();
            case "evalsplit" -> evalSplit();
            case "bagstats" -> bagStats();
            case "evalparts" -> evalParts(args.length > 1 ? args[1] : null);
            case "levers" -> levers(500, args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
            case "prof" -> profile(args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : "eval",
                    args.length > 3 ? Integer.parseInt(args[3]) : 20);
            case "wlstats" -> whitelistStats(args.length > 1 ? args[1] : null);
            case "warm" -> warm(args.length > 1 ? args[1] : null);
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
     * Phase split in the feature-selected configuration, per game: container extraction, the Gson tree the tokeniser
     * walks, the walk itself (warm dictionary), the distance loop, and the whole `evaluateState`. Each round picks its
     * target from this table rather than from a fixed list of candidate ideas.
     */
    static void levers(int topK, String only, String phase) {
        System.out.printf("%-14s %6s %8s %8s %8s %10s %8s %8s %8s %9s %6s%n", "game", "states", "cont_us", "tree_us",
                "walk_us", "tokenise_us", "rec_us", "dist_us", "eval_us", "eval_after", "emits");
        for (Config cfg : CONFIGS) {
            if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(cfg.game.name())) continue;
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, topK));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            // steady state first, so every phase below sees the same dictionary
            for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            TokenBag bag = (TokenBag) heuristic.currentJSONBag;
            PrototypeSet compiled = heuristic.compiledPrototypes();
            com.google.gson.Gson gson = new com.google.gson.Gson();

            List<core.AbstractGameStateContainer> containers = new ArrayList<>();
            List<com.google.gson.JsonElement> trees = new ArrayList<>();
            for (AbstractGameState gs : states) {
                core.AbstractGameStateContainer c = GameStateContainerFactory.createContainer(gs);
                containers.add(c);
                trees.add(gson.toJsonTree(c));
            }
            TokenBag scratch = new TokenBag(bag.dictionary());
            long emissions = 0;
            for (AbstractGameState gs : states) {
                scratch.clear();
                Tokenizer.tokenizeInto(gs, scratch);
                emissions += scratch.total();
            }

            double contUs = measure(phase, "cont", 10, 21, () -> {
                for (AbstractGameState gs : states) GameStateContainerFactory.createContainer(gs);
            }, states.size());
            double evalUs = measure(phase, "eval", 25, 15, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            double treeUs = measure(phase, "tree", 10, 21, () -> {
                for (core.AbstractGameStateContainer c : containers) gson.toJsonTree(c);
            }, states.size()) - (Double.isNaN(contUs) ? 0 : contUs);
            double walkUs = measure(phase, "walk", 5, 9, () -> {
                for (com.google.gson.JsonElement tree : trees) {
                    scratch.clear();
                    Tokenizer.tokenizeInto(tree, scratch, "", true, "both", true, false, false, false);
                }
            }, states.size());
            double tokUs = measure(phase, "tok", 5, 9, () -> {
                for (AbstractGameState gs : states) {
                    scratch.clear();
                    Tokenizer.tokenizeInto(gs, scratch);
                }
            }, states.size());
            // tokenise into the live bag with the write log on, then undo it: the difference to tokUs is the cost of
            // recording and rollback, which evaluateState pays once per candidate action and no earlier phase column saw
            double recUs = measure(phase, "rec", 5, 9, () -> {
                for (AbstractGameState gs : states) {
                    bag.startRecording();
                    Tokenizer.tokenizeInto(gs, bag);
                    bag.rollback();
                }
            }, states.size());
            double[] out = new double[prototypes.size()];
            double distUs = measure(phase, "dist", 5, 9, () -> {
                for (AbstractGameState gs : states) JensenShannonDistance.distances(compiled, bag, out);
            }, states.size());
            // measured again at the end of the run, when every other phase's working set is live
            double evalAfterUs = phase == null ? microsPerOp(25, 15, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size()) : Double.NaN;
            if (phase != null) {
                System.out.printf("%-14s %8s %10.2f%n", cfg.game.name(), phase, pick(phase, contUs, treeUs, walkUs, tokUs, recUs, distUs, evalUs));
            } else {
                System.out.printf("%-14s %6d %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8d%n",
                        cfg.game.name(), states.size(), contUs, treeUs, walkUs, tokUs, recUs, distUs, evalUs, evalAfterUs,
                        emissions / Math.max(1, states.size()));
            }
        }
    }

    /** Verbatim copy of JSONBagHeuristic.evaluateState's hot path, to separate "the work" from "the wrapper". */
    static double evalCopy(AbstractGameState gs, int playerId, PrototypeSet compiled, TokenBag current, double[] buf) {
        if (gs.getNPlayers() != compiled.count) throw new RuntimeException("mismatch");
        if (gs.getPlayerResults()[playerId] == core.CoreConstants.GameResult.LOSE_GAME) return 0;
        if (gs.getPlayerResults()[playerId] == core.CoreConstants.GameResult.WIN_GAME) return 1.0;
        if (buf.length != compiled.count) buf = new double[compiled.count];
        current.startRecording();
        try {
            Tokenizer.tokenizeInto(gs, current);
            JensenShannonDistance.distances(compiled, current, buf);
        } finally {
            current.rollback();
        }
        double sum = 0;
        for (int i = 0; i < buf.length; i++) sum += 1.0 - buf[i];
        return sum / buf.length;
    }

    /** Inside a single `evaluateState`: record+tokenise, distances, rollback, value normalisation. */
    static void evalParts(String only) {
        System.out.printf("%-14s %10s %8s %8s %8s %8s %8s%n", "game", "decisions", "tok_us", "dist_us", "roll_us", "norm_us", "eval_us");
        for (Config cfg : CONFIGS) {
            if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(cfg.game.name())) continue;
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, 500));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);     // steady state
            TokenBag bag = (TokenBag) heuristic.currentJSONBag;
            PrototypeSet compiled = heuristic.compiledPrototypes();
            double[] out = new double[prototypes.size()];

            long tok = 0, dist = 0, roll = 0, norm = 0;
            for (int pass = 0; pass < 3; pass++) {           // warm, then report the last pass only
                tok = dist = roll = norm = 0;
                for (AbstractGameState gs : states) {
                    long t0 = System.nanoTime();
                    bag.startRecording();
                    Tokenizer.tokenizeInto(gs, bag);
                    long t1 = System.nanoTime();
                    JensenShannonDistance.distances(compiled, bag, out);
                    long t2 = System.nanoTime();
                    bag.rollback();
                    long t3 = System.nanoTime();
                    double sum = 0;
                    for (double d : out) sum += 1.0 - d;
                    long t4 = System.nanoTime();
                    tok += t1 - t0; dist += t2 - t1; roll += t3 - t2; norm += t4 - t3;
                    if (sum < -1) System.err.print("");
                }
            }
            int n = states.size();
            int[] sink = {0};
            int maxMerged = 0, sumMerged = 0;
            for (AbstractGameState gs : states) {
                bag.startRecording();
                Tokenizer.tokenizeInto(gs, bag);
                maxMerged = Math.max(maxMerged, bag.nonZeroCount());
                sumMerged += bag.nonZeroCount();
                bag.rollback();
            }
            double tokOnlyUs = microsPerOp(20, 15, () -> {
                for (AbstractGameState gs : states) {
                    bag.startRecording();
                    Tokenizer.tokenizeInto(gs, bag);
                    bag.rollback();
                }
            }, states.size());
            double evalUs = microsPerOp(60, 15, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            double noFreqUs = microsPerOp(20, 15, () -> {
                for (AbstractGameState gs : states) {
                    bag.startRecording();
                    Tokenizer.tokenizeInto(core.AbstractGameStateContainer.class.cast(
                            players.jsonBagPlayers.GameStateContainerFactory.createContainer(gs)), bag,
                            "", true, "both", false, false, false, false);
                    bag.rollback();
                }
            }, states.size());
            double[] buf = new double[prototypes.size()];
            double copyUs = microsPerOp(60, 15, () -> {
                for (AbstractGameState gs : states) evalCopy(gs, 0, compiled, bag, buf);
            }, states.size());
            System.out.printf("%-14s %10d %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %8.2f %6d%n", cfg.game.name(), n,
                    tok / 1000.0 / n, dist / 1000.0 / n, roll / 1000.0 / n, norm / 1000.0 / n,
                    tokOnlyUs, noFreqUs, copyUs, evalUs, sumMerged / n);
        }
    }

    /**
     * Average warm `evaluateState` over a fixed corpus (the trajectory bag is warmed first, as the player's would be).
     * Averaging over 220 states makes this far less noisy than timing whole games, so it is the metric the iteration
     * rounds use to decide keep-or-revert.
     */
    static void warm(String only) {
        int repeats = 1;
        if (only != null && only.contains("@")) {
            String[] parts = only.split("@");
            only = parts[0];
            repeats = Integer.parseInt(parts[1]);
        }
        for (int pass = 0; pass < repeats; pass++)
        for (Config cfg : CONFIGS) {
            if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(cfg.game.name())) continue;
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, 500));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            // reach the steady state before any phase is timed: one full pass over the corpus interns every token, and
            // the dictionary then stays at that size for the rest of the run. Without this, the first phase measured
            // reads a cold 1.3k-entry dictionary and every later one reads the 19k-entry table, and the two are not
            // comparable (3x apart on Wonders7).
            for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            double us = microsPerOp(60, 15, () -> {
                for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            }, states.size());
            // stderr, so the A/B parser on stdout is untouched: corpus shape plus per-state allocation, which is what
            // explains why the same game costs several times more when five other games have run in the same JVM.
            TokenBag scratch = new TokenBag(((TokenBag) heuristic.currentJSONBag).dictionary());
            long emissions = 0;
            for (AbstractGameState gs : states) {
                scratch.clear();
                Tokenizer.tokenizeInto(gs, scratch);
                emissions += scratch.total();
            }
            var tm = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            long alloc0 = tm.getCurrentThreadAllocatedBytes();
            for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
            long allocPerState = (tm.getCurrentThreadAllocatedBytes() - alloc0) / Math.max(1, states.size());
            System.err.printf("# %s corpus=%d emits/state=%.0f bag=%d dict=%d alloc/state=%dKB maxheap=%dMB%n",
                    cfg.game.name(), states.size(), emissions / (double) states.size(),
                    heuristic.currentJSONBag.size(), ((TokenBag) heuristic.currentJSONBag).dictionary().size(),
                    allocPerState / 1024,
                    Runtime.getRuntime().maxMemory() / (1024 * 1024));
            System.out.printf("%-14s %10s %12.2f%n", cfg.game.name(), "eval_us", us);
        }
    }

    /**
     * Runs one pipeline phase in a loop for a fixed number of seconds, after the corpus, the dictionary and the JIT
     * are warm, so a JFR recording taken over this run attributes its samples to that phase alone. This is how a
     * round picks its target inside the phase the `levers` table says dominates: the corpus build and the warm-up
     * passes sit outside the loop, so they do not dilute the samples.
     *
     * <pre>
     * java -XX:StartFlightRecording=filename=w.jfr,settings=profile -cp ... JSONBagBench prof Wonders7 walk 20
     * </pre>
     */
    static void profile(String only, String phase, int seconds) {
        for (Config cfg : CONFIGS) {
            if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(cfg.game.name())) continue;
            var prototypes = Tokenizer.loadPrototypes(ROOT + "/" + cfg.folder, cfg.nPlayers, true);
            Set<String> topFeatures = new HashSet<>(topFeatures(cfg, 500));
            for (Map<String, Integer> prototype : prototypes) Tokenizer.filter(prototype, topFeatures, true);
            List<AbstractGameState> states = corpus(cfg, 220, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
            heuristic.setFilterSet(topFeatures);
            heuristic.resetCurrentBag(states.get(0));
            for (int i = 1; i < Math.min(30, states.size()); i++) heuristic.updateJSONBag(states.get(i));
            for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);     // steady dictionary
            TokenBag bag = (TokenBag) heuristic.currentJSONBag;
            PrototypeSet compiled = heuristic.compiledPrototypes();
            double[] out = new double[prototypes.size()];
            com.google.gson.Gson gson = new com.google.gson.Gson();
            List<com.google.gson.JsonElement> trees = new ArrayList<>();
            if ("walk".equals(phase)) {
                for (AbstractGameState gs : states) {
                    trees.add(gson.toJsonTree(GameStateContainerFactory.createContainer(gs)));
                }
            }
            Runnable body = switch (phase) {
                case "eval" -> () -> {
                    for (AbstractGameState gs : states) heuristic.evaluateState(gs, 0);
                };
                case "walk" -> () -> {
                    for (com.google.gson.JsonElement tree : trees) {
                        bag.startRecording();
                        Tokenizer.tokenizeInto(tree, bag, "", true, "both", true, false, false, false);
                        bag.rollback();
                    }
                };
                case "tok" -> () -> {
                    for (AbstractGameState gs : states) {
                        bag.startRecording();
                        Tokenizer.tokenizeInto(gs, bag);
                        bag.rollback();
                    }
                };
                case "dist" -> () -> {
                    for (AbstractGameState gs : states) JensenShannonDistance.distances(compiled, bag, out);
                };
                default -> throw new IllegalArgumentException("unknown profile phase " + phase);
            };
            for (int i = 0; i < 20; i++) body.run();                              // JIT settles before the timed loop
            long t0 = System.nanoTime();
            long end = t0 + seconds * 1_000_000_000L;
            long passes = 0;
            while (System.nanoTime() < end) {
                body.run();
                passes++;
            }
            double elapsedUs = (System.nanoTime() - t0) / 1000.0;
            System.out.printf("%-14s %-5s passes=%d us_per_state=%.2f%n", cfg.game.name(), phase, passes,
                    elapsedUs / (passes * (double) states.size()));
        }
    }

    /**
     * Classifies every token an evaluation emits under the top-500 whitelist, so a round can tell how much of the
     * intern probe exists only because the dictionary interned tokens the filter then rejects.
     * <p>
     * Three outcomes per emission: accepted (it enters the bag); rejected under a live path (some whitelisted token
     * extends the same parent path, so the value has to be built before it can be rejected); rejected under a dead
     * path (no whitelisted token shares the parent path, so the whole subtree contributes nothing and could be
     * skipped without building any value at all).
     */
    static void whitelistStats(String only) {
        System.out.printf("%-14s %8s %8s %8s %8s %8s %8s%n", "game", "emits", "accept", "rejLive", "rejDead",
                "dead%", "wlPaths");
        for (Config cfg : CONFIGS) {
            if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(cfg.game.name())) continue;
            List<String> features = topFeatures(cfg, 500);
            Set<String> whitelist = new HashSet<>(features);
            Set<String> livePaths = new HashSet<>();          // every '.'-boundary prefix of a whitelisted token
            for (String token : whitelist) {
                for (int i = token.indexOf('.', 1); i > 0; i = token.indexOf('.', i + 1)) {
                    livePaths.add(token.substring(0, i));
                }
            }
            List<AbstractGameState> states = corpus(cfg, 40, 11L, 22L, 33L, 44L);
            if (states.isEmpty()) continue;
            long emits = 0, accept = 0, rejLive = 0, rejDead = 0, mismatched = 0;
            for (AbstractGameState gs : states) {
                Map<String, Integer> all = Tokenizer.tokenize(gs);          // no filter: every emission is counted
                Map<String, Integer> expected = new HashMap<>(all);
                expected.keySet().retainAll(whitelist);
                Map<String, Integer> filtered = new HashMap<>(Tokenizer.tokenize(gs, whitelist, true));
                if (!expected.equals(filtered)) mismatched++;
                for (Map.Entry<String, Integer> e : all.entrySet()) {
                    String token = e.getKey();
                    int count = e.getValue();
                    emits += count;
                    if (whitelist.contains(token)) {
                        accept += count;
                        continue;
                    }
                    int dot = token.lastIndexOf('.');
                    String parent = dot > 0 ? token.substring(0, dot) : token;
                    if (livePaths.contains(parent)) {
                        rejLive += count;
                    } else {
                        rejDead += count;
                    }
                }
            }
            System.out.printf("%-14s %8d %8d %8d %8d %7.1f%% %8d%n", cfg.game.name(), emits, accept, rejLive, rejDead,
                    100.0 * rejDead / Math.max(1, emits), livePaths.size());
            TokenDictionary probe = new TokenDictionary();
            probe.setFilter(whitelist, true);
            System.out.printf("%-14s whitelist=%d prune=%s prefixes=%d bag-mismatched-states=%d%n", cfg.game.name(),
                    whitelist.size(), probe.pruning(), probe.prefixCount(), mismatched);
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
    /** Runs {@code body} only when it is the phase under test (or when no phase was named); NaN otherwise. */
    static double measure(String only, String name, int warmup, int reps, Runnable body, int opsPerRep) {
        if (only != null && !only.equals(name)) return Double.NaN;
        return microsPerOp(warmup, reps, body, opsPerRep);
    }

    static double pick(String phase, double cont, double tree, double walk, double tok, double rec, double dist, double eval) {
        return switch (phase) {
            case "cont" -> cont;
            case "tree" -> tree;
            case "walk" -> walk;
            case "tok" -> tok;
            case "rec" -> rec;
            case "dist" -> dist;
            case "eval" -> eval;
            default -> throw new IllegalArgumentException("unknown phase " + phase);
        };
    }

    static double microsPerOp(int warmup, int reps, Runnable body, int opsPerRep) {
        for (int i = 0; i < warmup; i++) body.run();
        long best = Long.MAX_VALUE;
        boolean trace = Boolean.getBoolean("jsonbag.trace");
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            body.run();
            long el = System.nanoTime() - t0;
            if (trace) System.err.printf("#   rep %2d %10.2f us%n", i, el / 1000.0 / opsPerRep);
            best = Math.min(best, el);
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
