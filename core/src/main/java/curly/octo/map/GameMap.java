package curly.octo.map;

import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;

import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class GameMap {
    private final int width;
    private final int height;
    private final int depth;
    private final MapTile[][][] map;
    private transient Random random;
    private final long seed;

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

        for(int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                map[x][0][z].geometryType = MapTileGeometryType.FULL;
                if (random.nextBoolean()) {
                    map[x][0][z].material = MapTileMaterial.GRASS;
                } else {
                    map[x][0][z].material = MapTileMaterial.DIRT;
                }
            }
        }

        int xIndex = 6;
        int zIndex = 0;
        for (MapTileGeometryType type : MapTileGeometryType.values()) {
            for (MapTileFillType fill : MapTileFillType.values()) {
                map[xIndex][1][zIndex].geometryType = type;
                map[xIndex][1][zIndex].fillType = fill;
                zIndex += 2;
            }
            zIndex = 0;
            xIndex += 2;
        }

        map[2][1][1].direction = CardinalDirection.NORTH;
        map[2][1][1].geometryType = MapTileGeometryType.SLAT;

        map[1][1][2].direction = CardinalDirection.EAST;
        map[1][1][2].geometryType = MapTileGeometryType.SLAT;

        map[0][1][1].direction = CardinalDirection.SOUTH;
        map[0][1][1].geometryType = MapTileGeometryType.SLAT;

        map[1][1][0].direction = CardinalDirection.WEST;
        map[1][1][0].geometryType = MapTileGeometryType.SLAT;


        map[1][1][1].geometryType = MapTileGeometryType.FULL;
    }

    public MapTile getTileFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)(worldX / MapTile.TILE_SIZE);
        int yIndex = (int)(worldY / MapTile.TILE_SIZE);
        int zIndex = (int)(worldZ / MapTile.TILE_SIZE);

        if (xIndex < 0 || xIndex >= width) {
          return null;
        } else if (yIndex < 0 || yIndex >= height) {
            return null;
        } else if (zIndex < 0 || zIndex >= depth) {
            return null;
        }
        return map[xIndex][yIndex][zIndex];
    }

    public MapTile getTile(int x, int y, int z) {
        return map[x][y][z];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
}
