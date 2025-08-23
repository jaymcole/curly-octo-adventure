package curly.octo.map.enums;

import com.badlogic.gdx.math.Vector3;

public enum Direction {
    NORTH,
    EAST,
    WEST,
    SOUTH,
    UP,
    DOWN;

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
        }
        return next;
    }

    public static void advanceVector(Direction direction, Vector3 coordinate) {
        switch (direction) {
            case NORTH:
                coordinate.x++;
                break;
            case EAST:
                coordinate.z++;
                break;
            case WEST:
                coordinate.z--;
                break;
            case SOUTH:
                coordinate.x--;
                break;
            case UP:
                coordinate.y++;
                break;
            case DOWN:
                coordinate.y--;
                break;
        }
    }

}
