package curly.octo.map;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.map.exploration.TileExplorationManager;

import java.util.*;

/**
 * Manages the chunking system for a GameMap, converting tile data into organized chunks.
 *
 * This class provides methods to:
 * - Convert a HashMap<Long, MapTile> into a 3D array of LevelChunks
 * - Perform BFS to find reachable areas and organize tiles into chunks
 * - Calculate chunk boundaries and manage chunk allocation
 * - Provide utilities for converting between world coordinates and chunk coordinates
 */
public class ChunkManager {

    private final GameMap gameMap;
    private HashMap<Long, LevelChunk> chunks; // Use HashMap instead of 3D array
    private Vector3 worldBounds;
    private Vector3 minWorldCoords;
    private Vector3 maxWorldCoords;

    public ChunkManager(GameMap gameMap) {
        this.gameMap = gameMap;
        this.chunks = new HashMap<>();
        calculateBounds();
    }

    /**
     * Organizes all tiles from the GameMap into chunks using multi-pass BFS.
     * This method processes tiles discovered through multiple BFS passes,
     * ensuring that both reachable and isolated areas are handled.
     *
     * @return A set of LevelChunks that contain tiles, organized by reachability
     */
    public Set<LevelChunk> organizeIntoChunks() {
        Log.info("ChunkManager", "Starting chunk organization with multi-pass BFS");

        Set<LevelChunk> populatedChunks = new HashSet<>();

        // Use TileExplorationManager for multi-pass BFS
        TileExplorationManager explorationManager = new TileExplorationManager(gameMap);
        List<Set<MapTile>> allRegions = explorationManager.exploreAllRegions();

        // Process tiles from all discovered regions
        int regionNumber = 1;
        for (Set<MapTile> region : allRegions) {
            Log.info("ChunkManager", String.format("Processing region %d with %d tiles", regionNumber, region.size()));

            for (MapTile tile : region) {
                if (placeTileInChunk(tile)) {
                    LevelChunk chunk = getChunkForTile(tile);
                    if (chunk != null && chunk.hasContent()) {
                        populatedChunks.add(chunk);
                    }
                }
            }
            regionNumber++;
        }

        TileExplorationManager.ExplorationStats stats = explorationManager.getStats();
        Log.info("ChunkManager", String.format(
            "Chunk organization complete: %d populated chunks, %s",
            populatedChunks.size(), stats.toString()));

        // Log detailed placement statistics
        Log.info("ChunkManager", String.format(
            "Tile placement stats: SUCCESS=%d, SET_TILE_FAIL=%d. Created %d chunks total.",
            placeTileSuccessCount, setTileFailureCount, chunks.size()
        ));

        return populatedChunks;
    }


    /**
     * Places a tile in the appropriate chunk based on its world coordinates.
     *
     * @param tile The MapTile to place
     * @return true if the tile was successfully placed, false otherwise
     */
    // Counters for debugging
    private int placeTileSuccessCount = 0;
    private int setTileFailureCount = 0;

    private boolean placeTileInChunk(MapTile tile) {
        Vector3 tileCoords = getTileCoordinates(tile);
        Vector3 chunkCoords = worldToChunkCoordinates((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z);

        // With HashMap approach, we can always create a chunk, so chunkCoords is never null
        LevelChunk chunk = getOrCreateChunk((int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z);

        boolean success = chunk.setTileByWorldCoordinates((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, tile);
        if (success) {
            placeTileSuccessCount++;
            // Log first few successful placements for debugging
            if (placeTileSuccessCount <= 5) {
                Log.info("ChunkManager", String.format(
                    "SUCCESS: Placed tile %s at tile coords (%d,%d,%d) in chunk (%d,%d,%d)",
                    tile.geometryType.name(),
                    (int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z,
                    (int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z
                ));
            }
        } else {
            setTileFailureCount++;
            if (setTileFailureCount <= 3) {
                Log.warn("ChunkManager", String.format(
                    "Failed to set tile at tile coords (%d,%d,%d) in chunk (%d,%d,%d)",
                    (int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z,
                    (int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z
                ));
            }
        }

        return success;
    }

    /**
     * Gets the chunk that contains a specific tile.
     *
     * @param tile The tile to find the chunk for
     * @return The LevelChunk containing this tile, or null if not found
     */
    public LevelChunk getChunkForTile(MapTile tile) {
        Vector3 tileCoords = getTileCoordinates(tile);
        Vector3 chunkCoords = worldToChunkCoordinates((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z);

        if (chunkCoords == null) {
            return null;
        }

        return getChunk((int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z);
    }

    /**
     * Converts world tile coordinates to chunk coordinates.
     * Now handles negative coordinates properly using floor division.
     *
     * @param worldX World X coordinate in tile units
     * @param worldY World Y coordinate in tile units
     * @param worldZ World Z coordinate in tile units
     * @return Vector3 with chunk coordinates (never null with HashMap approach)
     */
    public Vector3 worldToChunkCoordinates(int worldX, int worldY, int worldZ) {
        // Use floor division to handle negative coordinates properly
        int chunkX = Math.floorDiv(worldX, LevelChunk.CHUNK_SIZE);
        int chunkY = Math.floorDiv(worldY, LevelChunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.CHUNK_SIZE);

        return new Vector3(chunkX, chunkY, chunkZ);
    }

    /**
     * Encode chunk coordinates into a long key for HashMap storage.
     * This allows us to store chunks at any coordinate, including negative ones.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkY Chunk Y coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Encoded long key
     */
    private long encodeChunkCoordinates(int chunkX, int chunkY, int chunkZ) {
        // Use the same encoding as GameMap for consistency
        // This supports coordinates from -1048576 to 1048575 in each dimension
        return (((long)chunkX & 0x1FFFFF) << 42) | (((long)chunkY & 0x1FFFFF) << 21) | ((long)chunkZ & 0x1FFFFF);
    }

    /**
     * Get or create a chunk at the specified chunk coordinates.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkY Chunk Y coordinate
     * @param chunkZ Chunk Z coordinate
     * @return LevelChunk at the specified coordinates
     */
    private LevelChunk getOrCreateChunk(int chunkX, int chunkY, int chunkZ) {
        long key = encodeChunkCoordinates(chunkX, chunkY, chunkZ);
        LevelChunk chunk = chunks.get(key);

        if (chunk == null) {
            chunk = new LevelChunk(chunkX, chunkY, chunkZ);
            chunks.put(key, chunk);
        }

        return chunk;
    }

    /**
     * Converts chunk coordinates to world coordinates (bottom corner of the chunk).
     *
     * @param chunkX Chunk X coordinate
     * @param chunkY Chunk Y coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Vector3 with world coordinates of the chunk's bottom corner
     */
    public Vector3 chunkToWorldCoordinates(int chunkX, int chunkY, int chunkZ) {
        int worldX = chunkX * LevelChunk.CHUNK_SIZE + (int)minWorldCoords.x;
        int worldY = chunkY * LevelChunk.CHUNK_SIZE + (int)minWorldCoords.y;
        int worldZ = chunkZ * LevelChunk.CHUNK_SIZE + (int)minWorldCoords.z;

        return new Vector3(worldX, worldY, worldZ);
    }

    /**
     * Retrieves a chunk at the specified chunk coordinates.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkY Chunk Y coordinate
     * @param chunkZ Chunk Z coordinate
     * @return The LevelChunk at the specified coordinates, or null if it doesn't exist
     */
    public LevelChunk getChunk(int chunkX, int chunkY, int chunkZ) {
        long key = encodeChunkCoordinates(chunkX, chunkY, chunkZ);
        return chunks.get(key);
    }

    /**
     * Gets all chunks that contain content.
     *
     * @return A set of LevelChunks that have at least one tile
     */
    public Set<LevelChunk> getPopulatedChunks() {
        Set<LevelChunk> populated = new HashSet<>();

        for (LevelChunk chunk : chunks.values()) {
            if (chunk != null && chunk.hasContent()) {
                populated.add(chunk);
            }
        }

        return populated;
    }

    /**
     * Calculates the bounds needed for the chunk system based on the tiles in the GameMap.
     */
    private void calculateBounds() {
        if (gameMap.getAllTiles().isEmpty()) {
            minWorldCoords = new Vector3(0, 0, 0);
            maxWorldCoords = new Vector3(0, 0, 0);
            worldBounds = new Vector3(1, 1, 1);
            return;
        }

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        // Find the actual bounds of all tiles
        for (MapTile tile : gameMap.getAllTiles()) {
            Vector3 tileCoords = getTileCoordinates(tile);

            minX = Math.min(minX, tileCoords.x);
            maxX = Math.max(maxX, tileCoords.x);
            minY = Math.min(minY, tileCoords.y);
            maxY = Math.max(maxY, tileCoords.y);
            minZ = Math.min(minZ, tileCoords.z);
            maxZ = Math.max(maxZ, tileCoords.z);
        }

        minWorldCoords = new Vector3(minX, minY, minZ);
        maxWorldCoords = new Vector3(maxX, maxY, maxZ);
        worldBounds = new Vector3(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        Log.info("ChunkManager",
            String.format("World bounds: min(%d, %d, %d) to max(%d, %d, %d), size(%d, %d, %d)",
                (int)minX, (int)minY, (int)minZ, (int)maxX, (int)maxY, (int)maxZ,
                (int)worldBounds.x, (int)worldBounds.y, (int)worldBounds.z));
    }

    // Removed initializeChunks() method - now using HashMap with lazy chunk creation

    /**
     * Extracts tile coordinates from a MapTile's world position.
     *
     * @param tile The MapTile to get coordinates for
     * @return Vector3 with tile coordinates in tile units
     */
    private Vector3 getTileCoordinates(MapTile tile) {
        int tileX = (int)(tile.x / Constants.MAP_TILE_SIZE);
        int tileY = (int)(tile.y / Constants.MAP_TILE_SIZE);
        int tileZ = (int)(tile.z / Constants.MAP_TILE_SIZE);
        return new Vector3(tileX, tileY, tileZ);
    }

    // Getters

    public Vector3 getWorldBounds() {
        return new Vector3(worldBounds);
    }

    public Vector3 getMinWorldCoords() {
        return new Vector3(minWorldCoords);
    }

    public Vector3 getMaxWorldCoords() {
        return new Vector3(maxWorldCoords);
    }

    public int getTotalChunkCount() {
        return chunks.size(); // Return actual number of created chunks
    }

    /**
     * Get the theoretical maximum chunks needed to cover the world bounds.
     * This is different from getTotalChunkCount() which returns actual created chunks.
     */
    public int getMaxPossibleChunkCount() {
        int chunksX = (int)Math.ceil(worldBounds.x / (float)LevelChunk.CHUNK_SIZE);
        int chunksY = (int)Math.ceil(worldBounds.y / (float)LevelChunk.CHUNK_SIZE);
        int chunksZ = (int)Math.ceil(worldBounds.z / (float)LevelChunk.CHUNK_SIZE);
        return chunksX * chunksY * chunksZ;
    }
}
