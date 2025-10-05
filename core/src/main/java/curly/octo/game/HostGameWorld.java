package curly.octo.game;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.serverObjects.ClientProfile;
import curly.octo.game.serverStates.ServerStateManager;
import curly.octo.map.GameMap;

import java.util.HashMap;
import java.util.Random;

import static curly.octo.Constants.MAP_GENERATION_SEED;

/**
 * Host-specific game world that handles server-side game logic.
 * Optimized for network coordination without graphics overhead.
 */
public class HostGameWorld extends GameWorld {

    private HashMap<String, String> entityIdToEntityOwnerMap;
    private boolean deferredMapGeneration = false;
    private HashMap<String, ClientProfile> clientProfiles;


    public HostGameWorld(Random random) {
        super(random, true); // true = server-only mode
        Log.info("HostGameWorld", "Created host game world");
        entityIdToEntityOwnerMap = new HashMap<>();
        clientProfiles = new HashMap<>();
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
     * @return true if the host has an initial map available for distribution
     */
    public boolean hasInitialMap() {
        // If we have a map manager, we have a map available regardless of deferred generation setting
        // The deferredMapGeneration flag only affects whether we generate a map at startup
        return getMapManager() != null;
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
            Log.info("HostGameWorld", "Registered new client profile: " + clientKey);
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
            Log.info("HostGameWorld", "Client (" + clientKey + ") transitioned from [" + oldState + "] to [" + newState + "]");
        } else {
            Log.error("HostGameWorld", "Cannot update state - client profile not found: " + clientKey);
        }
    }

    @Override
    public void update(float deltaTime) {
        incrementPositionUpdateTimer(deltaTime);
        ServerStateManager.update(deltaTime);
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
