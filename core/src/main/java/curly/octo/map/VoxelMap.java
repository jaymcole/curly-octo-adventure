package curly.octo.map;

import com.badlogic.gdx.math.Vector3;
import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class VoxelMap {
    private int width;
    private int height;
    private int depth;
    private VoxelType[][][] map;
    private transient Random random;
    private long seed;

    // Default constructor required for Kryo
    public VoxelMap() {
        // Initialize with minimum size, will be replaced by deserialization
        this(1, 1, 1, 0);
    }

    public VoxelMap(int width, int height, int depth, long seed) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.seed = seed;
        this.map = new VoxelType[width][height][depth];
        this.random = new Random(seed);

        // Initialize the entire map as air
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    map[x][y][z] = VoxelType.AIR;
                }
            }
        }
    }

    /**
     * Generates a dungeon with rooms and corridors.
     */
    public void generateDungeon() {
        // Generate a few random rooms
        int roomCount = 5 + random.nextInt(5); // 5-10 rooms
        Vector3[] roomCenters = new Vector3[roomCount];

        for (int i = 0; i < roomCount; i++) {
            // Generate room dimensions (odd numbers for symmetry)
            int roomWidth = 5 + 2 * random.nextInt(4);  // 5, 7, 9, or 11
            int roomDepth = 5 + 2 * random.nextInt(4);  // 5, 7, 9, or 11
            int roomHeight = 3 + 2 * random.nextInt(2); // 3 or 5

            // Generate room position (ensure it's within bounds)
            int x = 2 + random.nextInt(width - roomWidth - 4);
            int y = 2 + random.nextInt(height - roomHeight - 4);
            int z = 2 + random.nextInt(depth - roomDepth - 4);

            // Create the room
            createRoom(x, y, z, roomWidth, roomHeight, roomDepth);
            roomCenters[i] = new Vector3(x + roomWidth / 2f, y + roomHeight / 2f, z + roomDepth / 2f);

            // Connect to previous room if it exists
            if (i > 0) {
                connectRooms(roomCenters[i-1], roomCenters[i]);
            }
        }

        // Set spawn point in the first room
        if (roomCenters.length > 0) {
            setSpawnPoint(roomCenters[0]);
        }
    }

    private void createRoom(int x, int y, int z, int width, int height, int depth) {
        // Floor and ceiling
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                // Floor
                setVoxel(x + dx, y, z + dz, VoxelType.STONE);
                // Ceiling
                setVoxel(x + dx, y + height - 1, z + dz, VoxelType.STONE);
            }
        }

        // Walls
        for (int dy = 1; dy < height - 1; dy++) {
            for (int dx = 0; dx < width; dx++) {
                // Front and back walls
                setVoxel(x + dx, y + dy, z, VoxelType.STONE);
                setVoxel(x + dx, y + dy, z + depth - 1, VoxelType.STONE);
            }
            for (int dz = 1; dz < depth - 1; dz++) {
                // Left and right walls
                setVoxel(x, y + dy, z + dz, VoxelType.STONE);
                setVoxel(x + width - 1, y + dy, z + dz, VoxelType.STONE);
            }
        }
    }

    private void connectRooms(Vector3 start, Vector3 end) {
        // Simple corridor generation - connect centers with straight lines
        int x1 = (int)start.x, y1 = (int)start.y, z1 = (int)start.z;
        int x2 = (int)end.x, y2 = (int)end.y, z2 = (int)end.z;

        // Move in x direction
        while (x1 != x2) {
            x1 += (x1 < x2) ? 1 : -1;
            setVoxel(x1, y1, z1, VoxelType.DIRT); // Floor
            setVoxel(x1, y1 + 1, z1, VoxelType.AIR); // Clear space above
            setVoxel(x1, y1 + 2, z1, VoxelType.AIR); // Clear space above
        }

        // Then in z direction
        while (z1 != z2) {
            z1 += (z1 < z2) ? 1 : -1;
            setVoxel(x1, y1, z1, VoxelType.DIRT);
            setVoxel(x1, y1 + 1, z1, VoxelType.AIR);
            setVoxel(x1, y1 + 2, z1, VoxelType.AIR);
        }
    }

    private void setSpawnPoint(Vector3 position) {
        int x = (int)position.x;
        int y = (int)position.y;
        int z = (int)position.z;
        setVoxel(x, y, z, VoxelType.SPAWN_POINT);
    }

    public VoxelType getVoxel(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            return VoxelType.AIR; // Out of bounds is considered air
        }
        return map[x][y][z];
    }

    private void setVoxel(int x, int y, int z, VoxelType type) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            map[x][y][z] = type;
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public long getSeed() { return seed; }

    /**
     * Called after Kryo deserialization to initialize transient fields.
     */
    public void postDeserialize() {
        // Reinitialize the random number generator
        this.random = new Random(seed);
    }
}
