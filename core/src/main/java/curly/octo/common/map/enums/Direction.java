package curly.octo.common.map.enums;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;

public enum Direction {
    NORTH,
    EAST,
    WEST,
    SOUTH,
    UP,
    DOWN;

    public static Direction rotate(Direction direction, Turn turnDirection) {
        switch (turnDirection) {
            case CLOCKWISE:
                return rotateClockwise(direction);
            case COUNTERCLOCKWISE:
                return rotateCounterClockwise(direction);
            default:
                Log.error("Bro, you forgot to implement a turn direction in Direction.java");
                return direction;
        }
    }

    public static Direction rotateClockwise(Direction direction) {
        Direction next = NORTH;
        switch(direction) {
            case NORTH:
                next = EAST;
                break;
            case EAST:
                next = SOUTH;
                break;
            case SOUTH:
                next = WEST;
                break;
            case WEST:
                next = NORTH;
                break;
        }
        return next;
    }

    public static Direction rotateCounterClockwise(Direction direction) {
        Direction next = NORTH;
        switch(direction) {
            case NORTH:
                next = WEST;
                break;
            case EAST:
                next = NORTH;
                break;
            case SOUTH:
                next = EAST;
                break;
            case WEST:
                next = SOUTH;
                break;
        }
        return next;
    }

    public static Vector3 advanceVector(Direction direction, Vector3 coordinate) {
        switch (direction) {
            case NORTH:
                coordinate.z++;
                break;
            case EAST:
                coordinate.x++;
                break;
            case WEST:
                coordinate.x--;
                break;
            case SOUTH:
                coordinate.z--;
                break;
            case UP:
                coordinate.y++;
                break;
            case DOWN:
                coordinate.y--;
                break;
        }
        return coordinate;
    }

}
