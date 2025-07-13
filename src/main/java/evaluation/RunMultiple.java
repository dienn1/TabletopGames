package evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RunMultiple {
    // TODO parse from args

    private static List<String> getConfigs(String gameName, String[] agentTypes, int paramCount, int seedCount) {
        String dir = "Experiments/" + gameName;
        return new ArrayList<>(){{
            for (String agent : agentTypes) {
                add(dir + "/agents/" + gameName.toLowerCase() + "Run" + agent + ".json");
            }
            for (int i = 1; i <= paramCount; i++) {
                add(dir + "/gameParams/" + gameName.toLowerCase() + "Run" + "Param" + i + ".json");
            }
            if (!gameName.equals("DotsAndBoxes") && !gameName.equals("Connect4")) {
                for (int i = 1; i <= seedCount; i++) {
                    add(dir + "/seeds/" + gameName.toLowerCase() + "Run" + "Seed" + i + ".json");
                }
            }
        }};
    }

    public static void main(String[] args) {
        List<String> gameNames = new ArrayList<>(){{
            add("Wonders7");
//            add("Dominion");
//            add("SeaSaltPaper");
//            add("CantStop");
//            add("Connect4");
//            add("DotsAndBoxes");
        }};
        String[] agentTypes = new String[] {
                "MCTS1",
                "MCTS1Tuned",
                "MCTS2Tuned",
                "OSLA",
                "Random"
        };
        int paramCount = 4;
        int seedCount = 4;
        List<String> configs = new ArrayList<>();
        for (String name : gameNames) {
            configs.addAll(getConfigs(name, agentTypes, paramCount, seedCount));
        }
//        System.out.println(configs);
        long t = System.currentTimeMillis();
        for (String configPath : configs) {
            System.out.println("Running " + configPath);
            RunGames.main(new String[]{"config="+configPath});
            System.out.println("---------------------------------------------------------");
        }
        System.out.println("FISNIHED RUNNING IN " + (System.currentTimeMillis() - t)/1000 + " SECONDS");
    }
}
