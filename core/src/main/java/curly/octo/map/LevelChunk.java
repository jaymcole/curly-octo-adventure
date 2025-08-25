package curly.octo.map;

import com.badlogic.gdx.math.Vector3;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a cubic chunk of MapTiles in the game world.
 * Chunks are used to organize tiles for efficient mesh generation and culling.
 *
 * Each chunk contains a 3D array of MapTiles and provides methods to:
 * - Store and retrieve tiles by local coordinates within the chunk
 * - Convert between world coordinates and local chunk coordinates
 * - Track whether the chunk contains any solid tiles worth rendering
 */
public class LevelChunk {

    public static final int CHUNK_SIZE = 16;

    private final MapTile[][][] tiles;
    private final Vector3 chunkCoordinates;
    private final Vector3 worldOffset;
    private boolean hasContent;
    private int solidTileCount;

    /**
     * Creates a new chunk at the specified chunk coordinates.
     *
     * @param chunkX The X coordinate of this chunk in chunk space
     * @param chunkY The Y coordinate of this chunk in chunk space
     * @param chunkZ The Z coordinate of this chunk in chunk space
     */
    public LevelChunk(int chunkX, int chunkY, int chunkZ) {
        this.tiles = new MapTile[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE];
        this.chunkCoordinates = new Vector3(chunkX, chunkY, chunkZ);
        this.worldOffset = new Vector3(
            chunkX * CHUNK_SIZE * MapTile.TILE_SIZE,
            chunkY * CHUNK_SIZE * MapTile.TILE_SIZE,
            chunkZ * CHUNK_SIZE * MapTile.TILE_SIZE
        );
        this.hasContent = false;
        this.solidTileCount = 0;
    }

    /**
     * Places a tile at the specified local coordinates within this chunk.
     *
     * @param localX Local X coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @param localY Local Y coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @param localZ Local Z coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @param tile The MapTile to place
     * @return true if the tile was successfully placed, false if coordinates are out of bounds
     */
    public boolean setTile(int localX, int localY, int localZ, MapTile tile) {
        if (!isValidLocalCoordinate(localX, localY, localZ)) {
            return false;
        }

        MapTile oldTile = tiles[localX][localY][localZ];
        tiles[localX][localY][localZ] = tile;

        updateContentFlags(oldTile, tile);
        return true;
    }

    /**
     * Retrieves a tile at the specified local coordinates within this chunk.
     *
     * @param localX Local X coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @param localY Local Y coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @param localZ Local Z coordinate within the chunk (0 to CHUNK_SIZE-1)
     * @return The MapTile at the specified coordinates, or null if empty or out of bounds
     */
    public MapTile getTile(int localX, int localY, int localZ) {
        if (!isValidLocalCoordinate(localX, localY, localZ)) {
            return null;
        }
        return tiles[localX][localY][localZ];
    }

    /**
     * Sets a tile using world coordinates. The method converts world coordinates
     * to local chunk coordinates and places the tile.
     *
     * @param worldX World X coordinate in tile units
     * @param worldY World Y coordinate in tile units
     * @param worldZ World Z coordinate in tile units
     * @param tile The MapTile to place
     * @return true if the tile was successfully placed, false if coordinates don't belong to this chunk
     */
    public boolean setTileByWorldCoordinates(int worldX, int worldY, int worldZ, MapTile tile) {
        Vector3 localCoords = worldToLocalCoordinates(worldX, worldY, worldZ);
        if (localCoords == null) {
            return false;
        }
        return setTile((int)localCoords.x, (int)localCoords.y, (int)localCoords.z, tile);
    }

    /**
     * Converts world tile coordinates to local chunk coordinates.
     *
     * @param worldX World X coordinate in tile units
     * @param worldY World Y coordinate in tile units
     * @param worldZ World Z coordinate in tile units
     * @return Vector3 with local coordinates, or null if the world coordinates don't belong to this chunk
     */
    public Vector3 worldToLocalCoordinates(int worldX, int worldY, int worldZ) {
        int expectedChunkX = Math.floorDiv(worldX, CHUNK_SIZE);
        int expectedChunkY = Math.floorDiv(worldY, CHUNK_SIZE);
        int expectedChunkZ = Math.floorDiv(worldZ, CHUNK_SIZE);

        if (expectedChunkX != (int)chunkCoordinates.x ||
            expectedChunkY != (int)chunkCoordinates.y ||
            expectedChunkZ != (int)chunkCoordinates.z) {
            return null;
        }

        int localX = worldX - ((int)chunkCoordinates.x * CHUNK_SIZE);
        int localY = worldY - ((int)chunkCoordinates.y * CHUNK_SIZE);
        int localZ = worldZ - ((int)chunkCoordinates.z * CHUNK_SIZE);

        return new Vector3(localX, localY, localZ);
    }

    /**
     * Gets all tiles in this chunk as a map of local coordinates to MapTiles.
     * Only returns non-null tiles.
     *
     * @return Map where keys are local coordinate strings "x,y,z" and values are MapTiles
     */
    public Map<String, MapTile> getAllTiles() {
        Map<String, MapTile> tileMap = new HashMap<>();

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    MapTile tile = tiles[x][y][z];
                    if (tile != null) {
                        tileMap.put(x + "," + y + "," + z, tile);
                    }
                }
            }
        }

        return tileMap;
    }

    /**
     * Checks if local coordinates are valid for this chunk.
     */
    private boolean isValidLocalCoordinate(int localX, int localY, int localZ) {
        return localX >= 0 && localX < CHUNK_SIZE &&
               localY >= 0 && localY < CHUNK_SIZE &&
               localZ >= 0 && localZ < CHUNK_SIZE;
    }

    /**
     * Updates the hasContent flag and solid tile count when tiles are added/removed.
     */
    private void updateContentFlags(MapTile oldTile, MapTile newTile) {
        if (oldTile != null && oldTile.geometryType != curly.octo.map.enums.MapTileGeometryType.EMPTY) {
            solidTileCount--;
        }

        if (newTile != null && newTile.geometryType != curly.octo.map.enums.MapTileGeometryType.EMPTY) {
            solidTileCount++;
            hasContent = true;
        } else if (solidTileCount <= 0) {
            hasContent = false;
            solidTileCount = 0;
        }
    }

    // Getters

    /**
     * @return The chunk coordinates in chunk space
     */
    public Vector3 getChunkCoordinates() {
        return new Vector3(chunkCoordinates);
    }

    /**
     * @return The world offset of this chunk (bottom-corner world position)
     */
    public Vector3 getWorldOffset() {
        return new Vector3(worldOffset);
    }

    /**
     * @return True if this chunk contains any solid (non-empty) tiles
     */
    public boolean hasContent() {
        return hasContent;
    }

    /**
     * @return The number of solid tiles in this chunk
     */
    public int getSolidTileCount() {
        return solidTileCount;
    }

    /**
     * @return The total capacity of this chunk
     */
    public int getCapacity() {
        return CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
    }

    /**
     * Calculates the load factor of this chunk (0.0 = empty, 1.0 = completely full).
     *
     * @return Load factor as a float between 0.0 and 1.0
     */
    public float getLoadFactor() {
        return (float) solidTileCount / getCapacity();
    }
}
