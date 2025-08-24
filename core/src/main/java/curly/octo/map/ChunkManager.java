package curly.octo.map;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.MapHint;
import curly.octo.map.hints.SpawnPointHint;

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
    private LevelChunk[][][] chunks;
    private Vector3 chunkBounds;
    private Vector3 worldBounds;
    private Vector3 minWorldCoords;
    private Vector3 maxWorldCoords;
    
    public ChunkManager(GameMap gameMap) {
        this.gameMap = gameMap;
        calculateBounds();
        initializeChunks();
    }
    
    /**
     * Organizes all tiles from the GameMap into chunks using BFS from spawn points.
     * This method processes tiles in the order they are discovered during BFS,
     * ensuring that reachable areas are prioritized.
     * 
     * @return A set of LevelChunks that contain tiles, organized by reachability
     */
    public Set<LevelChunk> organizeIntoChunks() {
        Log.info("ChunkManager", "Starting chunk organization with BFS from spawn points");
        
        Set<LevelChunk> populatedChunks = new HashSet<>();
        Set<MapTile> processedTiles = new HashSet<>();
        
        // Step 1: Process reachable tiles using BFS from spawn points
        processReachableTiles(populatedChunks, processedTiles);
        
        // Step 2: Process any remaining unvisited tiles (isolated areas)
        processRemainingTiles(populatedChunks, processedTiles);
        
        Log.info("ChunkManager", 
            String.format("Chunk organization complete: %d populated chunks, %d total tiles processed",
                populatedChunks.size(), processedTiles.size()));
                
        return populatedChunks;
    }
    
    /**
     * Processes tiles reachable from spawn points using BFS.
     * This ensures that the main playable areas are processed first.
     */
    private void processReachableTiles(Set<LevelChunk> populatedChunks, Set<MapTile> processedTiles) {
        Queue<MapTile> bfsQueue = new ArrayDeque<>();
        
        // Find spawn points and start BFS from them
        ArrayList<MapHint> spawnHints = gameMap.getAllHintsOfType(SpawnPointHint.class);
        int spawnPointsFound = 0;
        
        if (!spawnHints.isEmpty()) {
            for (MapHint hint : spawnHints) {
                MapTile spawnTile = gameMap.getTile(hint.tileLookupKey);
                if (spawnTile != null && !processedTiles.contains(spawnTile)) {
                    bfsQueue.offer(spawnTile);
                    processedTiles.add(spawnTile);
                    spawnPointsFound++;
                }
            }
        } else {
            // Fallback: start from first empty tile found
            Log.warn("ChunkManager", "No spawn points found, starting BFS from first empty tile");
            for (MapTile tile : gameMap.getAllTiles()) {
                if (tile.geometryType == MapTileGeometryType.EMPTY) {
                    bfsQueue.offer(tile);
                    processedTiles.add(tile);
                    spawnPointsFound = 1;
                    break;
                }
            }
        }
        
        Log.info("ChunkManager", "Starting BFS from " + spawnPointsFound + " spawn points");
        
        // Perform BFS to process all reachable tiles
        int tilesProcessedByBFS = 0;
        while (!bfsQueue.isEmpty()) {
            MapTile currentTile = bfsQueue.poll();
            
            // Place current tile in appropriate chunk
            if (placeTileInChunk(currentTile)) {
                LevelChunk chunk = getChunkForTile(currentTile);
                if (chunk != null && chunk.hasContent()) {
                    populatedChunks.add(chunk);
                }
            }
            tilesProcessedByBFS++;
            
            // Find and queue neighboring tiles
            Vector3 tileCoords = getTileCoordinates(currentTile);
            exploreNeighbors(tileCoords, bfsQueue, processedTiles);
        }
        
        Log.info("ChunkManager", "BFS processed " + tilesProcessedByBFS + " reachable tiles");
    }
    
    /**
     * Processes any tiles that weren't reached by BFS (isolated areas).
     */
    private void processRemainingTiles(Set<LevelChunk> populatedChunks, Set<MapTile> processedTiles) {
        int remainingTiles = 0;
        
        for (MapTile tile : gameMap.getAllTiles()) {
            if (!processedTiles.contains(tile)) {
                if (placeTileInChunk(tile)) {
                    LevelChunk chunk = getChunkForTile(tile);
                    if (chunk != null && chunk.hasContent()) {
                        populatedChunks.add(chunk);
                    }
                }
                remainingTiles++;
            }
        }
        
        if (remainingTiles > 0) {
            Log.info("ChunkManager", "Processed " + remainingTiles + " isolated tiles not reachable from spawn points");
        }
    }
    
    /**
     * Explores the 6 neighboring positions around a tile and adds unvisited tiles to the BFS queue.
     */
    private void exploreNeighbors(Vector3 tileCoords, Queue<MapTile> bfsQueue, Set<MapTile> processedTiles) {
        // Check all 6 directions (3D neighbors)
        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};
        
        for (int i = 0; i < 6; i++) {
            int neighborX = (int)tileCoords.x + dx[i];
            int neighborY = (int)tileCoords.y + dy[i];
            int neighborZ = (int)tileCoords.z + dz[i];
            
            MapTile neighbor = gameMap.getTile(neighborX, neighborY, neighborZ);
            
            if (neighbor != null && !processedTiles.contains(neighbor)) {
                bfsQueue.offer(neighbor);
                processedTiles.add(neighbor);
            }
        }
    }
    
    /**
     * Places a tile in the appropriate chunk based on its world coordinates.
     * 
     * @param tile The MapTile to place
     * @return true if the tile was successfully placed, false otherwise
     */
    private boolean placeTileInChunk(MapTile tile) {
        Vector3 tileCoords = getTileCoordinates(tile);
        Vector3 chunkCoords = worldToChunkCoordinates((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z);
        
        if (chunkCoords == null) {
            Log.warn("ChunkManager", "Tile at " + tileCoords + " is outside chunk bounds");
            return false;
        }
        
        LevelChunk chunk = getChunk((int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z);
        if (chunk == null) {
            Log.warn("ChunkManager", "No chunk found at chunk coordinates " + chunkCoords);
            return false;
        }
        
        return chunk.setTileByWorldCoordinates((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, tile);
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
     * 
     * @param worldX World X coordinate in tile units
     * @param worldY World Y coordinate in tile units
     * @param worldZ World Z coordinate in tile units
     * @return Vector3 with chunk coordinates, or null if out of bounds
     */
    public Vector3 worldToChunkCoordinates(int worldX, int worldY, int worldZ) {
        if (worldX < minWorldCoords.x || worldX > maxWorldCoords.x ||
            worldY < minWorldCoords.y || worldY > maxWorldCoords.y ||
            worldZ < minWorldCoords.z || worldZ > maxWorldCoords.z) {
            return null;
        }
        
        // Adjust for minimum coordinates and calculate chunk position
        int adjustedX = (int)(worldX - minWorldCoords.x);
        int adjustedY = (int)(worldY - minWorldCoords.y);
        int adjustedZ = (int)(worldZ - minWorldCoords.z);
        
        int chunkX = adjustedX / LevelChunk.CHUNK_SIZE;
        int chunkY = adjustedY / LevelChunk.CHUNK_SIZE;
        int chunkZ = adjustedZ / LevelChunk.CHUNK_SIZE;
        
        return new Vector3(chunkX, chunkY, chunkZ);
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
     * @return The LevelChunk at the specified coordinates, or null if out of bounds
     */
    public LevelChunk getChunk(int chunkX, int chunkY, int chunkZ) {
        if (chunks == null ||
            chunkX < 0 || chunkX >= chunkBounds.x ||
            chunkY < 0 || chunkY >= chunkBounds.y ||
            chunkZ < 0 || chunkZ >= chunkBounds.z) {
            return null;
        }
        
        return chunks[chunkX][chunkY][chunkZ];
    }
    
    /**
     * Gets all chunks that contain content.
     * 
     * @return A set of LevelChunks that have at least one tile
     */
    public Set<LevelChunk> getPopulatedChunks() {
        Set<LevelChunk> populated = new HashSet<>();
        
        if (chunks != null) {
            for (int x = 0; x < chunkBounds.x; x++) {
                for (int y = 0; y < chunkBounds.y; y++) {
                    for (int z = 0; z < chunkBounds.z; z++) {
                        LevelChunk chunk = chunks[x][y][z];
                        if (chunk != null && chunk.hasContent()) {
                            populated.add(chunk);
                        }
                    }
                }
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
    
    /**
     * Initializes the chunk array based on the calculated world bounds.
     */
    private void initializeChunks() {
        // Calculate how many chunks we need in each dimension
        int chunksX = (int)Math.ceil(worldBounds.x / (float)LevelChunk.CHUNK_SIZE);
        int chunksY = (int)Math.ceil(worldBounds.y / (float)LevelChunk.CHUNK_SIZE);
        int chunksZ = (int)Math.ceil(worldBounds.z / (float)LevelChunk.CHUNK_SIZE);
        
        chunkBounds = new Vector3(chunksX, chunksY, chunksZ);
        chunks = new LevelChunk[chunksX][chunksY][chunksZ];
        
        // Create all chunks
        for (int x = 0; x < chunksX; x++) {
            for (int y = 0; y < chunksY; y++) {
                for (int z = 0; z < chunksZ; z++) {
                    chunks[x][y][z] = new LevelChunk(x, y, z);
                }
            }
        }
        
        Log.info("ChunkManager", 
            String.format("Initialized %d chunks (%dx%dx%d) for world bounds %s",
                chunksX * chunksY * chunksZ, chunksX, chunksY, chunksZ, worldBounds));
    }
    
    /**
     * Extracts tile coordinates from a MapTile's world position.
     * 
     * @param tile The MapTile to get coordinates for
     * @return Vector3 with tile coordinates in tile units
     */
    private Vector3 getTileCoordinates(MapTile tile) {
        int tileX = (int)(tile.x / MapTile.TILE_SIZE);
        int tileY = (int)(tile.y / MapTile.TILE_SIZE);
        int tileZ = (int)(tile.z / MapTile.TILE_SIZE);
        return new Vector3(tileX, tileY, tileZ);
    }
    
    // Getters
    
    public Vector3 getChunkBounds() {
        return new Vector3(chunkBounds);
    }
    
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
        return (int)(chunkBounds.x * chunkBounds.y * chunkBounds.z);
    }
}