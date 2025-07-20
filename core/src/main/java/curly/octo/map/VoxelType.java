package curly.octo.map;

/**
 * Represents different types of voxels in our dungeon.
 */
public enum VoxelType {
    AIR,        // Empty space
    STONE,      // Wall/floor material
    DIRT,       // Alternative floor material
    DOOR,       // Door between rooms
    SPAWN_POINT // Player spawn location
}
