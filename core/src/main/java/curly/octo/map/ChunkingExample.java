package curly.octo.map;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.rendering.ChunkedMapModelBuilder;

import java.util.Set;

/**
 * Example class demonstrating how to use the ChunkManager and ChunkedMapModelBuilder
 * with your existing GameMap for efficient level organization and mesh generation.
 */
public class ChunkingExample {
    
    /**
     * Example showing how to integrate chunk-based processing into your map generation workflow.
     * This method demonstrates the typical usage pattern for the chunking system.
     */
    public static void demonstrateChunking(GameMap gameMap) {
        Log.info("ChunkingExample", "=== Chunk System Demo ===");
        
        // Step 1: Create a ChunkManager for the map
        ChunkManager chunkManager = new ChunkManager(gameMap);
        
        // Step 2: Organize the map into chunks using BFS from spawn points
        Set<LevelChunk> populatedChunks = chunkManager.organizeIntoChunks();
        
        // Step 3: Log chunk organization results
        Vector3 chunkBounds = chunkManager.getChunkBounds();
        Vector3 worldBounds = chunkManager.getWorldBounds();
        
        Log.info("ChunkingExample", String.format(
            "Organized map into chunks:\n" +
            "  Total chunks: %d (%dx%dx%d)\n" +
            "  Populated chunks: %d (%.1f%% usage)\n" +
            "  World bounds: %dx%dx%d tiles\n" +
            "  World coordinates: (%d,%d,%d) to (%d,%d,%d)",
            chunkManager.getTotalChunkCount(),
            (int)chunkBounds.x, (int)chunkBounds.y, (int)chunkBounds.z,
            populatedChunks.size(),
            (populatedChunks.size() * 100.0f / chunkManager.getTotalChunkCount()),
            (int)worldBounds.x, (int)worldBounds.y, (int)worldBounds.z,
            (int)chunkManager.getMinWorldCoords().x, (int)chunkManager.getMinWorldCoords().y, (int)chunkManager.getMinWorldCoords().z,
            (int)chunkManager.getMaxWorldCoords().x, (int)chunkManager.getMaxWorldCoords().y, (int)chunkManager.getMaxWorldCoords().z
        ));
        
        // Step 4: Demonstrate chunk-level operations
        demonstrateChunkOperations(populatedChunks);
        
        // Step 5: Show coordinate conversions
        demonstrateCoordinateConversions(chunkManager, gameMap);
        
        Log.info("ChunkingExample", "=== End Chunk Demo ===");
    }
    
    /**
     * Demonstrates operations you can perform on individual chunks.
     */
    private static void demonstrateChunkOperations(Set<LevelChunk> populatedChunks) {
        Log.info("ChunkingExample", "--- Chunk Operations ---");
        
        int totalTiles = 0;
        LevelChunk largestChunk = null;
        int maxTiles = 0;
        
        for (LevelChunk chunk : populatedChunks) {
            int solidTiles = chunk.getSolidTileCount();
            totalTiles += solidTiles;
            
            if (solidTiles > maxTiles) {
                maxTiles = solidTiles;
                largestChunk = chunk;
            }
            
            Log.info("ChunkingExample", String.format(
                "Chunk at (%d,%d,%d): %d tiles (%.1f%% full)",
                (int)chunk.getChunkCoordinates().x,
                (int)chunk.getChunkCoordinates().y, 
                (int)chunk.getChunkCoordinates().z,
                solidTiles,
                chunk.getLoadFactor() * 100f
            ));
        }
        
        if (largestChunk != null) {
            Log.info("ChunkingExample", String.format(
                "Largest chunk: (%d,%d,%d) with %d tiles",
                (int)largestChunk.getChunkCoordinates().x,
                (int)largestChunk.getChunkCoordinates().y,
                (int)largestChunk.getChunkCoordinates().z,
                maxTiles
            ));
        }
        
        Log.info("ChunkingExample", "Total tiles across all chunks: " + totalTiles);
    }
    
    /**
     * Demonstrates coordinate conversion capabilities.
     */
    private static void demonstrateCoordinateConversions(ChunkManager chunkManager, GameMap gameMap) {
        Log.info("ChunkingExample", "--- Coordinate Conversions ---");
        
        // Test coordinate conversions with a few sample tiles
        int sampleCount = 0;
        for (MapTile tile : gameMap.getAllTiles()) {
            if (sampleCount >= 3) break; // Only show first 3 tiles
            
            // Get tile coordinates
            int tileX = (int)(tile.x / MapTile.TILE_SIZE);
            int tileY = (int)(tile.y / MapTile.TILE_SIZE);
            int tileZ = (int)(tile.z / MapTile.TILE_SIZE);
            
            // Convert to chunk coordinates
            Vector3 chunkCoords = chunkManager.worldToChunkCoordinates(tileX, tileY, tileZ);
            
            if (chunkCoords != null) {
                // Get the chunk containing this tile
                LevelChunk chunk = chunkManager.getChunk((int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z);
                
                if (chunk != null) {
                    // Convert to local coordinates within the chunk
                    Vector3 localCoords = chunk.worldToLocalCoordinates(tileX, tileY, tileZ);
                    
                    if (localCoords != null) {
                        Log.info("ChunkingExample", String.format(
                            "Tile at world (%d,%d,%d) -> Chunk (%d,%d,%d) -> Local (%d,%d,%d)",
                            tileX, tileY, tileZ,
                            (int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z,
                            (int)localCoords.x, (int)localCoords.y, (int)localCoords.z
                        ));
                        
                        // Verify reverse conversion
                        Vector3 backToWorld = chunk.localToWorldCoordinates((int)localCoords.x, (int)localCoords.y, (int)localCoords.z);
                        if (backToWorld != null) {
                            boolean conversionCorrect = (tileX == (int)backToWorld.x) && 
                                                      (tileY == (int)backToWorld.y) && 
                                                      (tileZ == (int)backToWorld.z);
                            Log.info("ChunkingExample", "  Reverse conversion: " + (conversionCorrect ? "✓" : "✗"));
                        }
                    }
                }
            }
            sampleCount++;
        }
    }
    
    /**
     * Example method showing how to use chunks for mesh generation optimization.
     * This demonstrates how you might update only specific chunks instead of the entire map.
     */
    public static void demonstrateChunkBasedMeshGeneration(GameMap gameMap) {
        Log.info("ChunkingExample", "=== Chunk-Based Mesh Generation Demo ===");
        
        // Create chunked model builder
        ChunkedMapModelBuilder builder = new ChunkedMapModelBuilder(gameMap);
        
        // Get the populated chunks
        Set<LevelChunk> chunks = builder.getPopulatedChunks();
        
        Log.info("ChunkingExample", "Chunks available for mesh generation: " + chunks.size());
        
        // In a real implementation, you could:
        // 1. Generate meshes only for visible chunks (frustum culling)
        // 2. Update only chunks that have changed
        // 3. Use different LOD levels for distant chunks
        // 4. Stream chunks in/out based on player position
        
        for (LevelChunk chunk : chunks) {
            Vector3 chunkCoords = chunk.getChunkCoordinates();
            Vector3 worldOffset = chunk.getWorldOffset();
            
            Log.info("ChunkingExample", String.format(
                "Chunk (%d,%d,%d) at world offset (%.0f,%.0f,%.0f) - %d tiles ready for meshing",
                (int)chunkCoords.x, (int)chunkCoords.y, (int)chunkCoords.z,
                worldOffset.x, worldOffset.y, worldOffset.z,
                chunk.getSolidTileCount()
            ));
        }
        
        Log.info("ChunkingExample", "=== End Mesh Generation Demo ===");
    }
    
    /**
     * Utility method to add chunk organization to existing GameMap workflows.
     * Call this after your map generation is complete to set up chunking.
     */
    public static ChunkManager setupChunking(GameMap gameMap) {
        Log.info("ChunkingExample", "Setting up chunking for GameMap...");
        
        ChunkManager chunkManager = new ChunkManager(gameMap);
        Set<LevelChunk> populatedChunks = chunkManager.organizeIntoChunks();
        
        Log.info("ChunkingExample", String.format(
            "Chunking setup complete: %d populated chunks out of %d total", 
            populatedChunks.size(), chunkManager.getTotalChunkCount()));
        
        return chunkManager;
    }
}