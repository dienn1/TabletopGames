package evaluation.tournaments;

import core.AbstractParameters;
import core.AbstractPlayer;
import evaluation.RunArg;
import evaluation.listeners.IGameListener;
import evaluation.listeners.TournamentMetricsGameListener;
import games.GameType;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import players.IAnyTimePlayer;
import utilities.*;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static core.CoreConstants.GameResult;
import static evaluation.tournaments.AbstractTournament.TournamentMode.*;
import static java.lang.Math.sqrt;
import static java.util.stream.Collectors.toList;

public class RoundRobinTournament extends AbstractTournament {
    private static boolean debug = false;
    public TournamentMode tournamentMode;
    final int totalGameBudget;
    int gamesPerMatchup;
    protected List<IGameListener> listeners = new ArrayList<>();
    private boolean verbose;
    public boolean alphaRankDetails = true;
    double[] alphaRankByWin;
    double[] alphaRankByOrdinal;
    protected IResultsAnalysis winRateAnalysis = new WinRateAnalysis();
    protected IResultsAnalysis ordinalAnalysis = new OrdinalAnalysis();
    protected LinkedHashMap<String, Pair<Double, Double>> finalWinRanking; // contains name of agent
    protected LinkedHashMap<String, Pair<Double, Double>> finalOrdinalRanking; // contains name of agent
    LinkedList<Integer> allAgentIds;
    private int totalGamesRun;
    protected boolean randomGameParams;
    public String name;
    public boolean byTeam;
    protected String evalMethod;

    protected long randomSeed;
    List<Integer> gameSeeds = new ArrayList<>();
    int tournamentSeeds;
    String seedFile;
    Random seedRnd;

    /**
     * Create a round robin tournament, which plays all agents against all others.
     *
     * @param agents         - players for the tournament.
     * @param gameToPlay     - game to play in this tournament.
     * @param playersPerGame - number of players per game.
     */
    public RoundRobinTournament(List<? extends AbstractPlayer> agents, GameType gameToPlay, int playersPerGame,
                                AbstractParameters gameParams, Map<RunArg, Object> config) {
        super(agents, gameToPlay, playersPerGame, gameParams);
        Map<String, Integer> nameCounts = new HashMap<>();
        for (AbstractPlayer agent : this.agents) {
            String name = agent.toString();
            nameCounts.put(name, nameCounts.getOrDefault(name, 0) + 1);
            if (nameCounts.get(name) > 1) {
                agent.setName(name + " " + nameCounts.get(name));
            }
        }
        int nTeams = game.getGameState().getNTeams();
        this.verbose = (boolean) config.getOrDefault(RunArg.verbose, false);
        this.tournamentMode = switch (config.get(RunArg.mode).toString().toUpperCase()) {
            case "EXHAUSTIVE" -> EXHAUSTIVE;
            case "EXHAUSTIVESP" -> EXHAUSTIVE_SELF_PLAY;
            case "ONEVSALL" -> ONE_VS_ALL;
            case "FIXED" -> FIXED;
            default -> RANDOM;
        };
        if (tournamentMode == EXHAUSTIVE && nTeams > this.agents.size()) {
            throw new IllegalArgumentException("Not enough agents to fill a match without self-play." +
                    "Either add more agents, reduce the number of players per game, or switch to RANDOM mode.");
        }
        if (tournamentMode == FIXED && this.agents.size() != playersPerGame) {
            throw new IllegalArgumentException("In FIXED mode, the number of agents must match the number of players per game.");
        }
        this.evalMethod = (String) config.getOrDefault(RunArg.evalMethod, "Win");

        this.allAgentIds = new LinkedList<>();
        for (int i = 0; i < this.agents.size(); i++)
            this.allAgentIds.add(i);

        this.totalGameBudget = (int) config.getOrDefault(RunArg.matchups, 100);
        int budget = (int) config.get(RunArg.budget);
        if (budget > 0) {
            // in this case we set the budget of the players
            for (AbstractPlayer player : agents) {
                if (player instanceof IAnyTimePlayer) {
                    ((IAnyTimePlayer) player).setBudget(budget);
                }
            }
        }
        this.byTeam = (boolean) config.getOrDefault(RunArg.byTeam, false);
        this.tournamentSeeds = (int) config.getOrDefault(RunArg.distinctRandomSeeds, 0);
        this.seedFile = (String) config.getOrDefault(RunArg.seedFile, "");
        if (!seedFile.isEmpty()) {
            this.gameSeeds = loadSeedsFromFile();
            if (gameSeeds.isEmpty()) {
                throw new AssertionError("No seeds found in file " + seedFile);
            }
            this.tournamentSeeds = gameSeeds.size();
        }
        int agentPositions = byTeam ? nTeams : playersPerGame;
        int actualGames = this.totalGameBudget;
        switch (tournamentMode) {
            case ONE_VS_ALL:
                gamesPerMatchup = totalGameBudget / agentPositions;
                actualGames = this.gamesPerMatchup * agentPositions;
                break;
            case EXHAUSTIVE_SELF_PLAY:
            case EXHAUSTIVE:
                boolean selfPlay = tournamentMode == EXHAUSTIVE_SELF_PLAY;
                this.gamesPerMatchup = Utils.gamesPerMatchup(agentPositions, agents.size(), totalGameBudget, selfPlay);
                if (this.gamesPerMatchup < 1) {
                    throw new IllegalArgumentException(String.format("Higher budget needed. There are %d permutations of agents to positions in exhaustive mode, which is more than %d game in the available budget.",
                            Utils.playerPermutations(agentPositions, agents.size(), selfPlay), totalGameBudget));
                }
                actualGames = this.gamesPerMatchup * Utils.playerPermutations(agentPositions, agents.size(), selfPlay);
                break;
            case FIXED:
                // we run the totalGameBudget number of games with no change to agent order
            case RANDOM:
                this.gamesPerMatchup = totalGameBudget; // not actually used, we just run the totalGameBudget number of games
                break;
            default:
                throw new IllegalArgumentException("Unknown tournament mode " + config.get(RunArg.mode));
        }
        this.randomSeed = ((Number) config.getOrDefault(RunArg.seed, System.currentTimeMillis())).longValue();
        this.seedRnd = new Random(randomSeed);
        this.randomGameParams = (boolean) config.getOrDefault(RunArg.randomGameParams, false);

        this.name = String.format("Game: %s, Players: %d, Mode: %s, TotalGames: %d, GamesPerMatchup: %d",
                gameToPlay.name(), playersPerGame, tournamentMode, actualGames, gamesPerMatchup);
        System.out.println(name);
        String destDir = (String) config.getOrDefault(RunArg.destDir, "");
        if (!destDir.isEmpty())
            this.resultsFile = destDir + File.separator + resultsFile;
    }

    /**
     * Runs the round robin tournament.
     */
    @Override
    public void run() {
        if (verbose)
            System.out.println("Playing " + game.getGameType().name());

        Set<String> agentNames = agents.stream()
                //           .peek(a -> System.out.println(a.toString()))
                .map(AbstractPlayer::toString).collect(Collectors.toSet());

        for (IGameListener gameTracker : listeners) {
            gameTracker.init(game, nPlayers, agentNames);
            game.addListener(gameTracker);
        }

        LinkedList<Integer> matchUp = new LinkedList<>();
        // add outer loop if we have tournamentSeeds enabled; if not this will just run once
        List<Integer> allSeeds = new ArrayList<>(gameSeeds);
        for (int iter = 0; iter < Math.max(1, tournamentSeeds); iter++) {
            if (tournamentSeeds > 0) {
                // use the same seed for each game in the tournament
                // allSeeds contains the ones loaded from file - if empty then use a random one
                int nextRnd = allSeeds.isEmpty() ? seedRnd.nextInt() : allSeeds.get(iter);
                gameSeeds = IntStream.range(0, gamesPerMatchup).mapToObj(i -> nextRnd).collect(toList());
            } else {
                // use a seed per matchup
                gameSeeds = IntStream.range(0, gamesPerMatchup).mapToObj(i -> seedRnd.nextInt()).collect(toList());
            }
            createAndRunMatchUp(matchUp);
        }
        reportResults();

        for (IGameListener listener : listeners)
            listener.report();
    }

    protected List<Integer> loadSeedsFromFile() {
        // we open seedFile, and read in the comma-delimited list of seeds, and put this in an array
        try {
            Scanner scanner = new Scanner(new File(seedFile)).useDelimiter("\\s*,\\s*");
            List<Integer> seeds = new ArrayList<>();
            while (scanner.hasNextInt()) {
                seeds.add(scanner.nextInt());
            }
            return new ArrayList<>(seeds);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load seeds from file " + seedFile);
        }

    }

    public int getWinnerIndex() {
        LinkedHashMap<String, Pair<Double, Double>> ranking = switch (evalMethod) {
            case "Ordinal", "Score" -> finalOrdinalRanking;
            default -> finalWinRanking;
        };
        if (ranking == null || ranking.isEmpty())
            throw new UnsupportedOperationException("Cannot get winner before results have been calculated");

        // The winner is the first key in finalRanking
        String winnerName = ranking.keySet().iterator().next();
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).toString().equals(winnerName)) return i;
        }
        return -1;
    }

    public AbstractPlayer getWinner() {
        return agents.get(getWinnerIndex());
    }

    /**
     * Recursively creates one combination of players and evaluates it.
     *
     * @param matchUp - current combination of players, updated recursively.
     */
    public void createAndRunMatchUp(List<Integer> matchUp) {

        int nTeams = byTeam ? game.getGameState().getNTeams() : nPlayers;
        if (gameSeeds == null || gameSeeds.isEmpty()) {
            gameSeeds = IntStream.range(0, gamesPerMatchup).mapToObj(i -> seedRnd.nextInt()).collect(toList());
        }
        switch (tournamentMode) {
            case FIXED:
                // we add the agents to the matchUp in the order they are in the list
                // we always run the same fixed set of agents, so ignore the input matchup
                matchUp.clear();
                for (int i = 0; i < agents.size(); i++) {
                    matchUp.add(i);
                }
                evaluateMatchUp(matchUp, gamesPerMatchup, gameSeeds);
                break;
            case RANDOM:
                // In the RANDOM case we use a new seed for each game
                PermutationCycler idStream = new PermutationCycler(agents.size(), seedRnd, nTeams);
                for (int i = 0; i < totalGameBudget; i++) {
                    List<Integer> matchup = new ArrayList<>(nTeams);
                    for (int j = 0; j < nTeams; j++)
                        matchup.add(idStream.getAsInt());
                    evaluateMatchUp(matchup, 1, Collections.singletonList(gameSeeds.get(i)));
                }
                break;
            case ONE_VS_ALL:
                // In this case agents.get(0) must always play
                List<Integer> agentOrder = new ArrayList<>(this.allAgentIds);
                agentOrder.remove(Integer.valueOf(0));
                for (int p = 0; p < nTeams; p++) {
                    // we put the focus player at each position (p) in turn
                    if (agentOrder.size() == 1) {
                        // to reduce variance in this case we can use the same set of seeds for each case
                        List<Integer> matchup = new ArrayList<>(nTeams);
                        for (int j = 0; j < nTeams; j++) {
                            if (j == p)
                                matchup.add(0); // focus player
                            else {
                                matchup.add(agentOrder.get(0));
                            }
                        }
                        // We split the total budget equally across the possible positions the focus player can be in
                        // We will therefore use the first chunk of gameSeeds only (but use the same gameSeeds for each position)
                        evaluateMatchUp(matchup, totalGameBudget / nTeams, gameSeeds);
                    } else {
                        for (int m = 0; m < this.totalGameBudget / nTeams; m++) {
                            Collections.shuffle(agentOrder, seedRnd);
                            List<Integer> matchup = new ArrayList<>(nTeams);
                            for (int j = 0; j < nTeams; j++) {
                                if (j == p)
                                    matchup.add(0); // focus player
                                else {
                                    matchup.add(agentOrder.get(j % agentOrder.size()));
                                }
                            }
                            evaluateMatchUp(matchup, 1, Collections.singletonList(gameSeeds.get(m)));
                        }
                    }
                }
                break;
            case EXHAUSTIVE:
            case EXHAUSTIVE_SELF_PLAY:
                // in this case we are in exhaustive mode, so we recursively construct all possible combinations of players
                if (matchUp.size() == nTeams) {
                    evaluateMatchUp(matchUp, gamesPerMatchup, gameSeeds);
                } else {
                    for (Integer agentID : this.allAgentIds) {
                        if (tournamentMode == EXHAUSTIVE_SELF_PLAY || !matchUp.contains(agentID)) {
                            matchUp.add(agentID);
                            createAndRunMatchUp(matchUp);
                            matchUp.remove(agentID);
                        }
                    }
                }
        }
    }

    /**
     * Evaluates one combination of players.
     *
     * @param agentIDsInThisGame - IDs of agents participating in this run.
     */
    protected void evaluateMatchUp(List<Integer> agentIDsInThisGame, int nGames, List<Integer> seeds) {
        if (seeds.size() < nGames)
            throw new AssertionError("Not enough seeds for the number of games requested");
        if (debug)
            System.out.printf("Evaluate %s at %tT%n", agentIDsInThisGame.toString(), System.currentTimeMillis());
        LinkedList<AbstractPlayer> matchUpPlayers = new LinkedList<>();

        // create a copy of the player to avoid them sharing the same state
        for (int agentID : agentIDsInThisGame)
            matchUpPlayers.add(this.agents.get(agentID).copy());

        if (verbose) {
            StringBuffer sb = new StringBuffer();
            sb.append("[");
            for (int agentID : agentIDsInThisGame)
                sb.append(this.agents.get(agentID).toString()).append(",");
            sb.setCharAt(sb.length() - 1, ']');
            System.out.println(sb);
        }

        // TODO : Not sure this is the ideal place for this...ask Raluca
        Set<String> agentNames = agents.stream().map(AbstractPlayer::toString).collect(Collectors.toSet());
        for (IGameListener listener : listeners) {
            if (listener instanceof TournamentMetricsGameListener) {
                ((TournamentMetricsGameListener) listener).tournamentInit(game, nPlayers, agentNames, new HashSet<>(matchUpPlayers));
            }
        }

        // Run the game N = gamesPerMatchUp times with these players
        for (int i = 0; i < nGames; i++) {
            // if tournamentSeeds > 0, then we are running this many tournaments, each with a different random seed fixed for the whole tournament
            // so we override the standard random seeds
            game.reset(matchUpPlayers, seeds.get(i));

            // Randomize parameters
            if (randomGameParams) {
                game.getGameState().getGameParameters().randomize();
                System.out.println("Game parameters: " + game.getGameState().getGameParameters());
            }

            game.run();  // Always running tournaments without visuals
            GameResult[] results = game.getGameState().getPlayerResults();

            tournamentResults.record(game);

            if (verbose) {
                StringBuffer sb = new StringBuffer();
                sb.append("[");
                for (int j = 0; j < matchUpPlayers.size(); j++) {
                    for (int player = 0; player < game.getGameState().getNPlayers(); player++) {
                        if (game.getGameState().getTeam(player) == j) {
                            sb.append(results[player]).append(",");
                            break; // we stop after one player on the team to avoid double counting
                        }
                    }
                }
                sb.setCharAt(sb.length() - 1, ']');
                System.out.println(sb);
            }

        }
        totalGamesRun += nGames;
    }

    protected void calculateFinalResults() {
        finalWinRanking = winRateAnalysis.getRanking(tournamentResults);
        finalOrdinalRanking = ordinalAnalysis.getRanking(tournamentResults);
    }

    protected void reportResults() {
        calculateFinalResults();
        boolean toFile = resultsFile != null && !resultsFile.isEmpty();
        List<String> dataDump = new ArrayList<>();
        dataDump.add(name + "\n");

        if (agents.size() > game.getGameState().getNPlayers()) {
            // We only calculate alpha-rank if we have more agents than players
            // otherwise the Transition matrix is singular
            int[][] ordinalDelta = new int[agents.size()][agents.size()];
            for (int i = 0; i < agents.size(); i++) {
                for (int j = 0; j < agents.size(); j++) {
                    ordinalDelta[i][j] = tournamentResults.getOrdinalDelta(agents.get(i).toString(), agents.get(j).toString());
                }
            }
            alphaRankByOrdinal = reportAlphaRank(dataDump, ordinalDelta);
            dataDump.add("Alpha calculations using Win Rate\n");
            if (verbose)
                System.out.println("Alpha calculations using Win Rate");
            int[][] symmetrisedWins = new int[agents.size()][agents.size()];
            for (int i = 0; i < agents.size(); i++) {
                for (int j = 0; j < agents.size(); j++) {
                    symmetrisedWins[i][j] = (tournamentResults.getWins(agents.get(i).toString(), agents.get(j).toString()) - tournamentResults.getWins(agents.get(j).toString(), agents.get(i).toString()));
                }
            }
            alphaRankByWin = reportAlphaRank(dataDump, symmetrisedWins);
        }

        // To console
        if (verbose)
            System.out.printf("============= %s - %d games played ============= \n", game.getGameType().name(), totalGamesRun);
        for (int i = 0; i < this.agents.size(); i++) {
            String name = agents.get(i).toString();
            List<TournamentResults.Result> agentResults = tournamentResults.getPlayerResults(name);
            double totalPoints = agentResults.stream().mapToDouble(r -> r.points).sum();
            int totalWins = agentResults.stream().mapToInt(r -> r.win).sum();
            int nGames = agentResults.size();
            double totalScore = agentResults.stream().mapToDouble(r -> r.score).sum();

            String str = String.format("%s got %.2f points. ", name, totalPoints);
            if (toFile) dataDump.add(str);
            if (verbose) System.out.print(str);

            str = String.format("%s won %.1f%% of the %d games of the tournament. ",
                    name, 100.0 * totalWins / totalGamesRun, totalGamesRun);
            if (toFile) dataDump.add(str);
            if (verbose) System.out.print(str);

            str = String.format("%s won %.1f%% of the %d games it played during the tournament.\n",
                    name, 100.0 * totalWins / nGames, nGames);
            if (toFile) dataDump.add(str);
            if (verbose) System.out.print(str);
            str = String.format("%s got a mean score of %.2f.\n", name, totalScore / nGames);
            if (toFile) dataDump.add(str);
            if (verbose) System.out.print(str);


            for (int j = 0; j < this.agents.size(); j++) {
                if (i != j) {
                    int gamesPlayed = tournamentResults.getGamesPlayed(agents.get(i).toString(), agents.get(j).toString());
                    str = String.format("%s won %.1f%% of the %d games against %s.\n",
                            agents.get(i), 100.0 * tournamentResults.getWins(agents.get(i).toString(), agents.get(j).toString()) / gamesPlayed, gamesPlayed, agents.get(j));
                    if (toFile) dataDump.add(str);
                    if (verbose) System.out.print(str);
                }
            }

            if (toFile) dataDump.add("\n");
            if (verbose) System.out.println();
        }

        String str = "---- Ranking ---- (+/- are standard errors on the mean calculated using a Normal approximation) \n";
        if (toFile) dataDump.add(str);
        if (verbose) System.out.print(str);

        for (String agentName : finalWinRanking.keySet()) {
            str = String.format("%s: Win rate %.2f +/- %.3f\tMean Ordinal %.2f +/- %.2f\n",
                    agentName,
                    finalWinRanking.get(agentName).a, finalWinRanking.get(agentName).b,
                    finalOrdinalRanking.get(agentName).a, finalOrdinalRanking.get(agentName).b);
            if (toFile) dataDump.add(str);
            if (verbose) System.out.print(str);
        }

        if (agents.size() > game.getGameState().getNPlayers()) {
            // now report alpha-rank as long as we have more agents than players
            // otherwise the Transition matrix is singular and we get no additional information
            // compared the the simple win rates
            if (alphaRankByWin != null) {
                str = "\nAlpha-rank by Win Rate\n";
                if (toFile) dataDump.add(str);
                if (verbose) System.out.print(str);
                List<Pair<String, Double>> sortedAlphaRank = IntStream.range(0, agents.size())
                        .mapToObj(i -> new Pair<>(agents.get(i).toString(), alphaRankByWin[i]))
                        .sorted((o1, o2) -> o2.b.compareTo(o1.b))
                        .toList();
                for (Pair<String, Double> pair : sortedAlphaRank) {
                    str = String.format("\t%-30s\t%.2f\n", pair.a, pair.b);
                    if (toFile) dataDump.add(str);
                    if (verbose) System.out.print(str);
                }
            }
            if (alphaRankByOrdinal != null) {
                // and then by ordinal
                str = "\nAlpha-rank by Ordinal Position\n";
                if (toFile) dataDump.add(str);
                if (verbose) System.out.print(str);
                List<Pair<String, Double>> sortedAlphaRank = IntStream.range(0, agents.size())
                        .mapToObj(i -> new Pair<>(agents.get(i).toString(), alphaRankByOrdinal[i]))
                        .sorted((o1, o2) -> o2.b.compareTo(o1.b))
                        .toList();
                for (Pair<String, Double> pair : sortedAlphaRank) {
                    str = String.format("\t%-30s\t%.2f\n", pair.a, pair.b);
                    if (toFile) dataDump.add(str);
                    if (verbose) System.out.print(str);
                }
            }
        }
        // To file
        if (toFile) {
            try {
                File resultsFile = new File(this.resultsFile);
                if (!resultsFile.exists()) {
                    File dir = resultsFile.getParentFile();
                    if (dir != null && !dir.exists())
                        dir.mkdirs();
                }
                FileWriter writer = new FileWriter(resultsFile, true);
                for (String line : dataDump)
                    writer.write(line);
                writer.write("\n");
                writer.close();
            } catch (Exception e) {
                System.out.println("Unable to write results to " + resultsFile);
                resultsFile = null;
            }
        }
    }

    protected double[] reportAlphaRank(List<String> dataDump, int[][] values) {
        // alpha-rank calculations
        double[] alphaValues = new double[]{10.0};
        double[] retValue = new double[agents.size()];
        for (double alpha : alphaValues) {
            // T is our transition matrix
            double[][] T = new double[agents.size()][agents.size()];
            for (int i = 0; i < agents.size(); i++) {
                for (int j = 0; j < agents.size(); j++) {
                    if (i == j) {
                        T[i][j] = Math.exp(0);
                    } else {
                        double baseValue = values[i][j] / (double) tournamentResults.getGamesPlayed(agents.get(i).toString(), agents.get(j).toString());
                        T[i][j] = Math.exp(-alpha * baseValue);
                    }
                }
                // then normalise the row
                double rowSum = Arrays.stream(T[i]).sum();
                for (int j = 0; j < agents.size(); j++) {
                    T[i][j] /= rowSum;
                }
            }
            RealMatrix transitionMatrix = MatrixUtils.createRealMatrix(T);

            // We now find the stationary distribution of this transition matrix
            // i.e. the pi for which T^T pi = pi
            try {
                EigenDecomposition eig = new EigenDecomposition(transitionMatrix.transpose());
                double[] eigenValues = eig.getRealEigenvalues();
                // we now expect one of these to have a value of +1.0
                for (int eigenIndex = 0; eigenIndex < eigenValues.length; eigenIndex++) {
                    if (Math.abs(eigenValues[eigenIndex] - 1.0) < 1e-6) {
                        // we have found the eigenvector we want
                        double[] pi = eig.getEigenvector(eigenIndex).toArray();
                        // normalise pi
                        double piSum = Arrays.stream(pi).sum();
                        for (int i = 0; i < agents.size(); i++) {
                            pi[i] /= piSum;
                        }
                        String str = "Alpha: " + alpha;
                        dataDump.add(str + "\n");
                        if (verbose) System.out.println(str);
                        for (int i = 0; i < agents.size(); i++) {
                            retValue[i] = pi[i];
                            str = String.format("\t%.3f\t%s%n", pi[i], agents.get(i));
                            dataDump.add(str);
                            if (verbose) System.out.print(str);
                        }
                        dataDump.add("\n");
                        if (verbose) System.out.println();
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error in eigen decomposition - unable to calculate alpha-rank.");
                return null;
            }

            // print the transition matrix
            if (alphaRankDetails) {
                String str = "Transition matrix for alpha = " + alpha;
                dataDump.add(str + "\n");
                if (verbose) System.out.println(str);
                for (int i = 0; i < agents.size(); i++) {
                    for (int j = 0; j < agents.size(); j++) {
                        str = String.format("%.3f\t", T[i][j]);
                        dataDump.add(str);
                        if (verbose) System.out.print(str);
                    }
                    dataDump.add("\n");
                    if (verbose) System.out.println();
                }
            }

            // B = A^TA + A A^T
            RealMatrix B = transitionMatrix.transpose().multiply(transitionMatrix).add(transitionMatrix.multiply(transitionMatrix.transpose()));
            // This provides useful clustering information

            // print the B matrix
            if (alphaRankDetails) {
                String str = "B matrix for alpha = " + alpha;
                dataDump.add(str + "\n");
                if (verbose) System.out.println(str);
                for (int i = 0; i < agents.size(); i++) {
                    for (int j = 0; j < agents.size(); j++) {
                        str = String.format("%.3f\t", B.getEntry(i, j));
                        dataDump.add(str);
                        if (verbose) System.out.print(str);
                    }
                    dataDump.add("\n");
                    if (verbose) System.out.println();
                }
            }

            // Now we cluster based on the bibliometrically symmetrised matrix B
            double thresholdForCluster = 0.15 * sqrt(agents.size());
            String[] clusterMembership = new String[agents.size()];
            for (int i = 0; i < agents.size(); i++) {
                for (int j = i; j < agents.size(); j++) {
                    if (i != j) {
                        // we look at the Euclidean distance between the two rows
                        double distance = 0.0;
                        for (int k = 0; k < agents.size(); k++) {
                            distance += (B.getEntry(i, k) - B.getEntry(j, k)) * (B.getEntry(i, k) - B.getEntry(j, k));
                        }
                        distance = sqrt(distance);
                        if (distance < thresholdForCluster) {
                            if (clusterMembership[i] == null) {
                                if (clusterMembership[j] == null) {
                                    // neither in cluster, so new cluster
                                    clusterMembership[i] = agents.get(i).toString();
                                    clusterMembership[j] = agents.get(i).toString();
                                } else {
                                    // j is in a cluster, so i joins it
                                    clusterMembership[i] = clusterMembership[j];
                                }
                            } else {
                                if (clusterMembership[j] == null) {
                                    // i is in a cluster, so j joins it
                                    clusterMembership[j] = clusterMembership[i];
                                } else {
                                    // both in clusters, so merge
                                    String cluster = clusterMembership[i];
                                    for (int k = 0; k < agents.size(); k++) {
                                        if (clusterMembership[k] != null && clusterMembership[k].equals(clusterMembership[j])) {
                                            clusterMembership[k] = cluster;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // print the cluster membership
            if (alphaRankDetails && Arrays.stream(clusterMembership).anyMatch(Objects::nonNull)) {
                String str = "The following agents cluster together, and may be considered equivalent: ";
                dataDump.add(str + "\n");
                if (verbose) System.out.println(str);
                for (int i = 0; i < agents.size(); i++) {
                    // get all agents in this cluster
                    boolean clusterExists = false;
                    for (int j = 0; j < agents.size(); j++) {
                        if (clusterMembership[j] != null && clusterMembership[j].equals(agents.get(i).toString())) {
                            if (!clusterExists) {
                                str = String.format("\tCluster for %s%n", agents.get(i));
                                dataDump.add(str);
                                if (verbose) System.out.print(str);
                            }
                            clusterExists = true;
                            str = String.format("\t\t%s%n", agents.get(j));
                            dataDump.add(str);
                            if (verbose) System.out.print(str);
                        }
                    }
                }
                dataDump.add("\n");
                if (verbose) System.out.println();
            }
        }
        return retValue;
    }

    public double getWinRate(int agentID) {
        String name = agents.get(agentID).toString();
        return finalWinRanking.get(name) == null ? 0.0 : finalWinRanking.get(name).a;
    }

    public double getWinRateAlphaRank(int agentID) {
        if (alphaRankByWin == null)
            return getWinRate(agentID);
        return alphaRankByWin[agentID];
    }

    public int getAlphaRankWinnerByWinRate() {
        if (alphaRankByWin == null)
            return -1;
        return getBestAgentInArray(alphaRankByWin);
    }

    public int getAlphaRankWinnerByOrdinal() {
        if (alphaRankByOrdinal == null)
            return -1;
        return getBestAgentInArray(alphaRankByOrdinal);
    }

    private int getBestAgentInArray(double[] arrayIndexedByAgent) {
        int winnerIndex = 0;
        double max = arrayIndexedByAgent[0];
        for (int i = 1; i < arrayIndexedByAgent.length; i++) {
            if (arrayIndexedByAgent[i] > max) {
                max = arrayIndexedByAgent[i];
                winnerIndex = i;
            }
        }
        return winnerIndex;
    }

    public double getWinStdErr(int agentID) {
        String name = agents.get(agentID).toString();
        return finalWinRanking.get(name) == null ? 0.0 : finalWinRanking.get(name).b;
    }

    public double getSumOfSquares(int agentID, String type) {
        String name = agents.get(agentID).toString();
        List<TournamentResults.Result> agentResults = tournamentResults.getPlayerResults(name);
        if (type.equals("Win")) {
            return agentResults.stream().mapToDouble(r -> r.points * r.points).sum();
        } else {
            return agentResults.stream().mapToDouble(r -> (double) r.ordinal * r.ordinal).sum();
        }
    }

    public double getOrdinalRank(int agentID) {
        String name = agents.get(agentID).toString();
        return finalOrdinalRanking.get(name).a;
    }

    public double getOrdinalAlphaRank(int agentID) {
        if (alphaRankByOrdinal == null)
            return -getOrdinalRank(agentID);
        return alphaRankByOrdinal[agentID];
    }

    public double getOrdinalStdErr(int agentID) {
        String name = agents.get(agentID).toString();
        return finalOrdinalRanking.get(name).b;
    }

    public void addListener(IGameListener gameTracker) {
        listeners.add(gameTracker);
    }

    public int getNumberOfAgents() {
        return agents.size();
    }

    public int[] getNGamesPlayed() {
        int[] retValue = new int[agents.size()];
        for (int i = 0; i < agents.size(); i++) {
            retValue[i] = tournamentResults.getPlayerResults(agents.get(i).toString()).size();
        }
        return retValue;
    }
}
