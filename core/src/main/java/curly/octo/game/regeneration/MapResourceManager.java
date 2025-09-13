package curly.octo.game.regeneration;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.ClientGameWorld;
import curly.octo.map.GameMap;

/**
 * Handles all resource management during map regeneration.
 * Encapsulates cleanup, map application, and player reinitialization.
 */
public class MapResourceManager {
    
    private final ClientGameWorld gameWorld;
    
    public MapResourceManager(ClientGameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }
    
    /**
     * Clean up all resources before receiving new map
     */
    public void cleanupForRegeneration() {
        Log.info("MapResourceManager", "Starting cleanup for map regeneration");
        
        if (gameWorld == null) {
            Log.warn("MapResourceManager", "No game world available for cleanup");
            return;
        }
        
        try {
            // Use existing cleanup method
            gameWorld.cleanupForMapRegeneration();
            Log.info("MapResourceManager", "Map resources cleaned up successfully");
            
        } catch (Exception e) {
            Log.error("MapResourceManager", "Error during cleanup", e);
            throw new RuntimeException("Failed to cleanup map resources", e);
        }
    }
    
    /**
     * Apply new map to the game world
     */
    public void applyNewMap(GameMap newMap) {
        Log.info("MapResourceManager", "Applying new map to game world");
        
        if (gameWorld == null) {
            throw new RuntimeException("No game world available for map application");
        }
        
        if (newMap == null) {
            throw new RuntimeException("Cannot apply null map");
        }
        
        try {
            // Set the new map
            gameWorld.setMap(newMap);
            Log.info("MapResourceManager", "New map applied successfully");
            
        } catch (Exception e) {
            Log.error("MapResourceManager", "Error applying new map", e);
            throw new RuntimeException("Failed to apply new map", e);
        }
    }
    
    /**
     * Reinitialize all players after map regeneration
     */
    public void reinitializePlayers() {
        Log.info("MapResourceManager", "Reinitializing players for new map");
        
        if (gameWorld == null) {
            Log.warn("MapResourceManager", "No game world available for player reinitialization");
            return;
        }
        
        try {
            // Use existing reinitialization method
            gameWorld.reinitializePlayersAfterMapRegeneration();
            Log.info("MapResourceManager", "Players reinitialized successfully");
            
        } catch (Exception e) {
            Log.error("MapResourceManager", "Error reinitializing players", e);
            throw new RuntimeException("Failed to reinitialize players", e);
        }
    }
    
    /**
     * Get the underlying game world (for advanced operations if needed)
     */
    public ClientGameWorld getGameWorld() {
        return gameWorld;
    }
    
    /**
     * Check if the resource manager is ready to perform operations
     */
    public boolean isReady() {
        return gameWorld != null;
    }
}