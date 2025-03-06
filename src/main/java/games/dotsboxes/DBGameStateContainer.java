package games.dotsboxes;

import core.AbstractGameState;
import core.AbstractGameStateContainer;

import java.util.HashMap;
import java.util.HashSet;

public class DBGameStateContainer extends AbstractGameStateContainer {
    // List of all edges possible
    HashSet<DBEdge> edges;
    // List of all cells possible
    HashSet<DBCell> cells;
    // Mapping from each edge to the cells it neighbours
    HashMap<DBEdge, HashSet<DBCell>> edgeToCellMap;
    // Mapping from each cell to its edges
    HashMap<DBCell, HashSet<DBEdge>> cellToEdgesMap;

    // Mutable state:
    int[] nCellsPerPlayer;
    HashMap<DBCell, Integer> cellToOwnerMap;  // Mapping from each cell to its owner, if complete
    HashMap<DBEdge, Integer> edgeToOwnerMap;  // Mapping from each edge to its owner, if placed
    protected DBGameStateContainer(DBGameState gs) {
        super(gs);
        edges = gs.edges;
        cells = gs.cells;
        edgeToCellMap = gs.edgeToCellMap;
        cellToEdgesMap = gs.cellToEdgesMap;
        nCellsPerPlayer = gs.nCellsPerPlayer;
        cellToOwnerMap = gs.cellToOwnerMap;  // Mapping from each cell to its owner, if complete
        edgeToOwnerMap = gs.edgeToOwnerMap;  // Mapping from each edge to its owner, if placed
    }
}
