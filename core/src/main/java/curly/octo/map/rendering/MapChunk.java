package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import curly.octo.map.MapTile;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chunk of the map for rendering purposes.
 * Chunks split large maps into manageable pieces to avoid vertex limits.
 */
public class MapChunk {
    
    public static final int CHUNK_SIZE = 4; // 4x4 tile chunks to avoid vertex limits
    
    private final int chunkX;
    private final int chunkZ;
    private final List<MapTile> tiles;
    private Model opaqueModel;
    private Model waterModel;
    private boolean needsUpdate;
    
    public MapChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.tiles = new ArrayList<>();
        this.needsUpdate = true;
    }
    
    /**
     * Check if a tile belongs to this chunk
     */
    public boolean containsTile(MapTile tile) {
        int tileChunkX = getChunkCoordinate(tile.x);
        int tileChunkZ = getChunkCoordinate(tile.z);
        return tileChunkX == chunkX && tileChunkZ == chunkZ;
    }
    
    /**
     * Add a tile to this chunk
     */
    public void addTile(MapTile tile) {
        if (containsTile(tile)) {
            tiles.add(tile);
            needsUpdate = true;
        }
    }
    
    /**
     * Remove a tile from this chunk
     */
    public void removeTile(MapTile tile) {
        if (tiles.remove(tile)) {
            needsUpdate = true;
        }
    }
    
    /**
     * Get all tiles in this chunk
     */
    public List<MapTile> getTiles() {
        return new ArrayList<>(tiles);
    }
    
    /**
     * Get the world bounds of this chunk
     */
    public ChunkBounds getBounds() {
        float worldStartX = chunkX * CHUNK_SIZE * 16f; // 16 = MapTile.TILE_SIZE
        float worldStartZ = chunkZ * CHUNK_SIZE * 16f;
        float worldEndX = worldStartX + CHUNK_SIZE * 16f;
        float worldEndZ = worldStartZ + CHUNK_SIZE * 16f;
        
        return new ChunkBounds(worldStartX, worldStartZ, worldEndX, worldEndZ);
    }
    
    /**
     * Check if this chunk is within render distance of a position
     */
    public boolean isWithinRenderDistance(Vector3 position, float renderDistance) {
        ChunkBounds bounds = getBounds();
        
        // Calculate closest point on chunk to the position
        float closestX = Math.max(bounds.minX, Math.min(position.x, bounds.maxX));
        float closestZ = Math.max(bounds.minZ, Math.min(position.z, bounds.maxZ));
        
        // Calculate distance
        float dx = position.x - closestX;
        float dz = position.z - closestZ;
        float distanceSquared = dx * dx + dz * dz;
        
        return distanceSquared <= renderDistance * renderDistance;
    }
    
    // Getters and setters
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    
    public Model getOpaqueModel() { return opaqueModel; }
    public void setOpaqueModel(Model model) { 
        if (opaqueModel != null) opaqueModel.dispose();
        this.opaqueModel = model; 
    }
    
    public Model getWaterModel() { return waterModel; }
    public void setWaterModel(Model model) { 
        if (waterModel != null) waterModel.dispose();
        this.waterModel = model; 
    }
    
    public boolean needsUpdate() { return needsUpdate; }
    public void setNeedsUpdate(boolean needsUpdate) { this.needsUpdate = needsUpdate; }
    
    public boolean isEmpty() { return tiles.isEmpty(); }
    
    /**
     * Dispose of models to free memory
     */
    public void dispose() {
        if (opaqueModel != null) {
            opaqueModel.dispose();
            opaqueModel = null;
        }
        if (waterModel != null) {
            waterModel.dispose();
            waterModel = null;
        }
    }
    
    /**
     * Get chunk coordinate from world coordinate
     */
    public static int getChunkCoordinate(float worldCoord) {
        return (int) Math.floor(worldCoord / (CHUNK_SIZE * 16f));
    }
    
    /**
     * Get chunk key for a tile
     */
    public static long getChunkKey(MapTile tile) {
        return getChunkKey(getChunkCoordinate(tile.x), getChunkCoordinate(tile.z));
    }
    
    /**
     * Get chunk key from chunk coordinates
     */
    public static long getChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Bounds of a chunk in world coordinates
     */
    public static class ChunkBounds {
        public final float minX, minZ, maxX, maxZ;
        
        public ChunkBounds(float minX, float minZ, float maxX, float maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }
    }
}