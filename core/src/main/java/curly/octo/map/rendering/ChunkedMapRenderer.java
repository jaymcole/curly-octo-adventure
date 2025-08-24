package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderer that splits large maps into chunks to avoid vertex limits.
 * Only renders chunks within a certain distance of the camera.
 */
public class ChunkedMapRenderer {
    
    private static final float RENDER_DISTANCE = 200f; // Distance to render chunks
    private static final int MAX_CHUNK_UPDATES_PER_FRAME = 2; // Limit chunk updates per frame
    
    private final GameMap gameMap;
    private final Map<Long, MapChunk> chunks;
    private final Queue<MapChunk> chunksNeedingUpdate;
    private final BFSVisibleMapModelBuilder modelBuilder;
    
    // Materials for rendering
    private final Material stoneMaterial;
    private final Material dirtMaterial;
    private final Material grassMaterial;
    private final Material spawnMaterial;
    private final Material pinkWall;
    private final Material waterMaterial;
    
    public ChunkedMapRenderer(GameMap gameMap, Material stoneMaterial, Material dirtMaterial, 
                             Material grassMaterial, Material spawnMaterial, Material pinkWall, Material waterMaterial) {
        this.gameMap = gameMap;
        this.chunks = new ConcurrentHashMap<>();
        this.chunksNeedingUpdate = new LinkedList<>();
        this.modelBuilder = new BFSVisibleMapModelBuilder();
        
        this.stoneMaterial = stoneMaterial;
        this.dirtMaterial = dirtMaterial;
        this.grassMaterial = grassMaterial;
        this.spawnMaterial = spawnMaterial;
        this.pinkWall = pinkWall;
        this.waterMaterial = waterMaterial;
        
        buildInitialChunks();
    }
    
    /**
     * Build initial chunks from all tiles in the map
     */
    private void buildInitialChunks() {
        System.out.println("ChunkedMapRenderer: Building initial chunks");
        
        for (MapTile tile : gameMap.getAllTiles()) {
            long chunkKey = MapChunk.getChunkKey(tile);
            MapChunk chunk = chunks.get(chunkKey);
            
            if (chunk == null) {
                int chunkX = MapChunk.getChunkCoordinate(tile.x);
                int chunkZ = MapChunk.getChunkCoordinate(tile.z);
                chunk = new MapChunk(chunkX, chunkZ);
                chunks.put(chunkKey, chunk);
            }
            
            chunk.addTile(tile);
        }
        
        // Mark all chunks as needing update
        for (MapChunk chunk : chunks.values()) {
            if (!chunk.isEmpty()) {
                chunksNeedingUpdate.offer(chunk);
            }
        }
        
        System.out.println("ChunkedMapRenderer: Created " + chunks.size() + " chunks from " + 
                          gameMap.getAllTiles().size() + " tiles");
    }
    
    /**
     * Update chunk models (call this each frame)
     */
    public void update(Vector3 cameraPosition) {
        // Process a limited number of chunk updates per frame
        int updatesProcessed = 0;
        while (!chunksNeedingUpdate.isEmpty() && updatesProcessed < MAX_CHUNK_UPDATES_PER_FRAME) {
            MapChunk chunk = chunksNeedingUpdate.poll();
            if (chunk != null && chunk.needsUpdate()) {
                updateChunkModels(chunk);
                updatesProcessed++;
            }
        }
    }
    
    /**
     * Render visible chunks
     */
    public void render(ModelBatch modelBatch, Camera camera) {
        Vector3 cameraPos = camera.position;
        
        for (MapChunk chunk : chunks.values()) {
            if (chunk.isEmpty()) continue;
            
            // Only render chunks within render distance
            if (!chunk.isWithinRenderDistance(cameraPos, RENDER_DISTANCE)) {
                continue;
            }
            
            // Render opaque model
            if (chunk.getOpaqueModel() != null) {
                ModelInstance opaqueInstance = new ModelInstance(chunk.getOpaqueModel());
                modelBatch.render(opaqueInstance);
            }
            
            // Render water model
            if (chunk.getWaterModel() != null) {
                ModelInstance waterInstance = new ModelInstance(chunk.getWaterModel());
                modelBatch.render(waterInstance);
            }
        }
    }
    
    /**
     * Update the models for a specific chunk
     */
    private void updateChunkModels(MapChunk chunk) {
        if (chunk.isEmpty()) return;
        
        try {
            // Set up model builder with chunk tiles
            modelBuilder.setGameMap(gameMap);
            modelBuilder.clearTileFilter();
            
            // Add only this chunk's tiles to the builder
            Set<MapTile> chunkTileSet = new HashSet<>(chunk.getTiles());
            
            // Check if chunk is too large and needs further subdivision
            if (chunkTileSet.size() > 16) { // 4x4 = 16 tiles max, but allow some headroom
                System.out.println("ChunkedMapRenderer: Chunk at (" + chunk.getChunkX() + ", " + 
                                 chunk.getChunkZ() + ") has " + chunkTileSet.size() + " tiles - may be too large");
            }
            
            modelBuilder.setTileFilter(chunkTileSet);
            
            // Build opaque model
            ModelBuilder opaqueBuilder = new ModelBuilder();
            opaqueBuilder.begin();
            modelBuilder.buildGeometry(opaqueBuilder, stoneMaterial, dirtMaterial, grassMaterial, 
                                     spawnMaterial, pinkWall, null); // No water in opaque model
            chunk.setOpaqueModel(opaqueBuilder.end());
            
            // Build water model
            ModelBuilder waterBuilder = new ModelBuilder();
            waterBuilder.begin();
            modelBuilder.buildWaterGeometry(waterBuilder, waterMaterial);
            chunk.setWaterModel(waterBuilder.end());
            
            chunk.setNeedsUpdate(false);
            
        } catch (Exception e) {
            if (e.getMessage().contains("Too many vertices")) {
                System.err.println("ChunkedMapRenderer: Chunk at (" + chunk.getChunkX() + ", " + 
                                 chunk.getChunkZ() + ") has too many vertices (" + chunk.getTiles().size() + 
                                 " tiles) - skipping this chunk");
                chunk.setNeedsUpdate(false); // Don't retry
            } else {
                System.err.println("ChunkedMapRenderer: Failed to update chunk at (" + 
                                 chunk.getChunkX() + ", " + chunk.getChunkZ() + "): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Mark a chunk as needing update
     */
    public void markChunkForUpdate(int chunkX, int chunkZ) {
        long chunkKey = MapChunk.getChunkKey(chunkX, chunkZ);
        MapChunk chunk = chunks.get(chunkKey);
        if (chunk != null && !chunk.needsUpdate()) {
            chunk.setNeedsUpdate(true);
            chunksNeedingUpdate.offer(chunk);
        }
    }
    
    /**
     * Add a new tile to the appropriate chunk
     */
    public void addTile(MapTile tile) {
        long chunkKey = MapChunk.getChunkKey(tile);
        MapChunk chunk = chunks.get(chunkKey);
        
        if (chunk == null) {
            int chunkX = MapChunk.getChunkCoordinate(tile.x);
            int chunkZ = MapChunk.getChunkCoordinate(tile.z);
            chunk = new MapChunk(chunkX, chunkZ);
            chunks.put(chunkKey, chunk);
        }
        
        chunk.addTile(tile);
        if (!chunk.needsUpdate()) {
            chunksNeedingUpdate.offer(chunk);
        }
    }
    
    /**
     * Remove a tile from its chunk
     */
    public void removeTile(MapTile tile) {
        long chunkKey = MapChunk.getChunkKey(tile);
        MapChunk chunk = chunks.get(chunkKey);
        
        if (chunk != null) {
            chunk.removeTile(tile);
            if (!chunk.needsUpdate()) {
                chunksNeedingUpdate.offer(chunk);
            }
        }
    }
    
    /**
     * Get statistics about the chunked renderer
     */
    public ChunkStats getStats() {
        int totalChunks = chunks.size();
        int loadedChunks = 0;
        int chunksWithModels = 0;
        
        for (MapChunk chunk : chunks.values()) {
            if (!chunk.isEmpty()) {
                loadedChunks++;
                if (chunk.getOpaqueModel() != null || chunk.getWaterModel() != null) {
                    chunksWithModels++;
                }
            }
        }
        
        return new ChunkStats(totalChunks, loadedChunks, chunksWithModels, chunksNeedingUpdate.size());
    }
    
    /**
     * Dispose of all chunk models
     */
    public void dispose() {
        for (MapChunk chunk : chunks.values()) {
            chunk.dispose();
        }
        chunks.clear();
        chunksNeedingUpdate.clear();
    }
    
    /**
     * Statistics about chunk rendering
     */
    public static class ChunkStats {
        public final int totalChunks;
        public final int loadedChunks;
        public final int chunksWithModels;
        public final int chunksNeedingUpdate;
        
        public ChunkStats(int totalChunks, int loadedChunks, int chunksWithModels, int chunksNeedingUpdate) {
            this.totalChunks = totalChunks;
            this.loadedChunks = loadedChunks;
            this.chunksWithModels = chunksWithModels;
            this.chunksNeedingUpdate = chunksNeedingUpdate;
        }
        
        @Override
        public String toString() {
            return String.format("Chunks: %d total, %d loaded, %d with models, %d updating", 
                               totalChunks, loadedChunks, chunksWithModels, chunksNeedingUpdate);
        }
    }
}