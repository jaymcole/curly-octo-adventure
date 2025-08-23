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

    public RoomSnail(GameMap map, Vector3 coordinate, Direction direction, Random random, int width, int height) {
        this(map, coordinate, direction, random, width, height, 3); // Default to 3 tiles high for player space
    }

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

        // Create room with floor at current position, building upward
        Vector3 floorCenter = coordinate.cpy();

        int halfWidth = width / 2;
        int halfDepth = depth / 2;

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
        map.registerHint(new SpawnPointHint(map.constructKeyFromIndexCoordinates((int)floorCenter.x,(int)floorCenter.y - 1,(int)floorCenter.z)));
        System.out.println("RoomSnail: Created " + width + "x" + height + "x" + depth +
                          " room at " + floorCenter + " (" + tilesCreated + " tiles)");
        roomCreated = true;
        complete = true;
        return SnailResult.COMPLETE;
    }

    @Override
    public BaseSnail createCopy() {
        RoomSnail copy = new RoomSnail(map, coordinate.cpy(), direction, random, width, height, depth);
        copy.roomCreated = this.roomCreated;
        return copy;
    }
}
