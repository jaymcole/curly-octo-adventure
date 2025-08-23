package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.Turn;
import curly.octo.map.generators.snails.*;
import curly.octo.map.hints.SpawnPointHint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A map generator that uses the "snails" system to create interesting dungeon layouts.
 * Snails are simple agents that move around and mark tiles as part of the map.
 */
public class SnailMapGenerator extends MapGenerator {

    public SnailMapGenerator(Random random, GameMap gameMap) {
        super(random, gameMap);
    }

    @Override
    public void generate() {
        // Start with a simple branching dungeon pattern - spawn at ground floor level
        Vector3 startPos = new Vector3(0, 0, 0);  // Y=0 is floor level for snails
        Direction startDir = Direction.NORTH;

        // Create the initial snail with a complex behavior
        BaseSnail initialSnail = createMainPath(startPos, startDir);

        // Execute all snails until completion
        executeSnails(initialSnail);

        RoomSnail startRoom = new RoomSnail(map, startPos, Direction.NORTH, random, 5, 5);
        executeSnails(startRoom);
        // Add spawn point at origin
        addSpawnPoint(startPos);

        // Close the map by creating walls around all open spaces
        closeMap();

        // Debug output
        System.out.println("SnailMapGenerator: Created " + map.getAllTiles().size() + " total tiles");
    }

    private BaseSnail createMainPath(Vector3 startPos, Direction startDir) {
        // Start with a simpler pattern for debugging
        return new ForwardSnail(map, startPos, startDir, random, 8)
            .then(new TurnSnail(map, startPos, startDir, random, Turn.CLOCKWISE))
            .then(new ForwardSnail(map, startPos, startDir, random, 5))
            .then(new RoomSnail(map, startPos, startDir, random, 6, 6))
            .then(new TurnSnail(map, startPos, startDir, random, Turn.CLOCKWISE))
            .then(new ForwardSnail(map, startPos, startDir, random, 4))
            .spawn(
                new TurnSnail(map, startPos, startDir, random, Turn.COUNTERCLOCKWISE)
                    .then(new ForwardSnail(map, startPos, startDir, random, 6)),
                new TurnSnail(map, startPos, startDir, random, Turn.CLOCKWISE)
                    .then(new ForwardSnail(map, startPos, startDir, random, 6))
            );
    }

    private void executeSnails(BaseSnail initialSnail) {
        List<BaseSnail> activeSnails = new ArrayList<>();
        activeSnails.add(initialSnail);

        int maxSteps = 1000; // Safety limit
        int steps = 0;

        while (!activeSnails.isEmpty() && steps < maxSteps) {
            List<BaseSnail> nextGeneration = new ArrayList<>();

            for (BaseSnail snail : activeSnails) {
                if (!snail.isDone()) {
                    SnailResult result = snail.execute();

                    if (!result.isComplete()) {
                        // Snail continues, keep it for next iteration
                        nextGeneration.add(snail);
                    }

                    // Add any spawned snails
                    nextGeneration.addAll(result.getSpawnedSnails());
                }
            }

            activeSnails = nextGeneration;
            steps++;
        }

        if (steps >= maxSteps) {
            System.err.println("Warning: Snail generation reached maximum steps limit");
        }

        System.out.println("SnailMapGenerator: Executed " + steps + " steps with " +
                          (steps >= maxSteps ? "MAX STEPS REACHED" : "completion"));
    }

    private void addSpawnPoint(Vector3 startPos) {
        // Player spawns at standing height (1 tile above floor)
        Vector3 spawnPos = new Vector3(startPos.x, startPos.y + 1, startPos.z);
//        MapTile spawnTile = map.touchTile(spawnPos);
//        spawnTile.AddHint(new SpawnPointHint());
        map.registerHint(new SpawnPointHint(map.constructKeyFromIndexCoordinates((int)spawnPos.x,(int)spawnPos.y,(int)spawnPos.z)));

        // Player spawns at standing height (1 tile above floor)
//        Vector3 spawnPos = new Vector3(startPos.x, startPos.y + 1, startPos.z);
//
//        // Ensure spawn tile exists (should already exist from room/corridor generation)
//        map.touchTile(spawnPos);
//
//        // Add spawn point hint at standing height
//        Long spawnKey = map.constructKeyFromIndexCoordinates(
//            (int)spawnPos.x, (int)spawnPos.y, (int)spawnPos.z);
//        map.registerHint(new SpawnPointHint(spawnKey));
//
//        System.out.println("SnailMapGenerator: Added spawn point at " + spawnPos + " (1 tile above floor)");
    }
}
