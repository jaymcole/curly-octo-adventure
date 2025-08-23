package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

/**
 * A snail that probabilistically spawns another snail.
 * Useful for adding random features like side passages or decorations.
 */
public class SpawnSnail extends BaseSnail {

    private final float probability; // 0.0 to 1.0
    private final BaseSnail snailToSpawn;
    private boolean decided = false;

    public SpawnSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, 
                     float probability, BaseSnail snailToSpawn) {
        super(map, coordinate, direction, random);
        this.probability = Math.max(0.0f, Math.min(1.0f, probability));
        this.snailToSpawn = snailToSpawn;
    }

    @Override
    protected SnailResult doStep() {
        if (decided) {
            complete = true;
            return SnailResult.COMPLETE;
        }
        
        decided = true;
        complete = true;
        
        // Roll probability
        if (random.nextFloat() < probability) {
            // Spawn the snail at current position
            BaseSnail spawn = snailToSpawn.createCopy();
            spawn.coordinate = this.coordinate.cpy();
            spawn.direction = this.direction;
            
            return SnailResult.spawn(spawn);
        } else {
            // Don't spawn anything
            return SnailResult.COMPLETE;
        }
    }

    @Override
    public BaseSnail createCopy() {
        SpawnSnail copy = new SpawnSnail(map, coordinate.cpy(), direction, random, 
                                        probability, snailToSpawn.createCopy());
        copy.decided = this.decided;
        return copy;
    }
}