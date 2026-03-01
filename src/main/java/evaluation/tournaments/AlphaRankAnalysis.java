package evaluation.tournaments;

import core.AbstractPlayer;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import utilities.Pair;

import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Analysis implementation that computes AlphaRank values for agents.
 * This class contains a direct extraction of the existing AlphaRank code
 * from RoundRobinTournament.reportAlphaRank - functionality is preserved
 * without amendment.
 */
public class AlphaRankAnalysis implements IResultsAnalysis {

    public final boolean useOrdinal;

    public AlphaRankAnalysis(boolean useOrdinal) {
        this.useOrdinal = useOrdinal;
    }

    @Override
    public LinkedHashMap<String, Pair<Double, Double>> getRanking(TournamentResults results) {
        // Recreate the values matrix depending on mode (ordinal or win)

        List<String> agents = results.getAllAgentNames();

        // alpha-rank calculations
        double alpha = 10.0;
        double[] retValue = new double[agents.size()];
        double[][] T = calculateTransitionMatrix(alpha, results);

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
                    System.arraycopy(pi, 0, retValue, 0, agents.size());
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Error in eigen decomposition - unable to calculate alpha-rank.");
            return new LinkedHashMap<>();
        }

        // Convert returned alpha values into a ranking map preserving agent order
        LinkedHashMap<String, Pair<Double, Double>> ranking = new LinkedHashMap<>();
        for (int i = 0; i < agents.size(); i++) {
            ranking.put(agents.get(i), new Pair<>(retValue[i], 0.0));
        }

        // Sort by alpha value descending (highest first)
        List<Map.Entry<String, Pair<Double, Double>>> sorted = new ArrayList<>(ranking.entrySet());
        sorted.sort((e1, e2) -> Double.compare(e2.getValue().a, e1.getValue().a));
        LinkedHashMap<String, Pair<Double, Double>> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Pair<Double, Double>> e : sorted) sortedMap.put(e.getKey(), e.getValue());
        return sortedMap;
    }


    private double[][] calculateTransitionMatrix(double alpha, TournamentResults results) {
        List<String> agents = results.getAllAgentNames();
        int n = agents.size();
        int[][] values = new int[n][n];
        if (useOrdinal) {
            for (int i = 0; i < agents.size(); i++) {
                for (int j = 0; j < agents.size(); j++) {
                    values[i][j] = results.getOrdinalDelta(agents.get(i), agents.get(j));
                }
            }
        } else {
            for (int i = 0; i < agents.size(); i++) {
                for (int j = 0; j < agents.size(); j++) {
                    values[i][j] = (results.getWins(agents.get(i), agents.get(j)) - results.getWins(agents.get(j), agents.get(i)));
                }
            }
        }
        double[][] T = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    T[i][j] = Math.exp(0);
                } else {
                    double baseValue = values[i][j] / (double) results.getGamesPlayed(agents.get(i), agents.get(j));
                    T[i][j] = Math.exp(-alpha * baseValue);
                }
            }
            // then normalise the row
            double rowSum = Arrays.stream(T[i]).sum();
            for (int j = 0; j < n; j++) {
                T[i][j] /= rowSum;
            }
        }
        return T;
    }

    public String[] calculateClusters(TournamentResults results) {
        List<String> agents = results.getAllAgentNames();

        // alpha-rank calculations
        double alpha = 10.0;
        double[][] T = calculateTransitionMatrix(alpha, results);

        RealMatrix transitionMatrix = MatrixUtils.createRealMatrix(T);
        // B = A^TA + A A^T
        RealMatrix B = transitionMatrix.transpose().multiply(transitionMatrix).add(transitionMatrix.multiply(transitionMatrix.transpose()));
        // This provides useful clustering information
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
                                clusterMembership[i] = agents.get(i);
                                clusterMembership[j] = agents.get(i);
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
        return clusterMembership;
    }
}

