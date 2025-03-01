package evaluation;

import java.util.ArrayList;
import java.util.List;

public class RunMultiple {
    // TODO parse from args

    private static List<String> getConfigs(String gameName, String[] agentTypes, int paramCount, int seedCount) {
        String dir = "Experiments/" + gameName;
        return new ArrayList<>(){{
//            for (String agent : agentTypes) {
//                add(dir + "/agents/" + gameName.toLowerCase() + "Run" + agent + ".json");
//            }
            for (int i = 1; i <= paramCount; i++) {
                add(dir + "/gameParams/" + gameName.toLowerCase() + "Run" + "Param" + i + ".json");
            }
//            for (int i = 1; i <= seedCount; i++) {
//                add(dir + "/seeds/" + gameName.toLowerCase() + "Run" + "Seed" + i + ".json");
//            }
        }};
    }

    public static void main(String[] args) {
        String gameName = "SeaSaltPaper";
        String[] agentTypes = new String[] {
                "MCTS",
                "MCTS2",
                "MCTS2Tuned",
                "OSLA",
                "Random"
        };
        int paramCount = 4;
        int seedCount = 4;
        List<String> configs = getConfigs(gameName, agentTypes, paramCount, seedCount);
        long t = System.currentTimeMillis();
        for (String configPath : configs) {
            RunGames.main(new String[]{"config="+configPath});
        }
        System.out.println("FISNIHED RUNNING IN " + (System.currentTimeMillis() - t)/1000 + " SECONDS");
    }
}
