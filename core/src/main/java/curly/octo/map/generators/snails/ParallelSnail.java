package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A snail that executes a primary snail, then spawns additional snails when it completes.
 * Enables fluent spawning: snail1.spawn(snail2, snail3)
 */
public class ParallelSnail extends BaseSnail {

    private BaseSnail primarySnail;
    private BaseSnail[] spawnSnails;
    private boolean hasSpawned = false;

    public ParallelSnail(GameMap map, Vector3 coordinate, Direction direction, Random random,
                        BaseSnail primary, BaseSnail... spawns) {
        super(map, coordinate, direction, random);
        this.primarySnail = primary.createCopy();
        this.spawnSnails = new BaseSnail[spawns.length];
        
        for (int i = 0; i < spawns.length; i++) {
            this.spawnSnails[i] = spawns[i].createCopy();
        }
        
        // Set primary snail to our position
        this.primarySnail.coordinate = coordinate.cpy();
        this.primarySnail.direction = direction;
    }

    @Override
    protected SnailResult doStep() {
        if (complete) {
            return SnailResult.COMPLETE;
        }
        
        SnailResult result = primarySnail.execute();
        
        // Update our position to match primary snail
        this.coordinate = primarySnail.coordinate.cpy();
        this.direction = primarySnail.direction;
        
        if (result.isComplete() && !hasSpawned) {
            // Primary snail finished, spawn the others at final position
            hasSpawned = true;
            complete = true;
            
            List<BaseSnail> toSpawn = new ArrayList<>();
            
            // Add any snails the primary snail spawned
            toSpawn.addAll(result.getSpawnedSnails());
            
            // Add our spawn snails
            for (BaseSnail spawn : spawnSnails) {
                BaseSnail spawnCopy = spawn.createCopy();
                spawnCopy.coordinate = this.coordinate.cpy();
                spawnCopy.direction = this.direction;
                toSpawn.add(spawnCopy);
            }
            
            return SnailResult.spawn(toSpawn.toArray(new BaseSnail[0]));
        }
        
        // Forward result from primary snail
        return result;
    }

    @Override
    public BaseSnail createCopy() {
        BaseSnail[] spawnCopies = new BaseSnail[spawnSnails.length];
        for (int i = 0; i < spawnSnails.length; i++) {
            spawnCopies[i] = spawnSnails[i].createCopy();
        }
        
        ParallelSnail copy = new ParallelSnail(map, coordinate.cpy(), direction, random,
                                              primarySnail.createCopy(), spawnCopies);
        copy.hasSpawned = this.hasSpawned;
        return copy;
    }
}