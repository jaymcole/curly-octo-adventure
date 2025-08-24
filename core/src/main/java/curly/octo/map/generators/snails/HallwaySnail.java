package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;

import java.util.Random;

public class HallwaySnail extends BaseSnail{

    public int distance;
    public int hallwayWidth;
    public int ceilingHeight;

    public HallwaySnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int distance, int ceilingHeight, int hallwayWidth) {
        super(map, coordinate, direction, random);
        this.distance = distance;
        this.hallwayWidth = hallwayWidth;
        this.ceilingHeight = ceilingHeight;
    }

    @Override
    protected SnailResult doStep() {
        java.util.List<ExpansionNode> expansionNodes = new java.util.ArrayList<>();

        // Create a wide hallway by marking tiles across the width at each step
        for(int i = 0; i < distance; i++) {
            // Only check for collisions after we've made some progress (allow connecting to existing structures)
            // Be more lenient when the map is still small
            Vector3 checkPos = coordinate.cpy();
            int minStepsBeforeCollision = shouldBeLenientWithCollisions() ? Math.max(5, distance / 2) : 2;
            if (i > minStepsBeforeCollision && tileExists(checkPos)) {
                // Still create an expansion node at this connection point
                ExpansionNode connectionNode = new ExpansionNode(
                    coordinate.cpy(),
                    direction,
                    ExpansionNode.Priority.OPTIONAL,
                    "HallwaySnail-connection"
                );
                expansionNodes.add(connectionNode);
                complete = true;
                return SnailResult.withExpansionNodes(true, expansionNodes.toArray(new ExpansionNode[0]));
            }

            // Mark tiles across the width at current position
            Vector3 leftDirection = getPerpendicularVector(Direction.rotateCounterClockwise(direction));
            Vector3 rightDirection = getPerpendicularVector(Direction.rotateClockwise(direction));

            int halfWidth = hallwayWidth / 2;

            // Mark center and tiles to left and right
            for(int w = -halfWidth; w <= halfWidth; w++) {
                Vector3 tilePos = coordinate.cpy();
                if(w < 0) {
                    // Add left offset
                    tilePos.add(leftDirection.cpy().scl(Math.abs(w)));
                } else if(w > 0) {
                    // Add right offset
                    tilePos.add(rightDirection.cpy().scl(w));
                }

                // Mark vertical space at this position
                for(int h = 0; h < ceilingHeight; h++) {
                    Vector3 heightPos = new Vector3(tilePos.x, tilePos.y + h, tilePos.z);
                    markTileAsPartOfMap(heightPos);
                }
            }

            // Occasionally add expansion nodes along the sides (not too frequently)
            if (i > 2 && i < distance - 2 && random.nextFloat() < 0.15f) {
                // Add expansion node on left side
                Vector3 leftPos = coordinate.cpy().add(leftDirection.cpy().scl(halfWidth + 1));
                expansionNodes.add(new ExpansionNode(
                    leftPos,
                    Direction.rotateCounterClockwise(direction),
                    ExpansionNode.Priority.OPTIONAL,
                    "HallwaySnail"
                ));
            }

            if (i > 2 && i < distance - 2 && random.nextFloat() < 0.15f) {
                // Add expansion node on right side
                Vector3 rightPos = coordinate.cpy().add(rightDirection.cpy().scl(halfWidth + 1));
                expansionNodes.add(new ExpansionNode(
                    rightPos,
                    Direction.rotateClockwise(direction),
                    ExpansionNode.Priority.OPTIONAL,
                    "HallwaySnail"
                ));
            }

            // Move forward along main direction
            Direction.advanceVector(direction, coordinate);
        }

        // Always add expansion node at the end of the hallway (NECESSARY to continue generation)
        ExpansionNode endNode = new ExpansionNode(
            coordinate.cpy(),
            direction,
            ExpansionNode.Priority.NECESSARY,
            "HallwaySnail"
        );
        expansionNodes.add(endNode);

        complete = true;
        return SnailResult.withExpansionNodes(true, expansionNodes.toArray(new ExpansionNode[0]));
    }

    private Vector3 getPerpendicularVector(Direction dir) {
        Vector3 vec = new Vector3();
        Direction.advanceVector(dir, vec);
        return vec;
    }

    @Override
    public BaseSnail createCopy() {
        return new HallwaySnail(map, coordinate.cpy(), direction, random, distance, ceilingHeight, hallwayWidth);
    }
}
