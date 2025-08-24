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
    private static final int MAX_MAP_SIZE = 8000; // Maximum tiles to prevent infinite generation
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
        RoomSnail startRoom = new RoomSnail(map, startPos, Direction.NORTH, random, 7, 25, 7);
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

    private void generateExpansionBasedMap() {
        int iterations = 0;
        int maxIterations = 1000;

        while ((hasNecessaryNodes() || shouldAddMoreOptionalNodes()) && iterations < maxIterations) {
            // Process all necessary nodes first
            processNecessaryNodes();
            // Then process some optional nodes if map isn't large enough
            processOptionalNodes();
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
