package curly.octo.server;

import curly.octo.common.Constants;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.network.messages.*;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.server.playerManagement.ConnectionStatus;
import curly.octo.server.serverStates.mapTransfer.ServerMapTransferState;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.hints.SpawnPointHint;
import curly.octo.common.network.KryoNetwork;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.legacyMessages.MapRegenerationStartMessage;
import curly.octo.common.network.messages.legacyMessages.ClientReadyForMapMessage;
import curly.octo.common.PlayerObject;
import curly.octo.common.PlayerUtilities;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles server-side network operations.
 */
public class GameServer {
    private final Server server;  // Gameplay connection (small buffers)
    private BulkTransferServer bulkServer;  // Map transfer connection (large buffers)
    private final NetworkListener networkListener;
    private final GameMap map;
    private final List<PlayerObject> players;
    private final ServerCoordinator serverCoordinator;
    private final Map<Integer, String> connectionToPlayerMap = new HashMap<>();
    private final Set<Integer> readyClients = new HashSet<>(); // Track clients that have received map and assignment

    // Map regeneration state tracking
    private volatile boolean isRegenerating = false;
    private long currentRegenerationId = 0;
    private final Set<Integer> clientsReadyForMap = new HashSet<>();
    private Thread regenerationThread;

    // Initial generation player assignment tracking
    private final Map<Integer, String> pendingPlayerAssignments = new ConcurrentHashMap<>();

    // Chunked map transfer support
    private static final int CHUNK_SIZE = Constants.NETWORK_CHUNK_SIZE; // 8KB chunks
    private final Map<String, byte[]> serializedMaps = new ConcurrentHashMap<>(); // Cache serialized maps


    public GameServer(Random random, GameMap map, List<PlayerObject> players, ServerCoordinator serverCoordinator) {
        this.map = map;
        this.players = players;
        this.serverCoordinator = serverCoordinator;

        // Small buffers for gameplay connection (low latency for position updates)
        this.server = new Server(Constants.GAMEPLAY_BUFFER_SIZE, Constants.GAMEPLAY_BUFFER_SIZE);
        this.networkListener = new NetworkListener(this);

        KryoNetwork.register(server);
        NetworkManager.initialize(server);
        NetworkManager.onReceive(PlayerUpdate.class, this::handlePlayerUpdate);
        NetworkManager.onReceive(ClientStateChangeMessage.class, this::handleClientStateChangeMessage);
        NetworkManager.onReceive(ClientIdentificationMessage.class, this::handleClientIdentification);

        server.addListener(networkListener);

        // Create bulk transfer server (will be started in start() method)
        this.bulkServer = new BulkTransferServer();

        // Set ServerCoordinator reference on bulk server for profile tracking
        bulkServer.setServerCoordinator(serverCoordinator);

        Log.info("GameServer", "Initialized with dual-connection architecture (gameplay: 8KB, bulk: 64KB)");

//            @Override
//            public void received(Connection connection, Object object) {
//                if (object instanceof PlayerUpdate) {
//
//                } else if (object instanceof ClientReadyForMapMessage) {
//                    // Client is ready to receive new map data
//                    ClientReadyForMapMessage readyMessage = (ClientReadyForMapMessage) object;
//                    handleClientReadyForMap(connection, readyMessage);
//                }
//            }

        server.addListener(networkListener);
    }

    public void handleClientStateChangeMessage(Connection connection, ClientStateChangeMessage stateChangeMessage) {
        String clientKey = constructClientProfileKey(connection);
        serverCoordinator.updateClientState(clientKey, stateChangeMessage.oldState, stateChangeMessage.newState);
    }

    public void handleClientIdentification(Connection connection, ClientIdentificationMessage identificationMessage) {
        String clientKey = constructClientProfileKey(connection);
        ClientProfile profile = serverCoordinator.getClientProfile(clientKey);
        if (profile != null) {
            profile.clientUniqueId = identificationMessage.clientUniqueId;
            profile.userName = identificationMessage.clientName;
            Log.info("GameServer", "Client " + connection.getID() + " identified with uniqueId: " + identificationMessage.clientUniqueId);
        } else {
            Log.warn("GameServer", "Received identification from unknown client: " + connection.getID());
        }
    }

    public void handlePlayerUpdate(Connection connection, PlayerUpdate update) {
        // Received a player position update, broadcast to all other clients
        for (PlayerObject player : players) {
            if (player.entityId.equals(update.playerId)) {
                player.setPosition(new Vector3(update.x, update.y, update.z));
                player.setYaw(update.yaw);
                player.setPitch(update.pitch);
                break;
            }
        }

        // Only broadcast to OTHER clients (exclude the sender)
        for (Connection conn : server.getConnections()) {
            if (readyClients.contains(conn.getID()) && conn.getID() != connection.getID()) {
                conn.sendUDP(update);
            }
        }
    }

    /**
     * Starts the server and binds it to the specified ports.
     * @throws IOException if the server fails to start or bind to the ports
     */
    /**
     * Starts the server, binds to the configured ports, and attempts to set up port forwarding.
     * @throws IOException if the server fails to start or bind to the ports
     */
    public void start() throws IOException {
        // Start gameplay server
        server.start();
        server.bind(KryoNetwork.TCP_PORT, KryoNetwork.UDP_PORT);
        Log.info("Server", "Gameplay server started on TCP port " + KryoNetwork.TCP_PORT + " and UDP port " + KryoNetwork.UDP_PORT);

        // Start bulk transfer server
        bulkServer.start();
        Log.info("Server", "Bulk transfer server started on TCP port " + Constants.BULK_TRANSFER_TCP_PORT +
            " and UDP port " + Constants.BULK_TRANSFER_UDP_PORT);

        // Attempt to set up port forwarding for both servers
        if (KryoNetwork.setupPortForwarding("Game Server")) {
            Log.info("Server", "Successfully set up port forwarding");
        } else {
            Log.warn("Server", "Failed to set up automatic port forwarding. Players may not be able to connect from the internet.");
            Log.warn("Server", "Please configure port forwarding manually in your router settings if needed.");
        }
    }

    /**
     * Stops the server and releases all resources.
     */
    /**
     * Stops the server and cleans up any port forwarding rules.
     */
    public void stop() {
        if (server != null) {
            // Remove port forwarding rules
            KryoNetwork.removePortForwarding();
            server.stop();
            Log.info("Server", "Gameplay server stopped");
        }

        if (bulkServer != null) {
            bulkServer.stop();
            Log.info("Server", "Bulk transfer server stopped");
        }

        Log.info("Server", "All servers stopped and port forwarding rules removed");
    }

    /**
     * @return the underlying KryoNet gameplay server instance
     */
    public Server getServer() {
        return server;
    }

    /**
     * @return the bulk transfer server instance
     */
    public BulkTransferServer getBulkServer() {
        return bulkServer;
    }

    /**
     * Broadcasts a player's position to all connected clients
     * @param playerId The ID of the player whose position is being updated
     * @param position The new position of the player
     */
    public void broadcastPlayerPosition(String playerId, Vector3 position) {
        if (server != null) {
            PlayerUpdate update = new PlayerUpdate(playerId, position);
            server.sendToAllUDP(update); // Using UDP less reliable but faster updates
        }
    }

    public void broadcastNewPlayerRoster() {
        if (server != null) {
            try {
                PlayerObjectRosterUpdate update = createPlayerRosterUpdate();
                Log.info("GameServer", "Broadcasting player roster to all clients");
                NetworkManager.sendToAllClients(update);
                Log.info("GameServer", "Broadcast completed successfully");
            } catch (Exception e) {
                Log.error("GameServer", "Error broadcasting player roster: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void sendPlayerRosterToConnection(Connection connection) {
        if (server != null && connection != null) {
            try {
                PlayerObjectRosterUpdate update = createPlayerRosterUpdate();
                Log.info("GameServer", "Sending player roster directly to connection " + connection.getID());
                NetworkManager.sendToClient(connection.getID(), update);
                Log.info("GameServer", "Direct send completed successfully");
            } catch (Exception e) {
                Log.error("GameServer", "Error sending player roster to connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private PlayerObjectRosterUpdate createPlayerRosterUpdate() {
        PlayerObjectRosterUpdate update = new PlayerObjectRosterUpdate();
        PlayerObject[] playerRoster = new PlayerObject[players.size()];
        for (int i = 0; i < players.size(); i++) {
            playerRoster[i] = players.get(i);
        }
        update.players = playerRoster;
        return update;
    }

    public void assignPlayer(Connection connection, String playerId)
    {
        PlayerAssignmentUpdate assignmentUpdate = new PlayerAssignmentUpdate();
        assignmentUpdate.playerId = playerId;
        NetworkManager.sendToClient(connection.getID(), assignmentUpdate);

        // Mark this client as ready to receive position updates
        readyClients.add(connection.getID());
        Log.info("GameServer", "Client " + connection.getID() + " marked as ready for position updates");
    }

    public void broadcastPlayerDisconnect(String playerId) {
        if (server != null) {
            PlayerDisconnectUpdate disconnectUpdate = new PlayerDisconnectUpdate(playerId);
            NetworkManager.sendToAllClients(disconnectUpdate);
            Log.info("GameServer", "Broadcasting player disconnect for player " + playerId);
        }
    }

    public void sendMapRefreshToUser(Connection connection) {
        Log.info("GameServer", "Initiating map transfer for client " + connection.getID());

        boolean wasAlreadyInTransferState = ServerStateManager.getCurrentState() instanceof ServerMapTransferState;

        // Ensure we're in the correct state
        if (!wasAlreadyInTransferState) {
            // Transition to transfer state - start() will create workers for ALL clients
            ServerStateManager.setServerState(ServerMapTransferState.class);
        } else {
            // Already in transfer state (mid-transfer join scenario)
            // Explicitly create worker for this new client only
            // The start() method won't run again, so we need to handle it here
            ServerMapTransferState transferState = (ServerMapTransferState) ServerStateManager.getCurrentState();
            transferState.startTransferForClient(connection);
        }
    }



    // =====================================
    // INITIAL MAP GENERATION SUPPORT
    // =====================================

    /**
     * Checks if the server has an initial map available for distribution.
     * @return true if there's a map available, false if deferred generation is active
     */
    private boolean hasInitialMap() {
        return serverCoordinator.hasInitialMap();
    }

    /**
     * Triggers initial map generation when a client connects to a host without an initial map.
     * This routes the first connection through the regeneration workflow for consistency.
     */
    private void triggerInitialMapGeneration(Connection connection) {
        Log.info("GameServer", "No initial map available - triggering initial map generation for client " + connection.getID());

        // Generate a seed for the initial map
        long initialSeed = System.currentTimeMillis();

        // Use the regeneration system for initial map generation with the initial generation flag
        // This ensures consistency with the regeneration workflow and UI
        regenerateMap(initialSeed, "Initial map generation for host startup", true);
    }

    /**
     * Completes pending player assignments that were deferred during initial map generation.
     * This should be called after the initial map has been generated and sent to clients.
     */
    private void completePendingPlayerAssignments() {
        if (pendingPlayerAssignments.isEmpty()) {
            Log.debug("GameServer", "No pending player assignments to complete");
            return;
        }

        Log.info("GameServer", "Completing " + pendingPlayerAssignments.size() + " pending player assignments");

        for (Map.Entry<Integer, String> entry : pendingPlayerAssignments.entrySet()) {
            Integer connectionId = entry.getKey();
            String playerId = entry.getValue();

            // Find the connection object
            Connection connection = null;
            for (Connection conn : server.getConnections()) {
                if (conn.getID() == connectionId) {
                    connection = conn;
                    break;
                }
            }

            if (connection != null) {
                Log.info("GameServer", "Sending deferred player assignment to client " + connectionId + " (player: " + playerId + ")");

                // Send player roster first
                sendPlayerRosterToConnection(connection);
                // Then assign the player
                assignPlayer(connection, playerId);
            } else {
                Log.warn("GameServer", "Could not find connection " + connectionId + " for pending player assignment");
            }
        }

        // Clear pending assignments
        pendingPlayerAssignments.clear();
        Log.info("GameServer", "All pending player assignments completed");
    }

    // =====================================
    // MAP REGENERATION FUNCTIONALITY
    // =====================================

    /**
     * Regenerates the server map with a new seed and broadcasts it to all clients.
     * This triggers a complete resource cleanup and reload cycle on all clients.
     *
     * @param newSeed The seed for the new map generation
     * @param reason Optional reason for regeneration (for logging)
     */
    public void regenerateMap(long newSeed, String reason) {
        regenerateMap(newSeed, reason, false);
    }

    /**
     * Regenerates the server map with a new seed and broadcasts it to all clients.
     * This triggers a complete resource cleanup and reload cycle on all clients.
     *
     * @param newSeed The seed for the new map generation
     * @param reason Optional reason for regeneration (for logging)
     * @param isInitialGeneration Whether this is initial generation (host startup) vs regeneration
     */
    public void regenerateMap(long newSeed, String reason, boolean isInitialGeneration) {
        if (isRegenerating) {
            Log.warn("GameServer", "Map regeneration already in progress, ignoring request");
            return;
        }

        // Wait for any previous regeneration thread to finish
        if (regenerationThread != null && regenerationThread.isAlive()) {
            Log.info("GameServer", "Waiting for previous regeneration thread to complete...");
            try {
                regenerationThread.join(5000); // Wait up to 5 seconds
                if (regenerationThread.isAlive()) {
                    Log.warn("GameServer", "Previous regeneration thread didn't complete in time, proceeding anyway");
                }
            } catch (InterruptedException e) {
                Log.warn("GameServer", "Interrupted while waiting for previous regeneration thread");
                Thread.currentThread().interrupt();
            }
        }

        Log.info("GameServer", "Starting map regeneration with seed: " + newSeed +
                 (reason != null ? " (Reason: " + reason + ")" : ""));

        isRegenerating = true;
        currentRegenerationId = System.currentTimeMillis();
        clientsReadyForMap.clear();

        // Start regeneration in a separate thread to avoid blocking
        regenerationThread = new Thread(() -> {
            try {
                // Step 1: Notify all clients that map regeneration is starting
                MapRegenerationStartMessage startMessage = new MapRegenerationStartMessage(currentRegenerationId, newSeed, reason, isInitialGeneration);
                NetworkManager.sendToAllClients(startMessage);
                Log.info("GameServer", "Sent regeneration start message to all clients, waiting for readiness confirmations...");

                // Step 2: Wait for all clients to confirm they're ready
//                if (!waitForAllClientsReady()) {
//                    Log.error("GameServer", "Timeout waiting for clients to be ready, proceeding anyway");
//                }

                // Step 3: Clear cached map data to force fresh serialization
                serializedMaps.clear();

                // Step 4: Ask the game world to regenerate the map
                if (serverCoordinator != null) {
                    serverCoordinator.regenerateMap(newSeed);
                    Log.info("GameServer", "Map regenerated by game world");
                } else {
                    Log.error("GameServer", "Cannot regenerate map - game world is null");
                    return;
                }

                // Step 5: Send new map to all connected clients (now they're ready!)
                Log.info("GameServer", "Broadcasting new map to all ready clients");
                for (Connection connection : server.getConnections()) {
                    sendMapRefreshToUser(connection);
                }

                // Step 5.1: Complete any pending player assignments for initial generation
                completePendingPlayerAssignments();

                // Step 6: Reset all players to new spawn locations
                resetAllPlayersToSpawn();

                Log.info("GameServer", "Map regeneration completed successfully");

            } catch (Exception e) {
                Log.error("GameServer", "Failed to regenerate map: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isRegenerating = false;
                clientsReadyForMap.clear();
            }
        });

        regenerationThread.start();
    }

    /**
     * Regenerates the map using a random seed.
     *
     * @param reason Optional reason for regeneration
     */
    public void regenerateMapRandom(String reason) {
        long newSeed = System.currentTimeMillis();
        regenerateMap(newSeed, reason != null ? reason : "Random regeneration");
    }

    /**
     * Resets all connected players to spawn locations on the new map.
     */
    private void resetAllPlayersToSpawn() {
        if (map == null) {
            Log.error("GameServer", "Cannot reset players - map is null");
            return;
        }

        try {
            // Get all spawn points from the new map
            java.util.ArrayList<MapHint> spawnHints = map.getAllHintsOfType(SpawnPointHint.class);

            if (spawnHints.isEmpty()) {
                Log.warn("GameServer", "No spawn points found in new map");
                return;
            }

            Log.info("GameServer", "Resetting " + connectionToPlayerMap.size() + " players to spawn locations");

            int spawnIndex = 0;
            for (Map.Entry<Integer, String> entry : connectionToPlayerMap.entrySet()) {
                int connectionId = entry.getKey();
                String playerId = entry.getValue();

                // Use spawn points in rotation if there are more players than spawn points
                MapHint spawnHint = spawnHints.get(spawnIndex % spawnHints.size());
                MapTile spawnTile = map.getTile(spawnHint.tileLookupKey);

                Vector3 spawnPosition;
                if (spawnTile != null) {
                    // Spawn well above the tile to avoid clipping into floor
                    spawnPosition = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
                    Log.info("GameServer", "Player spawn position calculated: " + spawnPosition + " (tile Y: " + spawnTile.y + ")");
                } else {
                    Log.warn("GameServer", "Could not find spawn tile for hint, using default position");
                    spawnPosition = new Vector3(15, 1, 15);
                }

                // Send reset message to specific client
                PlayerResetMessage resetMessage = new PlayerResetMessage(playerId, spawnPosition, 0f);

                Connection connection = null;
                for (Connection conn : server.getConnections()) {
                    if (conn.getID() == connectionId) {
                        connection = conn;
                        break;
                    }
                }
                if (connection != null) {
                    NetworkManager.sendToClient(connection.getID(), resetMessage);
                    Log.info("GameServer", "Sent player reset to " + playerId +
                             " at position: " + spawnPosition);
                }

                spawnIndex++;
            }

        } catch (Exception e) {
            Log.error("GameServer", "Failed to reset players to spawn: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Triggers map regeneration for debugging/admin purposes.
     * Can be called from game commands or server console.
     */
    public void debugRegenerateMap() {
        regenerateMapRandom("Debug/Admin triggered");
    }

    /**
     * Handle client readiness confirmation for map regeneration
     */
    private void handleClientReadyForMap(Connection connection, ClientReadyForMapMessage readyMessage) {
        if (!isRegenerating || readyMessage.regenerationId != currentRegenerationId) {
            Log.warn("GameServer", "Received client ready message for wrong/old regeneration ID: " +
                     readyMessage.regenerationId + " (current: " + currentRegenerationId + ")");
            return;
        }

        synchronized (clientsReadyForMap) {
            clientsReadyForMap.add(connection.getID());
            Log.info("GameServer", "Client " + connection.getID() + " ready for map transfer (" +
                     clientsReadyForMap.size() + "/" + server.getConnections().size() + ")");

            // Notify waiting thread
            clientsReadyForMap.notifyAll();
        }
    }

    /**
     * Wait for all connected clients to confirm they're ready for map transfer
     */
    private boolean waitForAllClientsReady() {
        int totalClients = server.getConnections().size();
        if (totalClients == 0) {
            Log.info("GameServer", "No clients connected, proceeding with regeneration");
            return true;
        }

        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds timeout

        synchronized (clientsReadyForMap) {
            while (clientsReadyForMap.size() < totalClients) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeout) {
                    Log.warn("GameServer", "Timeout waiting for clients: " +
                             clientsReadyForMap.size() + "/" + totalClients + " ready");
                    return false;
                }

                try {
                    clientsReadyForMap.wait(timeout - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        Log.info("GameServer", "All " + totalClients + " clients are ready for map transfer");
        return true;
    }

    // =====================================
    // CONNECTION EVENT HANDLERS
    // =====================================

    /**
     * Handles a client connection event from NetworkListener
     */
    public void handleClientConnected(Connection connection) {
        // Register client profile in ServerCoordinator
        String clientKey = constructClientProfileKey(connection);
        serverCoordinator.registerClientProfile(clientKey);

        // Set gameplay connection ID in profile
        ClientProfile profile = serverCoordinator.getClientProfile(clientKey);
        if (profile != null) {
            profile.gameplayConnectionId = connection.getID();
            Log.info("GameServer", "Set gameplay connection ID " + connection.getID() + " for client profile: " + clientKey);
        }

        // Check if we have an initial map or need to generate one
        if (hasInitialMap()) {
            Log.info("connected", "Player is connecting to server");

            // Standard flow: send existing map, then assign player immediately
            sendMapRefreshToUser(connection);

            PlayerObject newPlayer = PlayerUtilities.createServerPlayerObject();
            players.add(newPlayer);

            connectionToPlayerMap.put(connection.getID(), newPlayer.entityId);
            broadcastNewPlayerRoster();
            // Also send roster directly to the new connection to ensure they get it
            sendPlayerRosterToConnection(connection);
            assignPlayer(connection, newPlayer.entityId);
        } else {
            // New flow: trigger initial map generation, delay player assignment until after generation
            Log.info("GameServer", "Deferring player assignment until after initial map generation for client " + connection.getID());

            // Create player but don't send assignment yet - store for later
            PlayerObject newPlayer = PlayerUtilities.createServerPlayerObject();
            players.add(newPlayer);
            connectionToPlayerMap.put(connection.getID(), newPlayer.entityId);

            // Store connection info for later player assignment
            pendingPlayerAssignments.put(connection.getID(), newPlayer.entityId);

            triggerInitialMapGeneration(connection);
        }
    }

    /**
     * Handles a client disconnection event from NetworkListener
     */
    public void handleClientDisconnected(Connection connection) {
        // Update client profile status to disconnected
        String clientKey = constructClientProfileKey(connection);
        ClientProfile profile = serverCoordinator.getClientProfile(clientKey);
        if (profile != null) {
            profile.connectionStatus = ConnectionStatus.DISCONNECTED;
            Log.info("GameServer", "Client profile marked as disconnected: " + clientKey);
        }

        // Remove from ready clients
        readyClients.remove(connection.getID());

        // Remove any pending player assignment for this connection
        String pendingPlayerId = pendingPlayerAssignments.remove(connection.getID());
        if (pendingPlayerId != null) {
            Log.info("GameServer", "Removed pending player assignment for disconnected client " + connection.getID());
        }

        // Find and remove the disconnected player using the connection mapping
        String playerId = connectionToPlayerMap.remove(connection.getID());
        if (playerId != null) {
            PlayerObject disconnectedPlayer = null;
            for (PlayerObject player : players) {
                if (player.entityId.equals(playerId)) {
                    disconnectedPlayer = player;
                    break;
                }
            }

            if (disconnectedPlayer != null) {
                players.remove(disconnectedPlayer);

                // Remove the disconnected player's light from the server's environment
//                        serverCoordinator.removePlayerFromEnvironment(disconnectedPlayer);

                // Broadcast disconnect message to all remaining clients
                broadcastPlayerDisconnect(disconnectedPlayer.entityId);

                broadcastNewPlayerRoster();
                Log.info("Server", "Player " + disconnectedPlayer.entityId + " disconnected");
            }
        }
    }

    public String constructClientProfileKey(Connection connection) {
        return String.valueOf(connection.getID());
    }
}
