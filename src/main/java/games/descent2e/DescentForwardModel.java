package games.descent2e;

import core.AbstractForwardModel;
import core.AbstractGameState;
import core.CoreConstants;
import core.StandardForwardModelWithTurnOrder;
import core.actions.AbstractAction;
import core.components.*;
import core.properties.*;
import games.descent2e.actions.*;
import games.descent2e.actions.attack.MeleeAttack;
import games.descent2e.actions.Move;
import games.descent2e.actions.attack.SurgeAttackAction;
import games.descent2e.actions.tokens.TokenAction;
import games.descent2e.components.*;
import games.descent2e.components.tokens.DToken;
import games.descent2e.concepts.DescentReward;
import games.descent2e.concepts.GameOverCondition;
import games.descent2e.concepts.Quest;
import utilities.LineOfSight;
import utilities.Pair;
import utilities.Utils;
import utilities.Vector2D;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import games.descent2e.DescentTypes.*;

import static core.CoreConstants.*;
import static games.descent2e.DescentConstants.*;
import static games.descent2e.components.DicePool.constructDicePool;
import static utilities.Utils.getNeighbourhood;

public class DescentForwardModel extends StandardForwardModelWithTurnOrder {

    @Override
    protected void _setup(AbstractGameState firstState) {
        DescentGameState dgs = (DescentGameState) firstState;
        DescentParameters descentParameters = (DescentParameters) firstState.getGameParameters();
        int nActionsPerFigure = descentParameters.nActionsPerFigure;
        dgs.data.load(descentParameters.getDataPath());
        dgs.initData = false;
        dgs.addAllComponents();
        DescentGameData _data = dgs.getData();

        // Set up revive dice pool: 2 red dice
        DicePool.revive = constructDicePool(descentParameters.reviveDice);

        // TODO: epic play options (pg 19)

        // Get campaign from game parameters, load all the necessary information
        Campaign campaign = ((DescentParameters)dgs.getGameParameters()).campaign;
        campaign.load(_data, descentParameters.dataPath);
        // TODO: Separate shop items (+shuffle), monster and lieutenent cards into 2 acts.

        Quest firstQuest = campaign.getQuests()[0];
        dgs.currentQuest = firstQuest;
        String firstBoard = firstQuest.getBoards().get(0);

        // Set up first board of first quest
        setupBoard(dgs, _data, firstBoard);

        // Overlord setup
        dgs.overlordPlayer = 0;  // First player is always the overlord
        // Overlord will also have a figure, but not on the board (to store xp and skill info)
        dgs.overlord = new Figure("Overlord", -1);
        // TODO: read the following from quest setup
        dgs.overlord.setAttribute(Figure.Attribute.Fatigue, new Counter(0, 0, 7, "Overlord Fatigue"));
        dgs.overlord.setTokenType("Overlord");
        // Overlord is player 0, first hero is player 1
        dgs.getTurnOrder().setStartingPlayer(1);

        // TODO: Shuffle overlord deck and give overlord nPlayers cards.

        // TODO: is this quest phase or campaign phase?

        // TODO: Let players choose these, for now randomly assigned
        // 5. Player setup phase interrupts, after which setup continues:
        // Players choose heroes & class

        ArrayList<Vector2D> heroStartingPositions = firstQuest.getStartingLocations().get(firstBoard);
        ArrayList<Integer> archetypes = new ArrayList<>();
        for (int i = 0; i < Archetype.values().length; i++) {
            archetypes.add(i);
        }
        Random rnd = new Random(firstState.getGameParameters().getRandomSeed());
        dgs.heroes = new ArrayList<>();
        for (int i = 1; i < Math.max(3, dgs.getNPlayers()); i++) {
            // Choose random archetype from those remaining
            int choice = archetypes.get(rnd.nextInt(archetypes.size()));
            archetypes.remove(Integer.valueOf(choice));  // TODO this should be commented in, but kept out for testing until it's guaranteed that archetypes >= nHeroes
            Archetype archetype = Archetype.values()[choice];

            // Choose random hero from that archetype
            List<Hero> heroes = _data.findHeroes(archetype);
            Hero figure = heroes.get(rnd.nextInt(heroes.size())).copyNewID();
            figure.getNActionsExecuted().setMaximum(nActionsPerFigure);
            figure.setComponentName("Hero: " + figure.getComponentName());  // For reference in rules

            if (dgs.getNPlayers() == 2) {
                // In 2-player games, 1 player controls overlord, the other 2 heroes
                figure.setOwnerId(1-dgs.overlordPlayer);
            } else {
                figure.setOwnerId(i);
            }

            // Choose random class from that archetype
            HeroClass[] options = HeroClass.getClassesForArchetype(archetype);
            choice = rnd.nextInt(options.length);
            HeroClass heroClass = options[choice];

            // Inform figure of chosen class
            figure.setProperty(new PropertyString("class", heroClass.name()));

            // Assign starting skills and equipment from chosen class
            Deck<Card> classDeck = _data.findDeck(heroClass.name());
            for (Card c: classDeck.getComponents()) {
                if (((PropertyInt)c.getProperty(xpHash)).value <= figure.getAttribute(Figure.Attribute.XP).getValue()) {
                    figure.equip(new DescentCard(c));
                }
            }
            // after equipping, set up abilities
            figure.getWeapons().stream().flatMap(w -> w.getWeaponSurges().stream())
                    .forEach(s -> figure.addAbility(new SurgeAttackAction(s, figure.getComponentID())));

            // Place hero on the board in random starting position out of those available
            choice = rnd.nextInt(heroStartingPositions.size());
            Vector2D position = heroStartingPositions.get(choice);
            figure.setPosition(position);

            // Tell the board there's a hero there
            PropertyInt prop = new PropertyInt("players", figure.getComponentID());
            dgs.masterBoard.getElement(position.getX(), position.getY()).setProperty(prop);

            // This starting position no longer an option (one hero per space)
            heroStartingPositions.remove(choice);

            // Inform game of this hero figure
            dgs.heroes.add(figure);
        }

        // Overlord chooses monster groups // TODO, for now randomly selected
        // Create and place monsters
        createMonsters(dgs, firstQuest, _data, rnd, nActionsPerFigure);

        // Set up tokens
        Random r = new Random(dgs.getGameParameters().getRandomSeed());
        dgs.tokens = new ArrayList<>();
        for (DToken.DTokenDef def: firstQuest.getTokens()) {
            int n = (def.getSetupHowMany().equalsIgnoreCase("nHeroes")? dgs.getNPlayers()-1 : Integer.parseInt(def.getSetupHowMany()));
            // Find position, if only 1 value for all this is a tile where they have to go, pick random locations
            // TODO let overlord pick locations if not fixed
            String tileName = null;
            if (def.getLocations().length == 1) tileName = def.getLocations()[0];
            for (int i = 0; i < n; i++) {
                Vector2D location = null;
                if (tileName == null) {
                    // Find fixed location
                    String[] split = def.getLocations()[i].split("-");
                    tileName = split[0];
                    String[] splitPos = split[1].split(";");
                    Vector2D locOnTile = new Vector2D(Integer.parseInt(splitPos[0]), Integer.parseInt(splitPos[1]));
                    Map<Vector2D, Vector2D> map = dgs.gridReferences.get(tileName);
                    for (Map.Entry<Vector2D, Vector2D> e: map.entrySet()) {
                        if (e.getValue().equals(locOnTile)) {
                            location = e.getKey();
                            break;
                        }
                    }
                    tileName = null;
                } else if (!tileName.equalsIgnoreCase("player")) {
                    // Find random location on tile
                    int idx = r.nextInt(dgs.gridReferences.get(tileName).size());
                    int k = 0;
                    for (Vector2D key: dgs.gridReferences.get(tileName).keySet()) {
                        if (k == idx) {
                            location = key; break;
                        }
                        k++;
                    }
                } else {
                    // A player should hold these tokens, not on the board, location is left null
                }
                DToken token = new DToken(def.getTokenType(), location);
                token.setEffects(def.getEffectsCopy());
                for (TokenAction ta: token.getEffects()) {
                    ta.setTokenID(token.getComponentID());
                }
                token.setAttributeModifiers(def.getAttributeModifiers());
                if (location == null) {
                    // Make a hero owner of it TODO: players choose?
                    int idx = r.nextInt(dgs.getNPlayers()-1);
                    if (idx == dgs.overlordPlayer) idx++;
                    token.setOwnerId(idx, dgs);
                }
                dgs.tokens.add(token);
            }
        }

        // Set up dice!
        dgs.attributeDicePool = new DicePool(Collections.emptyList());
        dgs.attackDicePool = new DicePool(Collections.emptyList());
        dgs.defenceDicePool = new DicePool(Collections.emptyList());

        // Shuffle search cards deck
        dgs.searchCards = _data.searchCards;
        dgs.searchCards.shuffle(r);

        // Ready to start playing!
    }

    @Override
    protected void _afterAction(AbstractGameState currentState, AbstractAction action) {
        DescentGameState dgs = (DescentGameState) currentState;
        Figure actingFigure = dgs.getActingFigure();

        if (checkEndOfGame(dgs)) return;  // TODO: this should be more efficient, and work with triggers so they're not checked after each small action, but only after actions that can actually trigger them

        // TODO: may still be able to play cards/skills/free effects - just add more Booleans into the if check when more are added
        // Turn ends for figure if they executed the number of actions available,
        Boolean noMoreActions = actingFigure.getNActionsExecuted().isMaximum();
        // have no more movement points available,
        Boolean noMoreMovement = actingFigure.getAttribute(Figure.Attribute.MovePoints).isMinimum();
        // and, for Heroes only, cannot spend any more Fatigue for movement points
        Boolean noMoreFatigueMovement = true;
        if (actingFigure instanceof Hero) {
            if (!actingFigure.getAttribute(Figure.Attribute.Fatigue).isMaximum()) {
                noMoreFatigueMovement = false;
            }
        }

        if (!(action instanceof EndTurn) && noMoreActions && noMoreMovement && noMoreFatigueMovement) {
            dgs.getTurnOrder().endPlayerTurn(dgs);
        }

        /*
        Hero turn:
        1. Start of turn:
            - start of turn abilities
            - refresh cards
            - test attributes for conditions
        2. Take 2 actions

        Overlord turn: TODO: current turn order alternates 1 monster group, 1 hero player etc.
        1. Start of turn:
            - Start of turn abilities
            - Draw 1 Overlord card
            - Refresh cards
        2. Activate monsters:
            - Choose monster group
            - On-activation group effects
            - Choose unactivated monster in group
            - On-activation effects
            - Perform 2 actions with the monster
            - End of monster activation effects
            - End of monster group activation effects
            - Repeat steps for each remaining monster group
        3. End of turn abilities
         */

        // Any figure that ends its turn in a lava space is immediately defeated.
        // Heroes that are defeated in this way place their hero token in the nearest empty space
        // (from where they were defeated) that does not contain lava. A large monster is immediately defeated only
        // if all spaces it occupies are lava spaces.

        // TODO

        // Quest finished -> Campaign phase
        // Set up campaign phase
        // receive gold from search cards and return cards to the deck.
        // recover all damage and fatigue, discard conditions and effects
        // receive quest reward (1XP per hero + bonus from quest)
        // shopping (if right after interlude, can buy any act 1 cards, then remove these from game)
        // spend XP points for skills
        // choose next quest (winner chooses)
        // setup next quest
        // Campaign phase -> quest phase

        // choosing interlude: the heroes pick if they won >= 2 act 1 quests, overlord picks if they won >=2 quests

        // TODO: in 2-hero games, free regular attack action each turn or recover 2 damage.
    }

    @Override
    protected List<AbstractAction> _computeAvailableActions(AbstractGameState gameState) {
        DescentGameState dgs = (DescentGameState)gameState;
        int currentPlayer = gameState.getCurrentPlayer();

        // Init action list
        ArrayList<AbstractAction> actions = new ArrayList<>();
        Figure actingFigure = dgs.getActingFigure();

        // End turn is always possible
        actions.add(new EndTurn());

        // If we have movement points to spend, add move actions
        if (actingFigure.getAttributeValue(Figure.Attribute.MovePoints) > 0) {
            actions.addAll(moveActions(dgs, actingFigure));
        }

        // TODO: stamina move, not an "action", but same rules for move apply [DONE - Toby]
        // If a hero has stamina to spare, add move actions that cost fatigue
        if (actingFigure instanceof Hero) {
            if (!actingFigure.getAttribute(Figure.Attribute.Fatigue).isMaximum()) {
                GetFatiguedMovementPoints fatiguedMovementPoints = new GetFatiguedMovementPoints();
                if (fatiguedMovementPoints.canExecute(dgs)) actions.add(fatiguedMovementPoints);
            }
        }

        // Add actions that cost action points
        if (!actingFigure.getNActionsExecuted().isMaximum()) {
            // Get movement points action
            GetMovementPoints movePoints = new GetMovementPoints();
            if (movePoints.canExecute(dgs)) actions.add(movePoints);

            // - Attack with 1 equipped weapon [ + monsters, the rest are just heroes] TODO

            AttackType attackType = AttackType.NONE;
            if (actingFigure instanceof Hero)
            {
                // Examines the Hero's equipment to see what their weapon's range is
                Deck<DescentCard> myEquipment = ((Hero) actingFigure).getHandEquipment();
                int length = myEquipment.getComponents().size();
                for (int i = 0; i < length; i++)
                {
                    AttackType temp = myEquipment.get(i).getAttackType();

                    // Checks if the Hero can make a melee attack, ranged attack, or both with their current equipment
                    if(temp != AttackType.NONE) {
                        if (attackType == AttackType.NONE) {
                            attackType = temp;
                        } else if (attackType != temp) {
                            attackType = AttackType.BOTH;
                        }
                    }
                }
            }

            if (actingFigure instanceof Monster)
            {
                // TODO Check if Monster is melee or ranged
                attackType = AttackType.MELEE;
            }

            if (attackType == AttackType.MELEE || attackType == AttackType.BOTH)
            {
                actions.addAll(meleeAttackActions(dgs, actingFigure));
            }
            if (attackType == AttackType.RANGED || attackType == AttackType.BOTH)
            {
                actions.addAll(rangedAttackActions(dgs, actingFigure));
            }

            // - Rest
            if (actingFigure instanceof Hero) {
                // Only heroes can rest
                Rest act = new Rest();
                if (act.canExecute(dgs)) {
                    actions.add(act);
                }
            }

            // - Open/close a door TODO
            // - Revive hero TODO

            // - Search
            if (actingFigure instanceof Hero) {
                // Only heroes can search for adjacent Search tokens (or ones they're sitting on top of)
                Vector2D loc = actingFigure.getPosition();
                GridBoard board = dgs.getMasterBoard();
                List<Vector2D> neighbours = getNeighbourhood(loc.getX(), loc.getY(), board.getWidth(), board.getHeight(), true);
                for (DToken token: dgs.tokens) {
                    if (token.getDescentTokenType() == DescentToken.Search
                            && token.getPosition() != null
                            && (neighbours.contains(token.getPosition()) || token.getPosition().equals(loc))) {
                        for (DescentAction da: token.getEffects()) {
                            actions.add(da.copy());
                        }
                    }
                }
            }

            // - Stand up TODO

            // - Special (specified by quest)
            if (actingFigure.getAbilities() != null) {
                for (DescentAction act : actingFigure.getAbilities()) {
                    // Check if action can be executed right now
                    if (act.canExecute(Triggers.ACTION_POINT_SPEND, dgs)) {
                        actions.add(act);
                    }
                }
            }
        }

        // TODO: exhaust a card for an action/modifier/effect "free" action

        return actions;
    }

    private HashMap<Vector2D, Pair<Double,List<Vector2D>>> getAllAdjacentNodes(DescentGameState dgs, Figure figure){
        Vector2D figureLocation = figure.getPosition();
        BoardNode figureNode = dgs.masterBoard.getElement(figureLocation.getX(), figureLocation.getY());
        String figureType = figure.getTokenType();

        //<Board Node, Cost to get there>
        HashMap<BoardNode, Pair<Double,List<Vector2D>>> expandedBoardNodes = new HashMap<>();
        HashMap<BoardNode, Pair<Double,List<Vector2D>>> nodesToBeExpanded = new HashMap<>();
        HashMap<BoardNode, Pair<Double,List<Vector2D>>> allAdjacentNodes = new HashMap<>();

        nodesToBeExpanded.put(figureNode, new Pair<>(0.0, new ArrayList<>()));
        while (!nodesToBeExpanded.isEmpty()){
            // Pick a node to expand, and remove it from the map
            Map.Entry<BoardNode,Pair<Double,List<Vector2D>>> entry = nodesToBeExpanded.entrySet().iterator().next();
            BoardNode expandingNode = entry.getKey();
            double expandingNodeCost = entry.getValue().a;
            List<Vector2D> expandingNodePath = entry.getValue().b;
            nodesToBeExpanded.remove(expandingNode);

            // Go through all the neighbour nodes
            HashMap<Integer, Double> neighbours = expandingNode.getNeighbours();
            for (Integer neighbourID : neighbours.keySet()){
                BoardNode neighbour = (BoardNode) dgs.getComponentById(neighbourID);
                Vector2D loc = ((PropertyVector2D) neighbour.getProperty(coordinateHash)).values;

                double costToMoveToNeighbour = expandingNode.getNeighbourCost(neighbour);
                double totalCost = expandingNodeCost + costToMoveToNeighbour;
                List<Vector2D> totalPath = new ArrayList<>(expandingNodePath);
                totalPath.add(loc);
                boolean isFriendly = false;
                boolean isEmpty = TerrainType.isWalkableTerrain(neighbour.getComponentName());

                PropertyInt figureOnLocation = (PropertyInt)neighbour.getProperty(playersHash);
                if (figureOnLocation.value != -1) {
                    isEmpty = false;
                    Figure neighbourFigure = (Figure) dgs.getComponentById(figureOnLocation.value);
                    if (figureType.equals(neighbourFigure.getTokenType())) {
                        isFriendly = true;
                    }
                }

                if (isFriendly){
                    //if the node is friendly and not expanded - add it to the expansion list
                    if(!expandedBoardNodes.containsKey(neighbour)){
                        nodesToBeExpanded.put(neighbour, new Pair<>(totalCost, totalPath));
                    //if the node is friendly and expanded but the cost was higher - add it to the expansion list
                    } else if (expandedBoardNodes.containsKey(neighbour) && expandedBoardNodes.get(neighbour).a > totalCost){
                        expandedBoardNodes.remove(neighbour);
                        nodesToBeExpanded.put(neighbour, new Pair<>(totalCost, totalPath));
                    }
                } else if (isEmpty) {
                    //if the node is empty - add it to adjacentNodeList
                    if (!allAdjacentNodes.containsKey(neighbour) || allAdjacentNodes.get(neighbour).a > totalCost){
                        allAdjacentNodes.put(neighbour, new Pair<>(totalCost, totalPath));
                    }
                }
                expandedBoardNodes.put(neighbour, new Pair<>(totalCost, totalPath));
            }
        }

        //Return list of coordinates
        HashMap<Vector2D, Pair<Double,List<Vector2D>>> allAdjacentLocations = new HashMap<>();
        for (BoardNode boardNode : allAdjacentNodes.keySet()){
            Vector2D loc = ((PropertyVector2D) boardNode.getProperty(coordinateHash)).values;
            allAdjacentLocations.put(loc, allAdjacentNodes.get(boardNode));
        }

        return allAdjacentLocations;
    }

    // Pair<final position, final orientation> -> pair<movement cost to get there, list of positions to travel through to get there>
    private Map<Pair<Vector2D, Monster.Direction>, Pair<Double,List<Vector2D>>> getPossibleRotationsForMoveActions(Map<Vector2D, Pair<Double,List<Vector2D>>> allAdjacentNodes, DescentGameState dgs, Figure figure){

        Map<Pair<Vector2D, Monster.Direction>, Pair<Double,List<Vector2D>>> possibleRotations = new HashMap<>();

        // Go through all adjacent nodes
        for (Map.Entry<Vector2D, Pair<Double,List<Vector2D>>> e : allAdjacentNodes.entrySet()) {
            Vector2D nodeLoc = e.getKey();

            if (figure.getSize().a > 1 || figure.getSize().b > 1) {
                // Only monsters can have a size bigger than 1x1
                Monster m = (Monster) figure;
                Pair<Integer, Integer> monsterSize = m.getSize();

                // Check all possible orientations with this position as the anchor if figure is bigger than 1x1
                // If all spaces occupied are legal, then keep valid options
                for (Monster.Direction d: Monster.Direction.values()) {
                    Vector2D topLeftCorner = m.applyAnchorModifier(nodeLoc.copy(), d);
                    Pair<Integer, Integer> mSize = monsterSize.copy();
                    if (d.ordinal() % 2 == 1) mSize.swap();

                    boolean legal = true;
                    for (int j = 0; j < mSize.a; j++) {
                        for (int i = 0; i < mSize.b; i++) {
                            BoardNode spaceOccupied = dgs.masterBoard.getElement(topLeftCorner.getX() + j, topLeftCorner.getY() + i);
                            if (spaceOccupied != null) {
                                PropertyInt figureOnLocation = (PropertyInt) spaceOccupied.getProperty(playersHash);
                                if (!TerrainType.isWalkableTerrain(spaceOccupied.getComponentName()) ||
                                        figureOnLocation.value != -1 && figureOnLocation.value != figure.getComponentID()) {
                                    legal = false;
                                    break;
                                }
                            } else {
                                legal = false;
                                break;
                            }
                        }
                    }
                    if (legal) {
                        possibleRotations.put(new Pair<>(nodeLoc, d), e.getValue());
                    }
                }
            } else {
                possibleRotations.put(new Pair<>(nodeLoc, Monster.Direction.getDefault()), e.getValue());
            }
        }

        return possibleRotations;
    }

    private Map<Vector2D, Pair<Double,List<Vector2D>>> getAllPointOfInterests(DescentGameState dgs, Figure figure){

        ArrayList<Vector2D> pointsOfInterest = new ArrayList<>();
        Map<Vector2D, Pair<Double,List<Vector2D>>> movePointOfInterest = new HashMap<>();
        if (figure.getTokenType().equals("Monster")) {
            for (Hero h : dgs.heroes) {
                pointsOfInterest.add(h.getPosition());
            }

        } else if (figure.getTokenType().equals("Hero")) {
            for (List<Monster> monsterGroup : dgs.monsters) {
                for (Monster m : monsterGroup) {
                    pointsOfInterest.add(m.getPosition());
                }
            }

            for (DToken dToken : dgs.tokens){
                if (dToken.getPosition() != null){
                    pointsOfInterest.add(dToken.getPosition());
                }
            }
        }

        //For every point of interest find neighbours that are empty and add then as potential move spots
        for (Vector2D point : pointsOfInterest){
            BoardNode figureNode = dgs.masterBoard.getElement(point.getX(), point.getY());
            Set<Integer> neighbourIDs = figureNode.getNeighbours().keySet();
            for (Integer neighbourID : neighbourIDs){
                BoardNode neighbourNode =  (BoardNode) dgs.getComponentById(neighbourID);
                PropertyInt figureOnLocation = (PropertyInt) neighbourNode.getProperty(playersHash);
                if (figureOnLocation.value == -1){
                    Vector2D loc = ((PropertyVector2D) neighbourNode.getProperty(coordinateHash)).values;

                    // TODO: use actual A* instead of 0
                    //Double movementCost =  a_star_distance(figureNode, neighboutNode)
                    Double movementCost = 1.0;
                    List<Vector2D> path = new ArrayList<Vector2D>() {{add(loc);}};  // TODO full path
                    movePointOfInterest.put(loc, new Pair<>(movementCost, path));
                }
            }
        }

        return movePointOfInterest;
    }

    private List<AbstractAction> moveActions(DescentGameState dgs, Figure f) {

        Map<Vector2D, Pair<Double, List<Vector2D>>> allAdjacentNodes = getAllAdjacentNodes(dgs, f);
        Map<Vector2D, Pair<Double, List<Vector2D>>> allPointOfInterests = getAllPointOfInterests(dgs, f);

//        allAdjacentNodes.putAll(allPointOfInterests);

        // get all potential rotations for the figure
        Map<Pair<Vector2D, Monster.Direction>, Pair<Double, List<Vector2D>>> allPossibleRotations = getPossibleRotationsForMoveActions(allAdjacentNodes, dgs, f);
        List<Move> actions = new ArrayList<>();
        for (Pair<Vector2D, Monster.Direction> loc : allPossibleRotations.keySet()) {
            if (allPossibleRotations.get(loc).a <= f.getAttributeValue(Figure.Attribute.MovePoints)) {
                Move myMoveAction = new Move(allPossibleRotations.get(loc).b, loc.b);
                myMoveAction.updateDirectionID(dgs);
                actions.add(myMoveAction);
            }
        }

        // Sorts the movement actions to always be in the same order (Clockwise NW to W, One Space then Multiple Spaces)
        Collections.sort(actions, Comparator.comparingInt(Move::getDirectionID));

        List<AbstractAction> sortedActions = new ArrayList<>();
        sortedActions.addAll(actions);

        return sortedActions;
    }

    private List<AbstractAction> meleeAttackActions(DescentGameState dgs, Figure f) {
        List<MeleeAttack> actions = new ArrayList<>();
        Vector2D currentLocation = f.getPosition();
        BoardNode currentTile = dgs.masterBoard.getElement(currentLocation.getX(), currentLocation.getY());
        // Find valid neighbours in master graph - used for melee attacks
        for (int neighbourCompID : currentTile.getNeighbours().keySet()) {
            BoardNode neighbour = (BoardNode) dgs.getComponentById(neighbourCompID);
            if (neighbour == null) continue;
            Vector2D loc = ((PropertyVector2D) neighbour.getProperty(coordinateHash)).values;
            int neighbourID = ((PropertyInt)neighbour.getProperty(playersHash)).value;
            if ( neighbourID != -1 ) {
                Figure other = (Figure)dgs.getComponentById(neighbourID);
                if (f instanceof Monster && other instanceof Hero) {
                    // Monster attacks a hero
                    actions.add(new MeleeAttack(f.getComponentID(), other.getComponentID()));
                }
                else if (f instanceof Hero && other instanceof Monster)
                {
                    // Player attacks a monster

                    // Make sure that the Player only gets one instance of attacking the monster
                    // This was previously an issue when dealing with Large creatures that took up multiple adjacent spaces
                    boolean canAdd = true;
                    for (MeleeAttack action : actions)
                    {
                        // TODO Hash Set Comparison instead of ID
                        if (other.getComponentID() == action.getDefendingFigure())
                        {
                            // If an attack action on the same enemy already exists, this prevents a duplicate from being added
                            canAdd = false;
                        }
                    }

                    if (canAdd)
                    {
                        actions.add(new MeleeAttack(f.getComponentID(), other.getComponentID()));
                    }
                }
            }
        }

        Collections.sort(actions, Comparator.comparingInt(MeleeAttack::getDefendingFigure));

        List<AbstractAction> sortedActions = new ArrayList<>();

        sortedActions.addAll(actions);

        return sortedActions;
    }

    private List<AbstractAction> rangedAttackActions(DescentGameState dgs, Figure f) {
        List<MeleeAttack> actions = new ArrayList<>();
        Vector2D currentLocation = f.getPosition();
        BoardNode currentTile = dgs.masterBoard.getElement(currentLocation.getX(), currentLocation.getY());
        // Find valid neighbours in master graph - used for melee attacks
        for (int neighbourCompID : currentTile.getNeighbours().keySet()) {
            BoardNode neighbour = (BoardNode) dgs.getComponentById(neighbourCompID);
            if (neighbour == null) continue;
            Vector2D loc = ((PropertyVector2D) neighbour.getProperty(coordinateHash)).values;
            int neighbourID = ((PropertyInt)neighbour.getProperty(playersHash)).value;
            if ( neighbourID != -1 ) {
                Figure other = (Figure)dgs.getComponentById(neighbourID);
                if (f instanceof Monster && other instanceof Hero) {
                    // Monster attacks a hero
                    actions.add(new MeleeAttack(f.getComponentID(), other.getComponentID()));
                }
                else if (f instanceof Hero && other instanceof Monster)
                {
                    // Player attacks a monster

                    // Make sure that the Player only gets one instance of attacking the monster
                    // This was previously an issue when dealing with Large creatures that took up multiple adjacent spaces
                    boolean canAdd = true;
                    for (MeleeAttack action : actions)
                    {
                        if (other.getComponentID() == action.getDefendingFigure())
                        {
                            // If an attack action on the same enemy already exists, this prevents a duplicate from being added
                            canAdd = false;
                        }
                    }

                    if (canAdd)
                    {
                        actions.add(new MeleeAttack(f.getComponentID(), other.getComponentID()));
                    }
                }
            }
        }

        Collections.sort(actions, Comparator.comparingInt(MeleeAttack::getDefendingFigure));

        List<AbstractAction> sortedActions = new ArrayList<>();

        sortedActions.addAll(actions);

        return sortedActions;
    }

    /*
    @Override
    protected AbstractForwardModel __copy() {
        return new DescentForwardModel();
    }
    */

    private boolean checkEndOfGame(DescentGameState dgs) {
        // TODO end of campaign / other phases
        for (GameOverCondition condition: dgs.currentQuest.getGameOverConditions()) {
            if (condition.test(dgs) == CoreConstants.GameResult.GAME_END) {
                // Quest is over, give rewards
                System.out.println("Victory!");
                List<DescentReward> commonRewards = dgs.currentQuest.getCommonRewards();
                List<DescentReward> heroRewards = dgs.currentQuest.getCommonRewards();
                List<DescentReward> overlordRewards = dgs.currentQuest.getCommonRewards();
                for (int i = 0; i < dgs.getNPlayers(); i++) {
                    // TODO Uncomment this when Rewards are fixed
                    /*
                    for (DescentReward dr: commonRewards) dr.applyReward(dgs, i);
                    if (i == dgs.overlordPlayer) {
                        for (DescentReward dr: overlordRewards) dr.applyReward(dgs, i);
                    } else {
                        for (DescentReward dr: heroRewards) dr.applyReward(dgs, i);
                    }
                    */
                }
                // Quest is over, return true
                return true;
            }
        }
        return false;
    }

    private void setupBoard(DescentGameState dgs, DescentGameData _data, String bConfig) {

        // 1. Read the graph board configuration for the master grid board; a graph board where nodes are individual tiles (grid boards) and connections between them
        GraphBoard config = _data.findGraphBoard(bConfig);

        // 2. Read all necessary tiles, which are all grid boards. Keep in a list.
        dgs.tiles = new HashMap<>();  // Maps from component ID to gridboard object
        HashMap<Integer, GridBoard> tileConfigs = new HashMap<>();
        dgs.gridReferences = new HashMap<>();  // Maps from tile name to list of positions in the master grid board that its cells occupy
        for (BoardNode bn : config.getBoardNodes()) {
            String name = bn.getComponentName();
            String tileName = name;
            if (name.contains("-")) {  // There may be multiples of one tile in the board, which follow format "tilename-#"
                tileName = tileName.split("-")[0];
            }
            GridBoard tile = _data.findGridBoard(tileName).copyNewID();
            if (tile != null) {
                tile = tile.copyNewID();
                tile.setProperty(bn.getProperty(orientationHash));
                tile.setComponentName(name);
                dgs.tiles.put(tile.getComponentID(), tile);
                tileConfigs.put(bn.getComponentID(), tile);
                dgs.gridReferences.put(name, new HashMap<>());
            }
        }

        // 3. Put together the master grid board
        // Find maximum board width and height, if all were put together side by side
        int width = 0;
        int height = 0;
        for (BoardNode bn : config.getBoardNodes()) {
            // Find width of this tile, according to orientation
            GridBoard tile = tileConfigs.get(bn.getComponentID());
            if (tile != null) {
                int orientation = ((PropertyInt) bn.getProperty(orientationHash)).value;
                if (orientation % 2 == 0) {
                    width += tile.getWidth();
                    height += tile.getHeight();
                } else {
                    width += tile.getHeight();
                    height += tile.getWidth();
                }
            }
        }

        // First tile will be in the center, board could expand in more directions
        width *= 2;
        height *= 2;

        // Create big board
        BoardNode[][] board = new BoardNode[height][width];  // Board nodes here will be the individual cells in the tiles
        dgs.tileReferences = new int[height][width];  // Reference to component ID of tile placed at that position
        HashMap<BoardNode, BoardNode> drawn = new HashMap<>();  // Keeps track of which tiles have been added to the board already, for recursive purposes

        // TODO iterate all that are not already drawn, to make sure disconnected tiles are also placed.
        // StartX / Y Will need to be adjusted to not draw on top of existing things

        // Find first tile, as board node in the board configuration graph board
        BoardNode firstTile = config.getBoardNodes().get(0);
        if (firstTile != null) {
            // Find grid board of first tile, rotate to correct orientation and add its tiles to the board
            GridBoard tile = tileConfigs.get(firstTile.getComponentID());
            int orientation = ((PropertyInt) firstTile.getProperty(orientationHash)).value;
            BoardNode[][] rotated = tile.rotate(orientation);
            int startX = width / 2 - rotated[0].length / 2;
            int startY = height / 2 - rotated.length / 2;
            // Bounds will keep track of where tiles actually exist in the master board, to trim to size later
            Rectangle bounds = new Rectangle(startX, startY, rotated[0].length, rotated.length);
            // Recursive call, will add all tiles in relation to their neighbours as per the board configuration
            addTilesToBoard(null, firstTile, startX, startY, board, null, tileConfigs, dgs.tileReferences, dgs.gridReferences, drawn, bounds, dgs, null);

            // Trim the resulting board and tile references to remove excess border of nulls according to 'bounds' rectangle
            BoardNode[][] trimBoard = new BoardNode[bounds.height][bounds.width];
            int[][] trimTileRef = new int[bounds.height][bounds.width];
            for (int i = 0; i < bounds.height; i++) {
                if (bounds.width >= 0) {
                    System.arraycopy(board[i + bounds.y], bounds.x, trimBoard[i], 0, bounds.width);
                    System.arraycopy(dgs.tileReferences[i + bounds.y], bounds.x, trimTileRef[i], 0, bounds.width);
                }
            }
            dgs.tileReferences = trimTileRef;
            // And grid references
            for (Map.Entry<String, Map<Vector2D, Vector2D>> e: dgs.gridReferences.entrySet()) {
                for (Vector2D v: e.getValue().keySet()) {
                    v.subtract(bounds.x, bounds.y);
                }
            }

            // This is the final master board!
            dgs.masterBoard = new GridBoard(trimBoard);
            // Init each node (cell) properties - not occupied ("players" int property), and its position in the master grid
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    BoardNode bn = dgs.masterBoard.getElement(j, i);
                    if (bn != null) {
                        bn.setProperty(new PropertyVector2D("coordinates", new Vector2D(j, i)));
                        bn.setProperty(new PropertyInt("players", -1));
                    }
                }
            }
        } else {
            System.out.println("Tiles for the map not found");
        }


        // Pathfinder utility (TEST).
//        dgs.addAllComponents();
//        Pathfinder pf = new Pathfinder(dgs.masterBoard);
//        int orig = dgs.masterBoard.getElement(10,3).getComponentID();
//        int dest = dgs.masterBoard.getElement(10,5).getComponentID();
//        Path p = pf.getPath(dgs, orig, dest);
//
//        System.out.println(p.toString());
//        int a = 0; //10,4 -> 10,5
    }

    /**
     * Recursively adds tiles to the board, iterating through all neighbours and updating references from grid
     * to tiles, list of valid neighbours for movement graph according to Descent movement rules, and bounds for
     * the resulting grid of the master board (with all tiles put together).
     * @param tileToAdd - board node representing tile to add to board
     * @param x - top-left x-coordinate for this tile's location in the master board
     * @param y - top-left y-coordinate for this tile's location in the master board
     * @param board - the master grid board representation
     * @param tileGrid - grid representation of tile to add to board (possibly a trimmed version from its corresponding
     *                 object in the "tiles" map to fit together with existing board)
     * @param tiles - mapping from board node component ID, to GridBoard object representing the tile
     * @param tileReferences - references from each cell in the grid to the component ID of the GridBoard representing
     *                       the tile at that location
     * @param drawn - a list of board nodes already drawn, to avoid drawing twice during recursive calls.
     * @param bounds - bounds of contents of the master grid board
     */
    private void addTilesToBoard(BoardNode parentTile, BoardNode tileToAdd, int x, int y, BoardNode[][] board,
                                 BoardNode[][] tileGrid,
                                 Map<Integer, GridBoard> tiles,
                                 int[][] tileReferences,  Map<String, Map<Vector2D, Vector2D>> gridReferences,
                                 Map<BoardNode, BoardNode> drawn,
                                 Rectangle bounds,
                                 DescentGameState dgs,
                                 String sideWithOpening) {
        if (!drawn.containsKey(parentTile) || !drawn.get(parentTile).equals(tileToAdd)) {
            // Draw this tile in the big board at x, y location
            GridBoard tile = tiles.get(tileToAdd.getComponentID());
            BoardNode[][] originalTileGrid = tile.rotate(((PropertyInt) tileToAdd.getProperty(orientationHash)).value);
            if (tileGrid == null) {
                tileGrid = originalTileGrid;
            }
            int height = tileGrid.length;
            int width = tileGrid[0].length;

            // Add cells from new tile to the master board
            for (int i = y; i < y + height; i++) {
                for (int j = x; j < x + width; j++) {
                    // Avoid removing already set tiles
                    if (tileGrid[i-y][j-x] == null || tileGrid[i-y][j-x].getComponentName().equalsIgnoreCase("null")
                            || board[i][j] != null && !board[i][j].getComponentName().equalsIgnoreCase ("null")
                            || !TerrainType.isInsideTerrain(tileGrid[i-y][j-x].getComponentName())) continue;

                    // Set
                    board[i][j] = tileGrid[i - y][j - x].copy();
                    board[i][j].setProperty(new PropertyInt("connections", tileToAdd.getComponentID()));

                    // Don't keep references for edge tiles
                    if (board[i][j] == null || board[i][j].getComponentName().equals("edge")
                            || board[i][j].getComponentName().equals("open")) continue;

                    // Set references
                    tileReferences[i][j] = tile.getComponentID();
                    for (String s : gridReferences.keySet()) {
                        gridReferences.get(s).remove(new Vector2D(j, i));
                    }
                    gridReferences.get(tile.getComponentName()).put(new Vector2D(j, i), new Vector2D(j-x, i-y));
                }
            }

            // Add connections at opening side with existing tiles
            addConnectionsAtOpeningOnSide(board, originalTileGrid, x, y, width, height, sideWithOpening);

            // Add connections inside the tile, ignoring blocked spaces
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    BoardNode currentSpace = tileGrid[i][j];
                    if (currentSpace != null && (TerrainType.isWalkableTerrain(currentSpace.getComponentName())
                            || currentSpace.getComponentName().equalsIgnoreCase("pit"))) {  // pits connect to walkable spaces only (pit-pit not allowed)
                        List<Vector2D> boardNs = getNeighbourhood(j, i, width, height, true);
                        for (Vector2D n2 : boardNs) {
                            if (tileGrid[n2.getY()][n2.getX()] != null && TerrainType.isWalkableTerrain(tileGrid[n2.getY()][n2.getX()].getComponentName())) {
                                board[n2.getY()+y][n2.getX()+x].addNeighbourWithCost(board[i+y][j+x], TerrainType.getMovePointsCost(board[i+y][j+x].getComponentName()));
                                board[i+y][j+x].addNeighbourWithCost(board[n2.getY()+y][n2.getX()+x], TerrainType.getMovePointsCost(board[n2.getY()+y][n2.getX()+x].getComponentName()));
                            }
                        }
                    }
                }
            }

            // This tile was drawn
//            drawn.add(tileToAdd);
            drawn.put(parentTile, tileToAdd);

            // Draw neighbours
            for (int neighbourCompId: tileToAdd.getNeighbours().keySet()) {
                BoardNode neighbour = (BoardNode) dgs.getComponentById(neighbourCompId);

                // Find location to start drawing neighbour
                Pair<String, Vector2D> connectionToNeighbour = findConnection(tileToAdd, neighbour, findOpenings(tileGrid));

                if (connectionToNeighbour != null) {
                    connectionToNeighbour.b.add(x, y);
                    // Find orientation and opening connection from neighbour, generate top-left corner of neighbour from that
                    GridBoard tileN = tiles.get(neighbour.getComponentID());
                    if (tileN != null) {
                        BoardNode[][] tileGridN = tileN.rotate(((PropertyInt) neighbour.getProperty(orientationHash)).value);

                        // Find location to start drawing neighbour
                        Pair<String, Vector2D> conn2 = findConnection(neighbour, tileToAdd, findOpenings(tileGridN));

                        int w = tileGridN[0].length;
                        int h = tileGridN.length;

                        if (conn2 != null) {
                            String side = conn2.a;
                            Vector2D connectionFromNeighbour = conn2.b;
                            if (side.equalsIgnoreCase("W")) {
                                // Remove first column
                                BoardNode[][] tileGridNTrim = new BoardNode[h][w - 1];
                                for (int i = 0; i < h; i++) {
                                    System.arraycopy(tileGridN[i], 1, tileGridNTrim[i], 0, w - 1);
                                }
                                tileGridN = tileGridNTrim;
                            } else if (side.equalsIgnoreCase("E")) {
                                connectionFromNeighbour.subtract(1, 0);
                                // Remove last column
                                BoardNode[][] tileGridNTrim = new BoardNode[h][w - 1];
                                for (int i = 0; i < h; i++) {
                                    System.arraycopy(tileGridN[i], 0, tileGridNTrim[i], 0, w - 1);
                                }
                                tileGridN = tileGridNTrim;
                            } else if (side.equalsIgnoreCase("N")) {
                                // Remove first row
                                BoardNode[][] tileGridNTrim = new BoardNode[h - 1][w];
                                for (int i = 1; i < h; i++) {
                                    System.arraycopy(tileGridN[i], 0, tileGridNTrim[i - 1], 0, w);
                                }
                                tileGridN = tileGridNTrim;
                            } else {
                                connectionFromNeighbour.subtract(0, 1);
                                // Remove last row
                                BoardNode[][] tileGridNTrim = new BoardNode[h - 1][w];
                                for (int i = 0; i < h - 1; i++) {
                                    System.arraycopy(tileGridN[i], 0, tileGridNTrim[i], 0, w);
                                }
                                tileGridN = tileGridNTrim;
                            }
                            Vector2D topLeftCorner = new Vector2D(connectionToNeighbour.b.getX() - connectionFromNeighbour.getX(),
                                    connectionToNeighbour.b.getY() - connectionFromNeighbour.getY());

                            // Update area bounds
                            if (topLeftCorner.getX() < bounds.x) bounds.x = topLeftCorner.getX();
                            if (topLeftCorner.getY() < bounds.y) bounds.y = topLeftCorner.getY();
                            int deltaMaxX = (int) (topLeftCorner.getX() + tileGridN[0].length - bounds.getMaxX());
                            if (deltaMaxX > 0) bounds.width += deltaMaxX;
                            int deltaMaxY = (int) (topLeftCorner.getY() + tileGridN.length - bounds.getMaxY());
                            if (deltaMaxY > 0) bounds.height += deltaMaxY;

                            // Draw neighbour recursively
                            addTilesToBoard(tileToAdd, neighbour, topLeftCorner.getX(), topLeftCorner.getY(), board, tileGridN,
                                    tiles, tileReferences, gridReferences, drawn, bounds, dgs, side);
                        }
                    }
                }
            }
        }
    }

    private void addConnectionsAtOpeningOnSide(BoardNode[][] board, BoardNode[][] originalTileGrid,
                                               int x, int y, int width, int height, String side) {
        if (side != null) {
            if (side.equalsIgnoreCase("n")) {
                // Nodes at opening that should connect are on the top row. Above them on original tile grid there is an "open" space
                int i = 0;
                for (int j = 0; j < width; j++) {
                    if (originalTileGrid[i][j] != null &&  // Same row, in the tile that was placed this is trimmed
                            originalTileGrid[i][j].getComponentName().equalsIgnoreCase("open")
                            && board[i + y - 1][j + x] != null) {
                        // Add connections for this node
                        for (int x1 = x-1; x1 <= x+1; x1++) {
                            if (j+x1 >= 0 && j+x1 < board[0].length) {
                                if (board[i + y][j + x] != null && board[i + y - 1][j + x1] != null) {
                                    board[i + y][j + x].addNeighbourWithCost(board[i + y - 1][j + x1], TerrainType.getMovePointsCost(board[i + y - 1][j + x1].getComponentName()));
                                    board[i + y - 1][j + x1].addNeighbourWithCost(board[i + y][j + x], TerrainType.getMovePointsCost(board[i + y][j + x].getComponentName()));
                                }
                            }
                        }
                        // And connections back from the node in front too
                        for (int x1 = x-1; x1 <= x+1; x1++) {
                            if (j+x1 >= 0 && j+x1 < board[0].length) {
                                if (board[i + y - 1][j + x] != null && board[i + y][j + x1] != null) {
                                    board[i + y - 1][j + x].addNeighbourWithCost(board[i + y][j + x1], TerrainType.getMovePointsCost(board[i + y][j + x1].getComponentName()));
                                    board[i + y][j + x1].addNeighbourWithCost(board[i + y - 1][j + x], TerrainType.getMovePointsCost(board[i + y - 1][j + x].getComponentName()));
                                }
                            }
                        }
                    }
                }
            } else if (side.equalsIgnoreCase("s")) {
                // Nodes at opening that should connect are on the bottom row. Below them is an "open" space
                int i = height-1;
                for (int j = 0; j < width; j++) {
                    if (originalTileGrid[i+1][j] != null &&  // Next row
                            originalTileGrid[i+1][j].getComponentName().equalsIgnoreCase("open")
                            && board[i + y + 1][j + x] != null) {
                        // Add connections for this node
                        for (int x1 = x-1; x1 <= x+1; x1++) {
                            if (j + x1 >= 0 && j + x1 < board[0].length) {
                                if (board[i + y][j + x] != null && board[i + y + 1][j + x1] != null) {
                                    board[i + y][j + x].addNeighbourWithCost(board[i + y + 1][j + x1], TerrainType.getMovePointsCost(board[i + y + 1][j + x1].getComponentName()));
                                    board[i + y + 1][j + x1].addNeighbourWithCost(board[i + y][j + x], TerrainType.getMovePointsCost(board[i + y][j + x].getComponentName()));
                                }
                            }
                        }
                        // And connections back from the node in front too
                        for (int x1 = x-1; x1 <= x+1; x1++) {
                            if (j + x1 >= 0 && j + x1 < board[0].length) {
                                if (board[i + y + 1][j + x] != null && board[i + y][j + x1] != null) {
                                    board[i + y + 1][j + x].addNeighbourWithCost(board[i + y][j + x1], TerrainType.getMovePointsCost(board[i + y][j + x1].getComponentName()));
                                    board[i + y][j + x1].addNeighbourWithCost(board[i + y + 1][j + x], TerrainType.getMovePointsCost(board[i + y + 1][j + x].getComponentName()));
                                }
                            }
                        }
                    }
                }
            } else if (side.equalsIgnoreCase("e")) {
                // Nodes are on the rightmost column. To their right is "open"
                int j = width-1;
                for (int i = 0; i < height; i++) {
                    if (originalTileGrid[i][j+1] != null &&  // Next column
                            originalTileGrid[i][j+1].getComponentName().equalsIgnoreCase("open")
                            && board[i + y][j + x + 1] != null) {
                        // Add connections for this node
                        for (int y1 = y-1; y1 <= y+1; y1++) {
                            if (i + y1 >= 0 && i + y1 < board.length
                                    && board[i + y][j + x] != null && board[i + y1][j + x + 1] != null) {
                                board[i + y][j + x].addNeighbourWithCost(board[i + y1][j + x + 1], TerrainType.getMovePointsCost(board[i + y1][j + x + 1].getComponentName()));
                                board[i + y1][j + x + 1].addNeighbourWithCost(board[i + y][j + x], TerrainType.getMovePointsCost(board[i + y][j + x].getComponentName()));
                            }
                        }
                        // And connections back from the node in front too
                        for (int y1 = y-1; y1 <= y+1; y1++) {
                            if (i + y1 >= 0 && i + y1 < board.length
                                    && board[i + y][j + x + 1] != null && board[i + y1][j + x] != null) {
                                board[i + y][j + x + 1].addNeighbourWithCost(board[i + y1][j + x], TerrainType.getMovePointsCost(board[i + y1][j + x].getComponentName()));
                                board[i + y1][j + x].addNeighbourWithCost(board[i + y][j + x + 1], TerrainType.getMovePointsCost(board[i + y][j + x + 1].getComponentName()));
                            }
                        }
                    }
                }
            } else if (side.equalsIgnoreCase("w")) {
                // Nodes are in the first column (leftmost). To their left is "open"
                int j = 0;
                for (int i = 0; i < height; i++) {
                    if (originalTileGrid[i][j] != null &&  // Same column, trimmed in tile that was placed
                            originalTileGrid[i][j].getComponentName().equalsIgnoreCase("open")
                            && board[i + y][j + x - 1] != null) {
                        // Add connections for this node
                        for (int y1 = y-1; y1 <= y+1; y1++) {
                            if (i + y1 >= 0 && i + y1 < board.length
                                    && board[i + y][j + x] != null && board[i + y1][j + x - 1] != null) {
                                board[i + y][j + x].addNeighbourWithCost(board[i + y1][j + x - 1], TerrainType.getMovePointsCost(board[i + y1][j + x - 1].getComponentName()));
                                board[i + y1][j + x - 1].addNeighbourWithCost(board[i + y][j + x], TerrainType.getMovePointsCost(board[i + y][j + x].getComponentName()));
                            }
                        }
                        // And connections back from the node in front too
                        for (int y1 = y-1; y1 <= y+1; y1++) {
                            if (i + y1 >= 0 && i + y1 < board.length
                                    && board[i + y][j + x - 1] != null && board[i + y1][j + x] != null) {
                                board[i + y][j + x - 1].addNeighbourWithCost(board[i + y1][j + x], TerrainType.getMovePointsCost(board[i + y1][j + x].getComponentName()));
                                board[i + y1][j + x].addNeighbourWithCost(board[i + y][j + x - 1], TerrainType.getMovePointsCost(board[i + y][j + x - 1].getComponentName()));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds a connection between two boardnodes representing tiles in the game (i.e. where the 2 tiles should be
     * connecting according to board configuration)
     * @param from - origin board node to find connection from
     * @param to - board node to find connection to
     * @param openings - list of openings for the origin board node
     * @return - a pair of side, and location (in tile space) of openings that would connect to the given tile as required
     */
    private Pair<String, Vector2D> findConnection(BoardNode from, BoardNode to, HashMap<String, ArrayList<Vector2D>> openings) {
        String[] neighbours = ((PropertyStringArray) from.getProperty(neighbourHash)).getValues();
        String[] connections = ((PropertyStringArray) from.getProperty(connectionHash)).getValues();

        for (int i = 0; i < neighbours.length; i++) {
            if (neighbours[i].equalsIgnoreCase(to.getComponentName())) {
                String conn = connections[i];

                String side = conn.split("-")[0];
                int countFromTop = Integer.parseInt(conn.split("-")[1]);
                if (openings.containsKey(side)) {
                    if (countFromTop >= 0 && countFromTop < openings.get(side).size()) {
                        return new Pair<>(side, openings.get(side).get(countFromTop));
                    }
                }
                break;
            }
        }
        return null;
    }

    /**
     * Finds coordinates (in tile space) for where openings on all sides (top-left locations).
     * // TODO: assumes all openings 2-tile wide + no openings are next to each other.
     * @param tileGrid - grid to look for openings in
     * @return - Mapping from side (N, S, W, E) to a list of openings on that particular side.
     */
    private HashMap<String, ArrayList<Vector2D>> findOpenings(BoardNode[][] tileGrid) {
        int height = tileGrid.length;
        int width = tileGrid[0].length;

        HashMap<String, ArrayList<Vector2D>> openings = new HashMap<>();
        // TOP, check each column, stop at the first encounter in each column.
        for (int j = 0; j < width; j++) {
            for (int i = 0; i < height; i++) {
                if (tileGrid[i][j] != null && tileGrid[i][j].getComponentName().equalsIgnoreCase("open")) {
                    // Check valid: nothing, null, or edge tile above
                    if (i == 0 || tileGrid[i-1][j].getComponentName().equalsIgnoreCase("null") ||
                            tileGrid[i-1][j].getComponentName().equalsIgnoreCase("edge")) {
                        // Check valid: nothing or not "open" to the left (already included, all openings 2-wide)
                        // But another "open" to the right
                        if ((j == 0 || tileGrid[i][j-1] != null && j < width-1 && tileGrid[i][j+1] != null
                                && !tileGrid[i][j-1].getComponentName().equalsIgnoreCase("open"))
                                && (j < width-1 && tileGrid[i][j+1].getComponentName().equalsIgnoreCase("open"))) {
                            if (!openings.containsKey("N")) {
                                openings.put("N", new ArrayList<>());
                            }
                            openings.get("N").add(new Vector2D(j, i));
                            break;
                        }
                    }
                }
            }
        }
        // BOTTOM, check each column, stop at the first encounter in each column (read from bottom to top).
        for (int j = 0; j < width; j++) {
            for (int i = height-1; i >= 0; i--) {
                if (tileGrid[i][j] != null && tileGrid[i][j].getComponentName().equalsIgnoreCase("open")) {
                    // Check valid: nothing, null, or edge tile below
                    if (i == height-1 || tileGrid[i+1][j].getComponentName().equalsIgnoreCase("null") ||
                            tileGrid[i+1][j].getComponentName().equalsIgnoreCase("edge")) {
                        // Check valid: nothing or not "open" to the left (already included, all openings 2-wide)
                        // But another "open" to the right
                        if ((j == 0 || !tileGrid[i][j-1].getComponentName().equalsIgnoreCase("open")) &&
                                (j < width-1 && tileGrid[i][j+1].getComponentName().equalsIgnoreCase("open"))) {
                            if (!openings.containsKey("S")) {
                                openings.put("S", new ArrayList<>());
                            }
                            openings.get("S").add(new Vector2D(j, i));
                            break;
                        }
                    }
                }
            }
        }
        // LEFT, check each row, stop at the first encounter in each row.
        for (int i = 0; i < height; i++){
            for (int j = 0; j < width; j++) {
                if (tileGrid[i][j] != null && tileGrid[i][j].getComponentName().equalsIgnoreCase("open")) {
                    // Check valid: nothing, null, or edge tile to the left
                    if (j == 0 || tileGrid[i][j-1].getComponentName().equalsIgnoreCase("null") ||
                            tileGrid[i][j-1].getComponentName().equalsIgnoreCase("edge")) {
                        // Check valid: nothing or not "open" above (already included, all openings 2-wide)
                        // But another "open" below
                        if ((i == 0 || !tileGrid[i-1][j].getComponentName().equalsIgnoreCase("open")) &&
                                (i < height-1 && tileGrid[i+1][j].getComponentName().equalsIgnoreCase("open"))) {
                            if (!openings.containsKey("W")) {
                                openings.put("W", new ArrayList<>());
                            }
                            openings.get("W").add(new Vector2D(j, i));
                            break;
                        }
                    }
                }
            }
        }
        // RIGHT, check each row, stop at the first encounter in each row (read from right to left).
        for (int i = 0; i < height; i++){
            for (int j = width-1; j >= 0; j--) {
                if (tileGrid[i][j] != null && tileGrid[i][j].getComponentName().equalsIgnoreCase("open")) {
                    // Check valid: nothing, null, or edge tile to the right
                    if (j == width-1 || tileGrid[i][j+1].getComponentName().equalsIgnoreCase("null") ||
                            tileGrid[i][j+1].getComponentName().equalsIgnoreCase("edge")) {
                        // Check valid: nothing or not "open" above (already included, all openings 2-wide)
                        // But another "open" below
                        if ((i == 0 || !tileGrid[i-1][j].getComponentName().equalsIgnoreCase("open")) &&
                                (i < height-1 && tileGrid[i+1][j].getComponentName().equalsIgnoreCase("open"))) {
                            if (!openings.containsKey("E")) {
                                openings.put("E", new ArrayList<>());
                            }
                            openings.get("E").add(new Vector2D(j, i));
                            break;
                        }
                    }
                }
            }
        }
        return openings;
    }

    /**
     * Creates all the monsters according to given quest information and places them randomly in the map on requested tile.
     * @param dgs - game state
     * @param quest - quest defining monsters
     * @param _data - all game data
     * @param rnd - random generator
     */
    private void createMonsters(DescentGameState dgs, Quest quest, DescentGameData _data, Random rnd, int nActionsPerFigure) {
        dgs.monsters = new ArrayList<>();
        List<String[]> monsters = quest.getMonsters();
        for (String[] mDef: monsters) {
            List<Monster> monsterGroup = new ArrayList<>();

            String nameDef = mDef[0];
            String name = nameDef.split(":")[0];
            String tile = mDef[1];
            Set<Vector2D> tileCoords = dgs.gridReferences.get(tile).keySet();
            int act = quest.getAct();
            Map<String, Monster> monsterDef = _data.findMonster(name);
            Monster superDef = monsterDef.get("super");
            int[] monsterSetup = ((PropertyIntArray)superDef.getProperty(setupHash)).getValues();

            // TODO: this could be adding/removing abilities too
            // Check attribute modifiers
            // Map from who (all/minion/master) -> list of modifiers in pairs (Atribute, howMuch)
            HashMap<String, ArrayList<Pair<Figure.Attribute, Integer>>> attributeModifiers = new HashMap<>();
            if (mDef.length > 2) {
                String mod = mDef[2];
                String[] modifiers = mod.split(";");
                for (String modifier: modifiers) {
                    String who = modifier.split(":")[0];
                    String attribute = modifier.split(":")[1];
                    String howMuch = modifier.split(":")[2];
                    int amount = Integer.parseInt(howMuch);

                    if (!attributeModifiers.containsKey(who)) {
                        attributeModifiers.put(who, new ArrayList<>());
                    }
                    attributeModifiers.get(who).add(new Pair<>(Figure.Attribute.valueOf(attribute), amount));
                }
            }

            // Always 1 master
            Monster master = monsterDef.get(act + "-master").copyNewID();
            master.getNActionsExecuted().setMaximum(nActionsPerFigure);
            master.setProperties(monsterDef.get(act + "-master").getProperties());
            master.setComponentName(name + " master");
            if (attributeModifiers.containsKey("master")) {
                for (Pair<Figure.Attribute, Integer> modifier : attributeModifiers.get("master")) {
                    master.getAttribute(modifier.a).setMaximum(master.getAttribute(modifier.a).getMaximum() + modifier.b);
                }
            }
            if (attributeModifiers.containsKey("all")) {
                for (Pair<Figure.Attribute, Integer> modifier : attributeModifiers.get("all")) {
                    master.getAttribute(modifier.a).setMaximum(master.getAttribute(modifier.a).getMaximum() + modifier.b);
                }
            }
            placeMonster(dgs, master, new ArrayList<>(tileCoords), rnd, superDef);
            master.setOwnerId(dgs.overlordPlayer);
            monsterGroup.add(master);

            // How many minions?
            int nMinions;
            if (nameDef.contains("group")) {
                if (nameDef.contains("ignore")) {
                    // Ignore group limits, max number
                    nMinions = monsterSetup[monsterSetup.length-1];
                } else {
                    // Respect group limits
                    nMinions = monsterSetup[Math.max(0,dgs.getNPlayers()-3)];
                }
            } else {
                // Format name:#minions
                nMinions = Integer.parseInt(nameDef.split(":")[1]);
            }

            // Place minions
            for (int i = 0; i < nMinions; i++) {
                Monster minion = monsterDef.get(act + "-minion").copyNewID();
                minion.setProperties(monsterDef.get(act + "-minion").getProperties());
                minion.setComponentName(name + " minion");
                placeMonster(dgs, minion, new ArrayList<>(tileCoords), rnd, superDef);
                minion.setOwnerId(dgs.overlordPlayer);
                minion.getNActionsExecuted().setMaximum(nActionsPerFigure);
                monsterGroup.add(minion);
                if (attributeModifiers.containsKey("minion")) {
                    for (Pair<Figure.Attribute, Integer> modifier : attributeModifiers.get("minion")) {
                        master.getAttribute(modifier.a).setMaximum(master.getAttribute(modifier.a).getMaximum() + modifier.b);
                    }
                }
                if (attributeModifiers.containsKey("all")) {
                    for (Pair<Figure.Attribute, Integer> modifier : attributeModifiers.get("all")) {
                        master.getAttribute(modifier.a).setMaximum(master.getAttribute(modifier.a).getMaximum() + modifier.b);
                    }
                }
            }

            dgs.monsters.add(monsterGroup);
        }
    }

    /**
     * Places a monster in the board, randomly choosing one valid tile from given list.
     * @param dgs - current game state
     * @param monster - monster to place
     * @param tileCoords - coordinate options for the monster
     * @param rnd - random generator
     */
    private void placeMonster(DescentGameState dgs, Monster monster, List<Vector2D> tileCoords, Random rnd, Token superDef) {
        // Finish setup of monster
        monster.setProperties(superDef.getProperties());

        // TODO: maybe change orientation if monster doesn't fit vertically
        String size = ((PropertyString)monster.getProperty(sizeHash)).value;
        int w = Integer.parseInt(size.split("x")[0]);
        int h = Integer.parseInt(size.split("x")[1]);
        monster.setSize(w, h);

        while (tileCoords.size() > 0) {
            Vector2D option = tileCoords.get(rnd.nextInt(tileCoords.size()));
            tileCoords.remove(option);
            BoardNode position = dgs.masterBoard.getElement(option.getX(), option.getY());
            if (position.getComponentName().equals("plain") &&
                    ((PropertyInt)position.getProperty(playersHash)).value == -1) {
                // TODO: some monsters want to spawn in lava/water.
                // This can be top-left corner, check if the other tiles are valid too
                boolean canPlace = true;
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < w; j++) {
                        if (i == 0 && j == 0) continue;
                        Vector2D thisTile = new Vector2D(option.getX() + j, option.getY() + i);
                        BoardNode tile = dgs.masterBoard.getElement(thisTile.getX(), thisTile.getY());
                        if (tile == null || !tile.getComponentName().equals("plain") ||
                                !tileCoords.contains(thisTile) ||
                                ((PropertyInt)tile.getProperty(playersHash)).value != -1) {
                            canPlace = false;
                        }
                    }
                }
                if (canPlace) {
                    monster.setPosition(option.copy());
                    PropertyInt prop = new PropertyInt("players", monster.getComponentID());
                    for (int i = 0; i < h; i++) {
                        for (int j = 0; j < w; j++) {
                            dgs.masterBoard.getElement(option.getX() + j, option.getY() + i).setProperty(prop);
                        }
                    }
                    break;
                }
            }
        }
    }

    private boolean hasLineOfSight(DescentGameState dgs, Vector2D startPoint, Vector2D endPoint){

        boolean hasLineOfSight = true;
        ArrayList<Vector2D> containedPoints = LineOfSight.bresenhamsLineAlgorithm(startPoint, endPoint);


        // For each coordinate in the line, check:
        // 1) Does the coordinate have it's board node
        // 2) Is the board node empty (no character on location)
        // 3) Is the board node connected to previously checked board node
        // If any of these are false, then there is no LOS
        for (int i = 1; i < containedPoints.size(); i++){

            Vector2D previousPoint = containedPoints.get(i - 1);
            Vector2D point = containedPoints.get(i);

            // Check 1) Does the board node exist at this coordinate
            BoardNode currentTile = dgs.masterBoard.getElement(point.getX(), point.getY());
            if (currentTile == null){
                hasLineOfSight = false;
                break;
            }

            // Check 2) Is the board node empty
            Integer owner = ((PropertyInt) currentTile.getProperty(playersHash)).value;
            if (owner != -1 && i != containedPoints.size() - 1){
                hasLineOfSight = false;
                break;
            }

            // Check 3) Is the board node connected to previous board node
            BoardNode previousTile = dgs.masterBoard.getElement(previousPoint.getX(), previousPoint.getY());
            if (!previousTile.getNeighbours().keySet().contains(currentTile.getComponentID())){
                hasLineOfSight = false;
                break;
            }
        }

        return hasLineOfSight;
    }
}
