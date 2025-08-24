package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.Turn;
import curly.octo.map.generators.snails.*;
import curly.octo.map.hints.SpawnPointHint;
import curly.octo.map.hints.LightHint;
import lights.LightPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A map generator that uses the "snails" system to create interesting dungeon layouts.
 * Snails are simple agents that move around and mark tiles as part of the map.
 * Uses expansion nodes to create complex, interconnected dungeon structures.
 */
public class SnailMapGenerator extends MapGenerator {

    // Expansion node management
    private final List<ExpansionNode> necessaryNodes = new ArrayList<>();
    private final List<ExpansionNode> optionalNodes = new ArrayList<>();

    // Map generation parameters
    private static final int MIN_MAP_SIZE = 200; // Minimum tiles before considering completion
    private static final int MAX_MAP_SIZE = 1000; // Maximum tiles to prevent infinite generation
    private static final float OPTIONAL_NODE_PROBABILITY = 0.4f; // Chance to use optional nodes

    // Snail type registry for flexible snail generation
    private final SnailTypeRegistry snailRegistry;

    public SnailMapGenerator(Random random, GameMap gameMap) {
        this(random, gameMap, LevelProfile.BALANCED);
    }

    public SnailMapGenerator(Random random, GameMap gameMap, LevelProfile profile) {
        super(random, gameMap);
        this.snailRegistry = profile.createRegistry();
    }

    @Override
    public void generate() {
        // Start with a spawn room - spawn at ground floor level
        Vector3 startPos = new Vector3(0, 0, 0);  // Y=0 is floor level for snails

        // Create initial spawn room
        RoomSnail startRoom = new RoomSnail(map, startPos, Direction.NORTH, random, 7, 4, 7);
        executeSnailWithNodes(startRoom);

        // Generate expansion-based dungeon
        generateExpansionBasedMap();

        // Add spawn point at origin
        addSpawnPoint(startPos);

        // Add a light at spawn point (above player head)
        addLight(new Vector3(startPos.x, startPos.y+ 1, startPos.z));

        // Close the map by creating walls around all open spaces
        int tilesBeforeClose = map.getAllTiles().size();
        closeMap();
        int tilesAfterClose = map.getAllTiles().size();

        // Count tiles by type for debugging
        int emptyTiles = 0;
        int fullTiles = 0;
        for (MapTile tile : map.getAllTiles()) {
            if (tile.geometryType == MapTileGeometryType.EMPTY) {
                emptyTiles++;
            } else if (tile.geometryType == MapTileGeometryType.FULL) {
                fullTiles++;
            }
        }

        // Debug output
        System.out.println("SnailMapGenerator: Before closeMap: " + tilesBeforeClose + " tiles");
        System.out.println("SnailMapGenerator: After closeMap: " + tilesAfterClose + " tiles");
        System.out.println("SnailMapGenerator: Final count - " + emptyTiles + " EMPTY, " + fullTiles + " FULL");
    }

    /**
     * Determine which level profile to use based on the current registry configuration
     */
    private LevelProfile determineLevelProfileFromRegistry() {
        // For now, just return BALANCED - could be enhanced to analyze registry weights
        return LevelProfile.BALANCED;
    }

    private void generateExpansionBasedMap() {
        int iterations = 0;
        int maxIterations = 1000;

        while ((hasNecessaryNodes() || shouldAddMoreOptionalNodes()) && iterations < maxIterations) {
            // Process all necessary nodes first
            processNecessaryNodes();
            // Then process some optional nodes if map isn't large enough
            processOptionalNodes();

            // If map is still too small and we're running out of nodes, inject more
            if (map.getAllTiles().size() < MIN_MAP_SIZE && !hasNecessaryNodes() && optionalNodes.size() < 3) {
                injectExpansionNodes();
            }

            iterations++;
        }
    }

    private void executeSnailWithNodes(BaseSnail snail) {
        List<BaseSnail> activeSnails = new ArrayList<>();
        activeSnails.add(snail);

        int maxSteps = 100;
        int steps = 0;

        while (!activeSnails.isEmpty() && steps < maxSteps) {
            List<BaseSnail> nextGeneration = new ArrayList<>();

            for (BaseSnail activeSnail : activeSnails) {
                if (!activeSnail.isDone()) {
                    SnailResult result = activeSnail.execute();

                    if (!result.isComplete()) {
                        nextGeneration.add(activeSnail);
                    }

                    // Add spawned snails
                    nextGeneration.addAll(result.getSpawnedSnails());

                    // Collect expansion nodes
                    for (ExpansionNode node : result.getExpansionNodes()) {
                        if (node.getPriority() == ExpansionNode.Priority.NECESSARY) {
                            necessaryNodes.add(node);
                        } else {
                            optionalNodes.add(node);
                        }
                    }
                }
            }

            activeSnails = nextGeneration;
            steps++;
        }
    }

    private void processNecessaryNodes() {
        List<ExpansionNode> nodesToProcess = new ArrayList<>(necessaryNodes);
        necessaryNodes.clear();

        for (ExpansionNode node : nodesToProcess) {
            if (!node.isConsumed()) {
                BaseSnail snail = createRandomSnailForNode(node);
                if (snail != null) {
                    executeSnailWithNodes(snail);
                    node.consume();
                }
            }
        }
    }

    private void processOptionalNodes() {
        List<ExpansionNode> nodesToProcess = new ArrayList<>(optionalNodes);
        optionalNodes.clear();

        for (ExpansionNode node : nodesToProcess) {
            if (!node.isConsumed() && random.nextFloat() < OPTIONAL_NODE_PROBABILITY) {
                BaseSnail snail = createRandomSnailForNode(node);
                if (snail != null) {
                    executeSnailWithNodes(snail);
                    node.consume();
                }
            }
        }
    }

    private BaseSnail createRandomSnailForNode(ExpansionNode node) {
        Vector3 pos = node.getPosition();
        Direction dir = node.getDirection();
        return snailRegistry.createRandomSnail(map, pos, dir, random);
    }

    private boolean hasNecessaryNodes() {
        return !necessaryNodes.isEmpty();
    }

    private boolean shouldAddMoreOptionalNodes() {
        int currentTiles = map.getAllTiles().size();
        boolean hasOptionalNodes = !optionalNodes.isEmpty();
        boolean underMaxSize = currentTiles < MAX_MAP_SIZE;
        boolean underMinSize = currentTiles < MIN_MAP_SIZE;

        // Force expansion if under minimum size and we have any nodes available
        if (underMinSize && hasOptionalNodes) {
            return true;
        }

        // Continue with optional nodes if under max size and nodes available
        return hasOptionalNodes && underMaxSize;
    }

    private void addSpawnPoint(Vector3 startPos) {
        // Player spawns at standing height (1 tile above floor)
        Vector3 spawnPos = new Vector3(startPos.x, startPos.y + 1, startPos.z);
        map.registerHint(new SpawnPointHint(map.constructKeyFromIndexCoordinates((int)spawnPos.x,(int)spawnPos.y,(int)spawnPos.z)));

        System.out.println("SnailMapGenerator: Added spawn point at " + spawnPos + " (1 tile above floor)");
    }

    /**
     * Inject expansion nodes at promising wall locations when the map is too small
     */
    private void injectExpansionNodes() {
        System.out.println("SnailMapGenerator: Injecting expansion nodes - current size: " + map.getAllTiles().size());

        List<Vector3> wallPositions = findWallExpansionOpportunities();
        int nodesInjected = 0;
        int maxInject = Math.min(5, wallPositions.size()); // Don't inject too many at once

        for (int i = 0; i < maxInject; i++) {
            Vector3 wallPos = wallPositions.get(random.nextInt(wallPositions.size()));
            Direction expansionDir = findBestExpansionDirection(wallPos);

            if (expansionDir != null) {
                // Create expansion node just outside the wall
                Vector3 expansionPos = wallPos.cpy();
                Direction.advanceVector(expansionDir, expansionPos);

                ExpansionNode injectedNode = new ExpansionNode(
                    expansionPos,
                    expansionDir,
                    ExpansionNode.Priority.OPTIONAL,
                    "WallInjection"
                );

                optionalNodes.add(injectedNode);
                nodesInjected++;
                System.out.println("SnailMapGenerator: Injected expansion node at " + expansionPos + " facing " + expansionDir);
            }

            wallPositions.remove(wallPos); // Don't use same position twice
        }

        System.out.println("SnailMapGenerator: Injected " + nodesInjected + " expansion nodes");
    }

    /**
     * Find wall positions that could be good expansion points
     */
    private List<Vector3> findWallExpansionOpportunities() {
        List<Vector3> opportunities = new ArrayList<>();
        List<com.badlogic.gdx.math.Vector3> allTiles = new ArrayList<>();

        // Convert existing tiles to coordinate list
        map.getAllTiles().forEach(tile -> {
            Vector3 tileCoord = new Vector3(
                Math.round(tile.x / 16f), // Convert world coords to tile coords
                Math.round(tile.y / 16f),
                Math.round(tile.z / 16f)
            );
            allTiles.add(tileCoord);
        });

        // Check each existing tile for expansion opportunities
        for (Vector3 tilePos : allTiles) {
            // Check the 4 cardinal directions from this tile
            Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

            for (Direction dir : directions) {
                Vector3 adjacentPos = tilePos.cpy();
                Direction.advanceVector(dir, adjacentPos);

                // If this adjacent position is empty and has some space around it, it's an opportunity
                if (map.getTile((int)adjacentPos.x, (int)adjacentPos.y, (int)adjacentPos.z) != null) {
                    continue; // Skip if tile already exists
                }

                // Check if there's space for expansion (at least 3 tiles in expansion direction)
                if (hasExpansionSpace(adjacentPos, dir, 3)) {
                    opportunities.add(adjacentPos);
                }
            }
        }

        System.out.println("SnailMapGenerator: Found " + opportunities.size() + " wall expansion opportunities");
        return opportunities;
    }

    /**
     * Check if there's enough space in a direction for expansion
     */
    private boolean hasExpansionSpace(Vector3 startPos, Direction direction, int requiredSpace) {
        Vector3 checkPos = startPos.cpy();

        for (int i = 0; i < requiredSpace; i++) {
            if (map.getTile((int)checkPos.x, (int)checkPos.y, (int)checkPos.z) != null) {
                return false; // Hit existing tile
            }
            Direction.advanceVector(direction, checkPos);
        }

        return true;
    }

    /**
     * Find the best direction to expand from a wall position
     */
    private Direction findBestExpansionDirection(Vector3 wallPos) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        List<Direction> validDirections = new ArrayList<>();

        for (Direction dir : directions) {
            if (hasExpansionSpace(wallPos, dir, 5)) { // Need at least 5 tiles of space
                validDirections.add(dir);
            }
        }

        if (validDirections.isEmpty()) {
            return null;
        }

        // Return a random valid direction
        return validDirections.get(random.nextInt(validDirections.size()));
    }

    private void addLight(Vector3 lightPos) {
        // Ensure light tile exists
        map.touchTile(lightPos);

        // Create light hint at position
        LightHint lightHint = new LightHint(map.constructKeyFromIndexCoordinates(
            (int)lightPos.x, (int)lightPos.y, (int)lightPos.z));
        lightHint.color_r = 0.8f;  // Warm white light
        lightHint.color_g = 0.7f;
        lightHint.color_b = 0.5f;
        lightHint.intensity = 3f;  // Much lower intensity
        lightHint.flicker = LightPresets.LIGHT_FLICKER_1;

        map.registerHint(lightHint);
        System.out.println("SnailMapGenerator: Added light at " + lightPos);
    }
}
