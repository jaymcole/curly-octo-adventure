package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

/**
 * A snail that moves forward in its current direction for a specified distance,
 * marking tiles as part of the map along the way.
 */
public class ForwardSnail extends BaseSnail {

    private int remainingDistance;

    public ForwardSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance) {
        super(map, coordinate, direction, random);
        this.remainingDistance = distance;
    }

    @Override
    protected SnailResult doStep() {
        if (remainingDistance <= 0) {
            complete = true;
            return SnailResult.COMPLETE;
        }
        
        // Create vertical corridor space (3 tiles high starting from current level)
        markTileAsPartOfMap(); // Floor level
        markTileAsPartOfMap(new Vector3(coordinate.x, coordinate.y + 1, coordinate.z)); // Middle
        markTileAsPartOfMap(new Vector3(coordinate.x, coordinate.y + 2, coordinate.z)); // Top
        
        // Move forward
        Direction.advanceVector(direction, coordinate);
        remainingDistance--;
        
        if (remainingDistance <= 0) {
            complete = true;
            return SnailResult.COMPLETE;
        }
        
        return SnailResult.CONTINUE;
    }

    @Override
    public BaseSnail createCopy() {
        return new ForwardSnail(map, coordinate.cpy(), direction, random, remainingDistance);
    }

}
