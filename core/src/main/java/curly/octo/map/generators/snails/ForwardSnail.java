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
    private int ceilingHeight;
    private boolean produceExpansionNode;

    public ForwardSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance, int ceilingHeight, boolean produceExpansionNode) {
        super(map, coordinate, direction, random);
        this.remainingDistance = distance;
        this.ceilingHeight = ceilingHeight;
        this.produceExpansionNode = produceExpansionNode;
    }

    public ForwardSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance, int ceilingHeight) {
        super(map, coordinate, direction, random);
        new ForwardSnail(map, coordinate, direction, random, distance, ceilingHeight, false);
    }

    @Override
    protected SnailResult doStep() {
        if (remainingDistance <= 0) {
            complete = true;
            return SnailResult.COMPLETE;
        }

        // Create vertical corridor space (3 tiles high starting from current level)
        markTileAsPartOfMap(); // Floor level
        for(int i = 0; i < ceilingHeight; i++) {
            markTileAsPartOfMap(new Vector3(coordinate.x, coordinate.y + i, coordinate.z)); // Middle
        }


        // Move forward
        Direction.advanceVector(direction, coordinate);
        remainingDistance--;

        if (remainingDistance <= 0) {
            complete = true;
            // Create expansion node at the end of the corridor (NECESSARY to avoid dead ends)
            if (produceExpansionNode) {
                ExpansionNode endNode = new ExpansionNode(
                    coordinate.cpy(),
                    direction,
                    ExpansionNode.Priority.NECESSARY,
                    "ForwardSnail"
                );
                return SnailResult.withExpansionNodes(true, endNode);
            }
        }

        return SnailResult.CONTINUE;
    }

    @Override
    public BaseSnail createCopy() {
        return new ForwardSnail(map, coordinate.cpy(), direction, random, remainingDistance, ceilingHeight);
    }

}
