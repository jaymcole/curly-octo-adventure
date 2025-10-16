package curly.octo.map.generators.kiss;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.enums.Direction;

public class KissEntrance {

    private final int entranceHash;
    public final Direction outwardFacingDirection;
    public final int offsetX, offsetY, offsetZ;
    public final KissTemplate associatedTemplate;

    public KissEntrance (int entranceHash, Vector3 offset, KissTemplate associatedTemplate, Direction outwardFacingDirection) {
        this.entranceHash = entranceHash;
        offsetX = (int)offset.x;
        offsetY = (int)offset.y;
        offsetZ = (int)offset.z;
        this.associatedTemplate = associatedTemplate;
        this.outwardFacingDirection = outwardFacingDirection;
    }

    public KissEntrance (int entranceHash, int offsetX, int offsetY, int offsetZ, KissTemplate associatedTemplate, Direction outwardFacingDirection) {
        this.entranceHash = entranceHash;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.associatedTemplate = associatedTemplate;
        this.outwardFacingDirection = outwardFacingDirection;
    }

    public String getKey(){
        return entranceHash + outwardFacingDirection.name();
    }

    public String getMatchingKey() {
        return entranceHash + getOppositeDirection(outwardFacingDirection).name();
    }

    private Direction getOppositeDirection(Direction direction) {
        switch(direction) {
            case NORTH: return Direction.SOUTH;
            case SOUTH: return Direction.NORTH;
            case EAST: return Direction.WEST;
            case WEST: return Direction.EAST;
            case UP: return Direction.DOWN;
            case DOWN: return Direction.UP;
            default: return direction;
        }
    }

}
