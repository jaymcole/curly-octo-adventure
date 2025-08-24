package curly.octo.map.generators.snails;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;
import lights.LightPresets;

import java.util.Random;

/**
 * A snail that excavates a rectangular room of specified dimensions.
 * The room is centered around the snail's starting position.
 */
public class RoomSnail extends BaseSnail {

    private final int width;
    private final int height;
    private final int depth;
    private boolean roomCreated = false;

    public RoomSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int width, int height, int depth) {
        super(map, coordinate, direction, random);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.depth = Math.max(1, depth);
    }

    @Override
    protected SnailResult doStep() {
        if (roomCreated) {
            complete = true;
            return SnailResult.COMPLETE;
        }

        // Check if room area is heavily occupied - only terminate if most of the room would overlap
        Vector3 floorCenter = coordinate.cpy();
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        
        // Check multiple positions within the room area
        Vector3[] checkPositions = {
            floorCenter.cpy(), // Center
            new Vector3(floorCenter.x - halfWidth + 1, floorCenter.y, floorCenter.z - halfDepth + 1), // SW interior
            new Vector3(floorCenter.x + halfWidth - 1, floorCenter.y, floorCenter.z - halfDepth + 1), // SE interior
            new Vector3(floorCenter.x - halfWidth + 1, floorCenter.y, floorCenter.z + halfDepth - 1), // NW interior
            new Vector3(floorCenter.x + halfWidth - 1, floorCenter.y, floorCenter.z + halfDepth - 1)  // NE interior
        };
        
        int occupiedCount = 0;
        for (Vector3 checkPos : checkPositions) {
            if (tileExists(checkPos)) {
                occupiedCount++;
            }
        }
        
        // Only terminate if more than half the check positions are occupied (heavy overlap)
        // But be more lenient if the map is still small
        int threshold = shouldBeLenientWithCollisions() ? checkPositions.length - 1 : checkPositions.length / 2;
        if (occupiedCount > threshold) {
            System.out.println("RoomSnail: Terminating - " + occupiedCount + "/" + checkPositions.length + " positions occupied at " + floorCenter + " (threshold=" + threshold + ")");
            complete = true;
            return SnailResult.COMPLETE;
        }

        int tilesCreated = 0;
        // Mark all tiles in the room - floor starts at current Y, builds up
        for (int x = -halfWidth; x <= (width - halfWidth - 1); x++) {
            for (int y = 0; y < height; y++) { // Start at floor (Y=0 relative), build up
                for (int z = -halfDepth; z <= (depth - halfDepth - 1); z++) {
                    Vector3 tilePos = new Vector3(
                        floorCenter.x + x,
                        floorCenter.y + y,  // Floor level + height offset
                        floorCenter.z + z
                    );
                    markTileAsPartOfMap(tilePos);
                    tilesCreated++;
                }
            }
        }

        // Create expansion nodes on room walls
        ExpansionNode[] expansionNodes = createRoomExpansionNodes(floorCenter);

        System.out.println("RoomSnail: Created " + width + "x" + height + "x" + depth +
                          " room at " + floorCenter + " (" + tilesCreated + " tiles, " +
                          expansionNodes.length + " expansion nodes)");

        roomCreated = true;
        complete = true;
        return SnailResult.withExpansionNodes(true, expansionNodes);
    }

    private ExpansionNode[] createRoomExpansionNodes(Vector3 center) {
        java.util.List<ExpansionNode> nodes = new java.util.ArrayList<>();

        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Add expansion nodes on each wall of the room (at floor level Y=0 for consistent building)
        // North wall
        Vector3 northPos = new Vector3(center.x, center.y, center.z + halfDepth + 1);
        nodes.add(new ExpansionNode(northPos, Direction.NORTH, ExpansionNode.Priority.OPTIONAL, "RoomSnail"));

        // South wall
        Vector3 southPos = new Vector3(center.x, center.y, center.z - halfDepth - 1);
        nodes.add(new ExpansionNode(southPos, Direction.SOUTH, ExpansionNode.Priority.OPTIONAL, "RoomSnail"));

        // East wall
        Vector3 eastPos = new Vector3(center.x + halfWidth + 1, center.y, center.z);
        nodes.add(new ExpansionNode(eastPos, Direction.EAST, ExpansionNode.Priority.OPTIONAL, "RoomSnail"));

        // West wall
        Vector3 westPos = new Vector3(center.x - halfWidth - 1, center.y, center.z);
        nodes.add(new ExpansionNode(westPos, Direction.WEST, ExpansionNode.Priority.OPTIONAL, "RoomSnail"));

        return nodes.toArray(new ExpansionNode[0]);
    }

    @Override
    public BaseSnail createCopy() {
        RoomSnail copy = new RoomSnail(map, coordinate.cpy(), direction, random, width, height, depth);
        copy.roomCreated = this.roomCreated;
        return copy;
    }
}
