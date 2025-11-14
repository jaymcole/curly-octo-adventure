package curly.octo.server;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.playerManagement.ClientManager;
import curly.octo.server.playerManagement.ClientConnectionKey;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.playerManagement.ClientUniqueId;
import curly.octo.server.serverAgents.BaseAgent;
import curly.octo.server.serverAgents.PlayerCollisionAgent;
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
    protected ServerGameObjectManager gameObjectManager;
    protected Random random;
    protected boolean disposed = false;

    public ClientManager clientManager;
//    public HashMap<ClientConnectionKey, ClientProfile> clientProfiles;
//    public ArrayList<ClientProfile> homelessedProfiles;
    public ArrayList<BaseAgent> serverAgents;

    // Queue for state updates that arrive before client identification
    private HashMap<ClientConnectionKey, ArrayList<PendingStateUpdate>> pendingStateUpdates;

    // Inner class to store pending state updates
    private static class PendingStateUpdate {
        String oldState;
        String newState;
        long timestamp;

        PendingStateUpdate(String oldState, String newState) {
            this.oldState = oldState;
            this.newState = newState;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public ServerCoordinator(Random random) {
        this.random = random;
        clientManager = new ClientManager();
        pendingStateUpdates = new HashMap<>();
        instantiateServerAgents();
    }

    private void instantiateServerAgents() {
        this.serverAgents = new ArrayList<>();
        serverAgents.add(new PlayerCollisionAgent(gameObjectManager));
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
    public ClientManager getClientProfiles() {
        return clientManager;
    }

    /**
     * Get a specific client profile by client key.
     * @param clientKey The client identifier
     * @return ClientProfile or null if not found
     */
    public ClientProfile getClientProfile(ClientConnectionKey clientKey) {
        return clientManager.getClientProfile(clientKey);
    }

    /**
     * Register a new client profile.
     * @param clientKey The client identifier
     */
    public void registerClientProfile(ClientConnectionKey clientKey, ClientUniqueId uniqueIdentifier, String preferredName) {
        Log.info("registerClientProfile","Client connecting...");
        if (clientManager.clientProfileExists(clientKey)) {
            Log.info("registerClientProfile","Client connection id already has entry");
            if (clientManager.getClientProfile(clientKey).clientUniqueId.equals(uniqueIdentifier)) {
                Log.info("registerClientProfile","Client uniqueId does not match existing entry. Homelessing existing user");
                clientManager.deactivateProfile(clientKey);
                clientManager.createNewProfile(clientKey);
            } else {
                ClientProfile existingProfile = null;
                for(ClientProfile homeless : clientManager.getAllInactiveProfiles()) {
                    if (homeless.clientUniqueId.equals(uniqueIdentifier)) {
                        Log.info("registerClientProfile","Found a client profile in homeless camp");
                        existingProfile = homeless;
                        break;
                    }
                }
                if (existingProfile != null) {
                    Log.info("registerClientProfile", "replacing homeless");
                    clientManager.activateProfile(clientKey, existingProfile.clientUniqueId);
                }
            }
        } else {
            Log.info("registerClientProfile", "creating new profile");
            clientManager.createNewProfile(clientKey);
        }
        clientManager.getClientProfile(clientKey).clientUniqueId = uniqueIdentifier;
        clientManager.getClientProfile(clientKey).userName = preferredName;
        Log.info("ServerCoordinator", "Profile successfully registered: " + clientKey + " -> uniqueId=" +
                 uniqueIdentifier + ", name=" + preferredName + " (Total profiles: " + clientManager.getAllClientProfiles().size() + ")");

        // Process any pending state updates for this client
        if (pendingStateUpdates.containsKey(clientKey)) {
            ArrayList<PendingStateUpdate> pending = pendingStateUpdates.get(clientKey);
            Log.info("ServerCoordinator", "Processing " + pending.size() + " queued state updates for " + clientKey);

            for (PendingStateUpdate update : pending) {
                long age = System.currentTimeMillis() - update.timestamp;
                Log.info("ServerCoordinator", "  Applying queued update (age=" + age + "ms): " +
                         update.oldState + " -> " + update.newState);
                clientManager.getClientProfile(clientKey).currentState = update.newState;
            }

            // Clear the queue
            pendingStateUpdates.remove(clientKey);
            Log.info("ServerCoordinator", "Cleared pending updates queue for " + clientKey);
        }
    }

    /**
     * Update a client's state.
     * @param clientKey The client identifier
     * @param oldState The previous state
     * @param newState The new state
     */
    public void updateClientState(ClientConnectionKey clientKey, String oldState, String newState) {
        ClientProfile profile = getClientProfile(clientKey);
        if (profile != null) {
            profile.currentState = newState;
            Log.info("ServerCoordinator", "Client (" + clientKey + ") transitioned from [" + oldState + "] to [" + newState + "]");
        } else {
            // Profile not found - queue the update for later processing
            Log.warn("ServerCoordinator", "Client profile not found for " + clientKey + " - QUEUING state update");
            Log.warn("ServerCoordinator", "  Attempted transition: " + oldState + " -> " + newState);
            Log.warn("ServerCoordinator", "  This is a race condition - state change arrived before identification");

            // Create queue for this client if it doesn't exist
            if (!pendingStateUpdates.containsKey(clientKey)) {
                pendingStateUpdates.put(clientKey, new ArrayList<>());
            }

            // Add the update to the queue
            pendingStateUpdates.get(clientKey).add(new PendingStateUpdate(oldState, newState));
            Log.info("ServerCoordinator", "  Queued state update. Total pending for this client: " +
                     pendingStateUpdates.get(clientKey).size());
        }
    }

    public void update(float deltaTime) {
        // Update server state machine
        ServerStateManager.update(deltaTime);
        for(BaseAgent agent : serverAgents) {
            agent.update(deltaTime);
        }

    }

    public void regenerateMap(long newSeed) {
        Log.info("ServerCoordinator", "Regenerating host map with seed: " + newSeed);

        try {
            // Create new map FIRST (before disposing old one to minimize null window)
            GameMap newMap = new GameMap(newSeed, true);
            Log.info("ServerCoordinator", "Generated new host map with seed: " + newSeed);
            Log.info("ServerCoordinator", "New map has " + newMap.getAllTiles().size() + " tiles, hash: " + newMap.hashCode());

            // Store reference to old map
            GameMap oldMap = mapManager;

            // Atomically swap to new map
            mapManager = newMap;

            // Dispose old map AFTER swap (minimizes window where map could be null/invalid)
            if (oldMap != null) {
                oldMap.dispose();
            }
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

    public ServerGameObjectManager getGameObjectManager() {
        return gameObjectManager;
    }

    public void setGameObjectManager(ServerGameObjectManager gameObjectManager) {
        this.gameObjectManager = gameObjectManager;
        Log.info("ServerCoordinator", "Game object manager set");
        instantiateServerAgents();
    }

    public void dispose() {
        if (disposed) {
            Log.info("ServerCoordinator", "Already disposed, skipping");
            return;
        }

        Log.info("ServerCoordinator", "Disposing server coordinator...");

        if (gameObjectManager != null) {
            try {
                gameObjectManager.dispose();
                Log.info("ServerCoordinator", "Game object manager disposed");
            } catch (Exception e) {
                Log.error("ServerCoordinator", "Error disposing game object manager: " + e.getMessage());
            }
            gameObjectManager = null;
        }

        if (mapManager != null) {
            try {
                mapManager.dispose();
                Log.info("ServerCoordinator", "Map manager disposed");
            } catch (Exception e) {
                Log.error("ServerCoordinator", "Error disposing map manager: " + e.getMessage());
            }
            mapManager = null;
        }

        clientManager.clearProfiles();
        disposed = true;
        Log.info("ServerCoordinator", "Server coordinator disposed");
    }
}
