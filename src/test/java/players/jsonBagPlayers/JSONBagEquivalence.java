package players.jsonBagPlayers;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.GameType;
import players.simple.RandomPlayer;

import utilities.Utils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Behavioural fingerprint for the JSON-Bag package: token maps, JSD values and heuristic values over the states of a
 * seeded random playout. Run before and after an optimisation into two directories and diff them; a changed token
 * digest is a behaviour change, a changed distance in the last digits is floating-point noise.
 * <p>
 * The playout tokenises the <em>live</em> game state rather than stored copies: some games' {@code copy()} produces
 * states whose JSON differs in list order, which would make the fingerprint itself irreproducible. The available
 * actions are sorted before the seeded pick, because several forward models build that list by iterating a hash-based
 * collection and the order then follows the JVM's identity hashes. Decisions are deliberately not fingerprinted:
 * looking one action ahead means copying the state, and {@code copy()} redeterminises hidden information from an
 * unseeded {@code Random} (see {@code AbstractGameState.redeterminisationRnd}), so such a value depends on when the
 * copy was taken rather than on the code under test. Chosen-action equivalence is therefore covered by the unit tests
 * and, in the player, by {@code JSONBagOSLAPlayerTest}.
 *
 * <pre>
 * java -cp "target/classes;target/test-classes;target/dependency/*" players.jsonBagPlayers.JSONBagEquivalence &lt;outDir&gt; [quick] [game]
 * </pre>
 */
public final class JSONBagEquivalence {

    /** Tokeniser configurations compared token-for-token. */
    private record Combo(String name, java.util.function.BiFunction<AbstractGameState, Set<String>, Map<String, Integer>> fn) {}

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : ".artifacts/jsonbag/equiv-current");
        boolean quick = args.length > 1 && args[1].equals("quick");
        Files.createDirectories(outDir);
        String only = args.length > 2 ? args[2] : null;
        if ("all".equalsIgnoreCase(only)) only = null;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 11L;
        for (JSONBagBench.Config cfg : JSONBagBench.CONFIGS) {
            if (only != null && !cfg.game.name().equalsIgnoreCase(only)) continue;
            String variant = System.getProperty("jsonbag.variant");
            String folder = cfg.folder;
            if (variant != null && new java.io.File(JSONBagBench.ROOT + "/" + cfg.game.name() + "/" + variant).isDirectory()) {
                folder = cfg.game.name() + "/" + variant;
            }
            var prototypes = Tokenizer.loadPrototypes(JSONBagBench.ROOT + "/" + folder, cfg.nPlayers, true);
            StringBuilder out = new StringBuilder();
            fingerprint(cfg, prototypes, quick ? 40 : 120, seed, out);
            Files.writeString(outDir.resolve(cfg.game.name() + ".txt"), out.toString(), StandardCharsets.UTF_8);
            System.out.print(out);
        }
    }

    private static List<Combo> combos() {
        return List.of(
                new Combo("default-object", (gs, f) -> Tokenizer.tokenize(gs)),
                new Combo("whitelist", (gs, f) -> Tokenizer.tokenize(gs, f, true)),
                new Combo("blacklist", (gs, f) -> Tokenizer.tokenize(gs, f, false)),
                new Combo("unordered", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", false, "both", true, false, false, false, null, false)),
                new Combo("ordered-only", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", true, "ordered", true, false, false, false, null, false)),
                new Combo("unordered-only", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", false, "unordered", true, false, false, false, null, false)),
                new Combo("no-numfreq", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", true, "both", false, false, false, false, null, false)),
                new Combo("player-filter", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", true, "both", true, true, false, false, null, false)),
                new Combo("binning", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", true, "both", true, false, true, false, null, false)),
                new Combo("pairXY", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "", true, "both", true, false, false, true, null, false)),
                new Combo("prefix", (gs, f) -> Tokenizer.tokenize(
                        GameStateContainerFactory.createContainer(gs), "root", true, "both", true, false, false, false, null, false)),
                new Combo("string-roundtrip", (gs, f) -> Tokenizer.tokenize(
                        new com.google.gson.Gson().toJson(GameStateContainerFactory.createContainer(gs)),
                        "", true, "both", true, false, false, false, null, false)));
    }

    private static void fingerprint(JSONBagBench.Config cfg, List<Map<String, Integer>> prototypes, int maxStates,
                                    long seed, StringBuilder out) {
        GameType gt = cfg.game;
        var game = gt.createGameInstance(cfg.nPlayers, seed);
        List<core.AbstractPlayer> players = new ArrayList<>();
        Random rnd = new Random(seed);
        for (int i = 0; i < cfg.nPlayers; i++) players.add(new RandomPlayer(new Random(rnd.nextLong())));
        game.reset(players);
        AbstractGameState gs = game.getGameState();

        List<Combo> combos = combos();
        StringBuilder[] tokenDigests = new StringBuilder[combos.size()];
        for (int i = 0; i < tokenDigests.length; i++) tokenDigests[i] = new StringBuilder();
        StringBuilder jsd = new StringBuilder();
        StringBuilder eval = new StringBuilder();
        StringBuilder stateDigests = new StringBuilder();

        JSONBagHeuristic heuristic = new JSONBagHeuristic(prototypes);
        Set<String> whitelist = Collections.emptySet();
        Set<String> blacklist = Collections.emptySet();
        int n = 0;
        while (gs.isNotTerminal() && n < maxStates) {
            List<AbstractAction> actions = deterministic(game.getForwardModel().computeAvailableActions(gs));
            if (actions.isEmpty()) break;
            if (n == 0) {
                List<String> keys = new ArrayList<>(Tokenizer.tokenize(gs).keySet());
                Collections.sort(keys);
                whitelist = new HashSet<>(keys.subList(0, Math.min(25, keys.size())));
                blacklist = new HashSet<>(keys.subList(0, Math.min(7, keys.size())));
                heuristic.resetCurrentBag(gs);
            } else {
                heuristic.updateJSONBag(gs);
            }
            for (int c = 0; c < combos.size(); c++) {
                Map<String, Integer> bag = combos.get(c).fn().apply(gs, whitelist);
                Map<String, Integer> flat = new HashMap<>(bag);   // copy first: bag.get() interns and would mutate the run
                List<String> keys = new ArrayList<>(flat.keySet());
                Collections.sort(keys);
                StringBuilder sb = new StringBuilder();
                for (String k : keys) sb.append(k).append('=').append(flat.get(k)).append(';');
                if (c == 0 && Boolean.getBoolean("jsonbag.dumpTokens")) {
                    for (String k : keys) System.out.println("TOKEN " + cfg.game.name() + " " + n + " " + k + "=" + flat.get(k));
                }
                tokenDigests[c].append(sb).append('\n');
                if (c == 0) stateDigests.append(n).append(':').append(bag.size()).append(':').append(sha(sb.toString())).append(' ');
            }
            Map<String, Integer> trajectory = heuristic.currentJSONBag;
            for (int p = 0; p < prototypes.size(); p++)
                jsd.append(Double.toString(Utils.jensenShannonDistance(prototypes.get(p), trajectory))).append(',');
            for (int player = 0; player < cfg.nPlayers; player++)
                eval.append(Double.toString(heuristic.evaluateState(gs, player))).append(',');

            game.getForwardModel().next(gs, actions.get(rnd.nextInt(actions.size())).copy());
            n++;
        }
        for (int c = 0; c < combos.size(); c++) {
            out.append("TOKENS ").append(cfg.game.name()).append(' ').append(combos.get(c).name()).append(' ')
                    .append(sha(tokenDigests[c].toString())).append('\n');
        }
        out.append("STATES ").append(cfg.game.name()).append(' ').append(sha(stateDigests.toString()))
                .append(' ').append(n).append('\n');
        out.append("JSDVALUE ").append(cfg.game.name()).append(' ').append(jsd).append('\n');
        out.append("EVALVALUE ").append(cfg.game.name()).append(' ').append(eval).append('\n');
    }

    /**
     * The available actions in a fixed order. Several forward models build this list by iterating a hash-based
     * collection, so its order follows the JVM's identity hashes: Dominion's playout diverged at state 2 between a
     * run on its own and a run after the other five games, and again under {@code -XX:hashCode=2}. Picking an index
     * from that order therefore made the fingerprint irreproducible. Sorting by class, text and content hash gives
     * every run the same list, so the seeded pick lands on the same action and two dumps stay comparable. (A game
     * whose actions override neither {@code toString} nor {@code hashCode} with their fields would still not be
     * covered; none of the six paper games is in that position.)
     */
    static List<AbstractAction> deterministic(List<AbstractAction> actions) {
        List<AbstractAction> ordered = new ArrayList<>(actions);
        ordered.sort(Comparator.comparing((AbstractAction a) -> a.getClass().getName() + '|' + a + '|' + a.hashCode()));
        return ordered;
    }

    static String sha(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
