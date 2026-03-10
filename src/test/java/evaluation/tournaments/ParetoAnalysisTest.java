package evaluation.tournaments;

import core.AbstractPlayer;
import org.junit.Test;
import players.simple.RandomPlayer;
import utilities.Pair;

import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class ParetoAnalysisTest {

    @Test
    public void testParetoFronts() {
        TournamentResults tr = new TournamentResults();

        AbstractPlayer A = new RandomPlayer();
        A.setName("A");
        AbstractPlayer B = new RandomPlayer();
        B.setName("B");
        AbstractPlayer C = new RandomPlayer();
        C.setName("C");
        AbstractPlayer D = new RandomPlayer();
        D.setName("D");

        // Ensure agents are registered
        tr.registerAgent(A);
        tr.registerAgent(B);
        tr.registerAgent(C);
        tr.registerAgent(D);

        // A beats everyone (B,C,D)
        recordHeadToHead(tr, A, B);
        recordHeadToHead(tr, A, C);
        recordHeadToHead(tr, A, D);

        // Cycle among B, C, D: B beats C, C beats D, D beats B
        recordHeadToHead(tr, B, C);
        recordHeadToHead(tr, C, D);
        recordHeadToHead(tr, D, B);

        ParetoAnalysis pa = new ParetoAnalysis();
        LinkedHashMap<String, Pair<Double, Double>> ranking = pa.getRanking(tr);

        // We should have 4 agents ranked
        assertEquals("Expected 4 agents in ranking", 4, ranking.size());

        // Agent A should be the only one on the first Pareto front (rank == 1.0)
        int firstFrontCount = 0;
        for (String agent : ranking.keySet()) {
            Pair<Double, Double> value = ranking.get(agent);
            if (Double.compare(value.a, 1.0) == 0) firstFrontCount++;
        }
        assertEquals("Only A should be on first Pareto front", 1, firstFrontCount);
        assertTrue("A should be present in ranking", ranking.containsKey("A"));
        assertEquals(1.0, ranking.get("A").a, 0.0);

        // The other three (B, C, D) should all be on the second Pareto front (rank == 2.0)
        for (String agent : new String[]{"B", "C", "D"}) {
            assertTrue("Agent " + agent + " should be present", ranking.containsKey(agent));
            assertEquals("Agent " + agent + " should be on second front", 2.0, ranking.get(agent).a, 0.0);
        }
    }

    // Helper to record a single head-to-head game where 'winner' beats 'loser'
    private void recordHeadToHead(TournamentResults tr, AbstractPlayer winner, AbstractPlayer loser) {
        tr.addResult(winner, 1.0, 1, 0.0, 1);
        tr.addResult(loser, 0.0, 2, 0.0, 0);
        tr.updateGamePlayed(winner, loser);
        tr.updateGamePlayed(loser, winner);
        tr.updateWins(winner, loser, 1);
    }
}
