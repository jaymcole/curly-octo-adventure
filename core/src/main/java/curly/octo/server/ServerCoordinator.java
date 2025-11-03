package curly.octo.server;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.common.map.GameMap;

import java.util.HashMap;
import java.util.Random;

import static curly.octo.common.Constants.MAP_GENERATION_SEED;

/**
 * Server-side game coordinator that handles network coordination and map distribution.
 * Optimized for server-side operations without graphics or physics overhead.
 * Does not simulate a "world" - rather coordinates client connections and distributes map data.
 */
public class ServerCoordinator {

    // Core server state
    protected GameMap mapManager;
    protected Random random;
    protected float positionUpdateTimer = 0;
    protected boolean disposed = false;

    // Server-specific state
    private HashMap<String, String> entityIdToEntityOwnerMap;
    private boolean deferredMapGeneration = false;
    public HashMap<String, ClientProfile> clientProfiles;


    public ServerCoordinator(Random random) {
        this.random = random;
        Log.info("ServerCoordinator", "Created server coordinator (no graphics, no physics)");
        entityIdToEntityOwnerMap = new HashMap<>();
        clientProfiles = new HashMap<>();
    }

    /**
     * Server-only map initialization that skips rendering and physics components.
     * Used to create a map for network distribution without graphics overhead.
     */
    public void initializeHostMap() {
        if (mapManager == null) {
            int size = 50;
            int height = 10;

            GameMap map = new GameMap(MAP_GENERATION_SEED, true);
            Log.info("ServerCoordinator", "Created host map ("+size+"x"+height+"x"+size+" = " + (size*height*size) + " tiles) - no rendering, no physics");

            // Set the map without renderer initialization
            mapManager = map;
            Log.info("ServerCoordinator", "Initialized host map");
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
            Log.info("ServerCoordinator", "Map generation deferred - will generate when client connects");
        }
    }

    /**
     * @return true if the host has an initial map available for distribution
     */
    public boolean hasInitialMap() {
        // If we have a map manager, we have a map available regardless of deferred generation setting
        // The deferredMapGeneration flag only affects whether we generate a map at startup
        return mapManager != null;
    }

    /**
     * Get all client profiles.
     * @return HashMap of client profiles keyed by client key
     */
    public HashMap<String, ClientProfile> getClientProfiles() {
        return clientProfiles;
    }

    /**
     * Get a specific client profile by client key.
     * @param clientKey The client identifier
     * @return ClientProfile or null if not found
     */
    public ClientProfile getClientProfile(String clientKey) {
        return clientProfiles.get(clientKey);
    }

    /**
     * Register a new client profile.
     * @param clientKey The client identifier
     */
    public void registerClientProfile(String clientKey) {
        if (!clientProfiles.containsKey(clientKey)) {
            clientProfiles.put(clientKey, new ClientProfile());
            Log.info("ServerCoordinator", "Registered new client profile: " + clientKey);
        }
    }

    /**
     * Update a client's state.
     * @param clientKey The client identifier
     * @param oldState The previous state
     * @param newState The new state
     */
    public void updateClientState(String clientKey, String oldState, String newState) {
        ClientProfile profile = clientProfiles.get(clientKey);
        if (profile != null) {
            profile.currentState = newState;
            Log.info("ServerCoordinator", "Client (" + clientKey + ") transitioned from [" + oldState + "] to [" + newState + "]");
        } else {
            Log.error("ServerCoordinator", "Cannot update state - client profile not found: " + clientKey);
        }
    }

    public void update(float deltaTime) {
        positionUpdateTimer += deltaTime;
        ServerStateManager.update(deltaTime);
    }

    public void regenerateMap(long newSeed) {
        Log.info("ServerCoordinator", "Regenerating host map with seed: " + newSeed);

        try {
            // Dispose current map if it exists
            if (mapManager != null) {
                mapManager.dispose();
                Log.info("ServerCoordinator", "Disposed old map");
            }

            // Create new map with new seed (server-only mode)
            GameMap newMap = new GameMap(newSeed, true);
            Log.info("ServerCoordinator", "Generated new host map with seed: " + newSeed);
            Log.info("ServerCoordinator", "New map has " + newMap.getAllTiles().size() + " tiles, hash: " + newMap.hashCode());

            // Set the new map
            mapManager = newMap;

            Log.info("ServerCoordinator", "Host map regeneration completed successfully");

        } catch (Exception e) {
            Log.error("ServerCoordinator", "Failed to regenerate host map: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Host map regeneration failed", e);
        }
    }

    // Accessors
    public GameMap getMapManager() {
        return mapManager;
    }

    public Random getRandom() {
        return random;
    }

    protected void setMapManager(GameMap mapManager) {
        this.mapManager = mapManager;
    }

    protected void incrementPositionUpdateTimer(float deltaTime) {
        positionUpdateTimer += deltaTime;
    }

    public void dispose() {
        if (disposed) {
            Log.info("ServerCoordinator", "Already disposed, skipping");
            return;
        }

        Log.info("ServerCoordinator", "Disposing server coordinator...");

        if (mapManager != null) {
            try {
                mapManager.dispose();
                Log.info("ServerCoordinator", "Map manager disposed");
            } catch (Exception e) {
                Log.error("ServerCoordinator", "Error disposing map manager: " + e.getMessage());
            }
            mapManager = null;
        }

        clientProfiles.clear();
        disposed = true;
        Log.info("ServerCoordinator", "Server coordinator disposed");
    }
}
