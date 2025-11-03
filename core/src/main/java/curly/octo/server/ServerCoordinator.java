package curly.octo.server;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.playerManagement.ClientConnectionKey;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.serverAgents.BaseAgent;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.common.map.GameMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Server-side game coordinator that handles network coordination and map distribution.
 * Optimized for server-side operations without graphics or physics overhead.
 * Does not simulate a "world" - rather coordinates client connections and distributes map data.
 */
public class ServerCoordinator {

    // Core server state
    protected GameMap mapManager;
    protected Random random;
    protected boolean disposed = false;

    public HashMap<ClientConnectionKey, ClientProfile> clientProfiles;
    public ArrayList<ClientProfile> homelessedProfiles;
    public ArrayList<BaseAgent> serverAgents;


    public ServerCoordinator(Random random) {
        this.random = random;
        clientProfiles = new HashMap<>();
        homelessedProfiles = new ArrayList<>();
        instantiateServerAgents();
    }

    private void instantiateServerAgents() {
        this.serverAgents = new ArrayList<>();
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
    public HashMap<ClientConnectionKey, ClientProfile> getClientProfiles() {
        return clientProfiles;
    }

    /**
     * Get a specific client profile by client key.
     * @param clientKey The client identifier
     * @return ClientProfile or null if not found
     */
    public ClientProfile getClientProfile(ClientConnectionKey clientKey) {
        return clientProfiles.get(clientKey);
    }

    /**
     * Register a new client profile.
     * @param clientKey The client identifier
     */
    public void registerClientProfile(ClientConnectionKey clientKey, String uniqueIdentifier, String preferredName) {
        if (clientProfiles.containsKey(clientKey)) {
            if (clientProfiles.get(clientKey).clientUniqueId.compareTo(uniqueIdentifier) != 0) {
                homelessedProfiles.add(clientProfiles.get(clientKey));
                clientProfiles.put(clientKey, new ClientProfile());
            } else {
                ClientProfile existingProfile = null;
                for(ClientProfile homeless : homelessedProfiles) {
                    if (homeless.clientUniqueId.compareTo(uniqueIdentifier) == 0) {
                        existingProfile = homeless;
                        break;
                    }
                }
                if (existingProfile != null) {
                    clientProfiles.put(clientKey, existingProfile);
                    homelessedProfiles.remove(existingProfile);
                }
            }

            Log.info("ServerCoordinator", "Registered new client profile: " + clientKey);
        } else {
            clientProfiles.put(clientKey, new ClientProfile());
        }
        clientProfiles.get(clientKey).clientUniqueId = uniqueIdentifier;
        clientProfiles.get(clientKey).userName = preferredName;
    }

    /**
     * Update a client's state.
     * @param clientKey The client identifier
     * @param oldState The previous state
     * @param newState The new state
     */
    public void updateClientState(ClientConnectionKey clientKey, String oldState, String newState) {
        ClientProfile profile = clientProfiles.get(clientKey);
        if (profile != null) {
            profile.currentState = newState;
            Log.info("ServerCoordinator", "Client (" + clientKey + ") transitioned from [" + oldState + "] to [" + newState + "]");
        } else {
            Log.error("ServerCoordinator", "Cannot update state - client profile not found: " + clientKey);
        }
    }

    public void update(float deltaTime) {
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
