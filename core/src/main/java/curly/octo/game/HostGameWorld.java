package curly.octo.game;

import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;

import java.util.HashMap;
import java.util.Random;

/**
 * Host-specific game world that handles server-side game logic.
 * Optimized for network coordination without graphics overhead.
 */
public class HostGameWorld extends GameWorld {

    public static final String WORLD_OWNERSHIP = "owned by world";
    private HashMap<String, String> entityIdToEntityOwnerMap;


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

            GameMap map = new GameMap(System.currentTimeMillis(), true); // true = server-only
            Log.info("HostGameWorld", "Created host map ("+size+"x"+height+"x"+size+" = " + (size*height*size) + " tiles) - no rendering, no physics");

            // Set the map without renderer initialization
            setMapManager(map);
            Log.info("HostGameWorld", "Initialized host map");
        }
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
}
