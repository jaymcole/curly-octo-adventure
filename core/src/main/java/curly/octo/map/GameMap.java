package curly.octo.map;

import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;

import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class GameMap {
    private int width;
    private int height;
    private int depth;
    private MapTile[][][] map;
    private transient Random random;
    private long seed;

    // Default constructor required for Kryo
    public GameMap() {
        // Initialize with minimum size, will be replaced by deserialization
        this(1, 1, 1, 0);
    }

    public GameMap(int width, int height, int depth, long seed) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.seed = seed;
        this.map = new MapTile[width][height][depth];
        this.random = new Random(seed);

        // Initialize the entire map as empty air
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    MapTile tile = new MapTile();
                    tile.x = x * MapTile.TILE_SIZE;
                    tile.y = y * MapTile.TILE_SIZE;
                    tile.z = z * MapTile.TILE_SIZE;
                    map[x][y][z] = tile;
                }
            }
        }
    }

    /**
     * Generates a dungeon with rooms and corridors.
     */
    public void generateDungeon() {
        int xIndex = 6;
        int zIndex = 0;
        for (MapTileGeometryType type : MapTileGeometryType.values()) {
            for (MapTileFillType fill : MapTileFillType.values()) {
                map[xIndex][0][zIndex].geometryType = type;
                map[xIndex][0][zIndex].fillType = fill;
                zIndex += 2;
            }
            zIndex = 0;
            xIndex += 2;
        }

        map[2][0][1].direction = CardinalDirection.NORTH;
        map[2][0][1].geometryType = MapTileGeometryType.SLAT;

        map[1][0][2].direction = CardinalDirection.EAST;
        map[1][0][2].geometryType = MapTileGeometryType.SLAT;

        map[0][0][1].direction = CardinalDirection.SOUTH;
        map[0][0][1].geometryType = MapTileGeometryType.SLAT;

        map[1][0][0].direction = CardinalDirection.WEST;
        map[1][0][0].geometryType = MapTileGeometryType.SLAT;


        map[1][0][1].geometryType = MapTileGeometryType.FULL;
    }

    public MapTile getTile(int x, int y, int z) {
        return map[x][y][z];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public long getSeed() { return seed; }
}
