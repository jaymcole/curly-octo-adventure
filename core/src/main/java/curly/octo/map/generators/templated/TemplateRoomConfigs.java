package curly.octo.map.generators.templated;

public class TemplateRoomConfigs {
    // Collections this room can connect to
    public String[] validCollections;

    // Specific room templates that this room can connect to
    public String[] validConnections;

    // Weight to be used when making random choice
    public float roomWeight;
}
