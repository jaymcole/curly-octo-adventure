package curly.octo.game;

import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;

import java.util.HashMap;
import java.util.Random;

import static curly.octo.Constants.MAP_GENERATION_SEED;

/**
 * Host-specific game world that handles server-side game logic.
 * Optimized for network coordination without graphics overhead.
 */
public class HostGameWorld extends GameWorld {

    public static final String WORLD_OWNERSHIP = "owned by world";
    private HashMap<String, String> entityIdToEntityOwnerMap;
    private boolean deferredMapGeneration = false;


    public HostGameWorld(Random random) {
        super(random, true); // true = server-only mode
        Log.info("HostGameWorld", "Created host game world");
        entityIdToEntityOwnerMap = new HashMap<>();
    }

    /**
     * Host-only map initialization that skips rendering components.
     * Used to create a map for network distribution without graphics overhead.
     */
    public void initializeHostMap() {
        if (getMapManager() == null) {
            int size = 50;
            int height = 10;

            GameMap map = new GameMap(MAP_GENERATION_SEED, true);
            Log.info("HostGameWorld", "Created host map ("+size+"x"+height+"x"+size+" = " + (size*height*size) + " tiles) - no rendering, no physics");

            // Set the map without renderer initialization
            setMapManager(map);
            Log.info("HostGameWorld", "Initialized host map");
        }
    }

    /**
     * Sets whether map generation should be deferred until client connection.
     * When true, the host will not generate an initial map and instead will
     * trigger map generation through the regeneration workflow when a client connects.
     */
    public void setDeferredMapGeneration(boolean deferred) {
        this.deferredMapGeneration = deferred;
        if (deferred) {
            Log.info("HostGameWorld", "Map generation deferred - will generate when client connects");
        }
    }

    /**
     * @return true if map generation is deferred (no initial map created)
     */
    public boolean isDeferredMapGeneration() {
        return deferredMapGeneration;
    }

    /**
     * @return true if the host has an initial map available for distribution
     */
    public boolean hasInitialMap() {
        // If we have a map manager, we have a map available regardless of deferred generation setting
        // The deferredMapGeneration flag only affects whether we generate a map at startup
        return getMapManager() != null;
    }

    @Override
    public void update(float deltaTime) {
        updateServerOnly(deltaTime);
    }

    /**
     * Host-specific update that skips physics simulation and rendering preparation.
     * Used to avoid duplicate physics calculations while maintaining network sync timing.
     */
    public void updateServerOnly(float deltaTime) {
        // Host only needs to update game objects for network synchronization
        // Skip physics (client handles authoritative physics)
        // Skip light flickering (purely visual)
        // Skip player controller updates (client handles input/camera)

        // Update server-side game objects if any
        // Note: Currently minimal since we don't have many server-only objects yet

        // Keep position update timer for network sync timing
        incrementPositionUpdateTimer(deltaTime);
    }
    
    @Override
    public void regenerateMap(long newSeed) {
        Log.info("HostGameWorld", "Regenerating host map with seed: " + newSeed);
        
        try {
            // Dispose current map if it exists
            if (mapManager != null) {
                mapManager.dispose();
                Log.info("HostGameWorld", "Disposed old map");
            }
            
            // Create new map with new seed (server-only mode)
            GameMap newMap = new GameMap(newSeed, true);
            Log.info("HostGameWorld", "Generated new host map with seed: " + newSeed);
            Log.info("HostGameWorld", "New map has " + newMap.getAllTiles().size() + " tiles, hash: " + newMap.hashCode());
            
            // Set the new map
            setMapManager(newMap);
            
            Log.info("HostGameWorld", "Host map regeneration completed successfully");
            
        } catch (Exception e) {
            Log.error("HostGameWorld", "Failed to regenerate host map: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Host map regeneration failed", e);
        }
    }
}
