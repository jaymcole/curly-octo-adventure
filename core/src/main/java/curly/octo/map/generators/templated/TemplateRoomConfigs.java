package curly.octo.map.generators.templated;

import curly.octo.map.enums.Direction;

public class TemplateRoomConfigs {
    // Collections this room can connect to
    public String[] validCollections;

    // Specific room templates that this room can connect to
    public String[] validConnections;

    public Direction[] exitDirections;
}
