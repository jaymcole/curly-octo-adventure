package curly.octo.map.enums;

public enum CardinalDirection {
    NORTH,
    EAST,
    WEST,
    SOUTH;

    public static CardinalDirection rotateClockwise(CardinalDirection direction) {
        CardinalDirection next = NORTH;
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

    public static CardinalDirection rotateCounterClockwise(CardinalDirection direction) {
        CardinalDirection next = NORTH;
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

}
