package curly.octo.network;

import curly.octo.Constants;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.GameWorld;
import curly.octo.map.GameMap;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.network.messages.MapChunkMessage;
import curly.octo.network.messages.MapTransferStartMessage;
import curly.octo.network.messages.MapTransferCompleteMessage;
import curly.octo.network.messages.MapRegenerationStartMessage;
import curly.octo.network.messages.ClientReadyForMapMessage;
import curly.octo.network.messages.PlayerResetMessage;
import curly.octo.network.messages.PlayerAssignmentUpdate;
import curly.octo.network.messages.PlayerDisconnectUpdate;
import curly.octo.network.messages.PlayerObjectRosterUpdate;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.gameobjects.PlayerObject;
import curly.octo.player.PlayerUtilities;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

/**
 * Handles server-side network operations.
 */
public class GameServer {
    private final Server server;
    private final NetworkListener networkListener;
    private final GameMap map;
    private final List<PlayerObject> players;
    private final GameWorld gameWorld;
    private final Map<Integer, String> connectionToPlayerMap = new HashMap<>();
    private final Set<Integer> readyClients = new HashSet<>(); // Track clients that have received map and assignment
    
    // Map regeneration state tracking
    private volatile boolean isRegenerating = false;
    private long currentRegenerationId = 0;
    private final Set<Integer> clientsReadyForMap = new HashSet<>();
    private Thread regenerationThread;
    
    // Chunked map transfer support
    private static final int CHUNK_SIZE = Constants.NETWORK_CHUNK_SIZE; // 8KB chunks
    private final Map<String, byte[]> serializedMaps = new ConcurrentHashMap<>(); // Cache serialized maps

    public GameServer(Random random, GameMap map, List<PlayerObject> players, GameWorld gameWorld) {
        this.map = map;
        this.players = players;
        this.gameWorld = gameWorld;
        // Small buffers for fast network operations - maps will be transferred in chunks
        this.server = new Server(Constants.NETWORK_BUFFER_SIZE, Constants.NETWORK_BUFFER_SIZE); // 16KB read/write buffers
        this.networkListener = new NetworkListener(server);

        // Register all network classes
        Network.register(server);

        // Add connection listener for logging
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                sendMapRefreshToUser(connection);
                PlayerObject newPlayer = PlayerUtilities.createServerPlayerObject();
                players.add(newPlayer);

                connectionToPlayerMap.put(connection.getID(), newPlayer.entityId);
                broadcastNewPlayerRoster();
                // Also send roster directly to the new connection to ensure they get it
                sendPlayerRosterToConnection(connection);
                assignPlayer(connection, newPlayer.entityId);


                // Safe logging of connection address
                String address = "unknown";
                try {
                    if (connection.getRemoteAddressTCP() != null) {
                        address = connection.getRemoteAddressTCP().getAddress().toString();
                    }
                } catch (Exception e) {
                    Log.warn("Server", "Could not get remote address: " + e.getMessage());
                }
                Log.info("Server", "Player " + newPlayer.entityId + " connected from " + address);
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof PlayerUpdate) {
                    // Received a player position update, broadcast to all other clients
                    PlayerUpdate update = (PlayerUpdate) object;
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
                } else if (object instanceof ClientReadyForMapMessage) {
                    // Client is ready to receive new map data
                    ClientReadyForMapMessage readyMessage = (ClientReadyForMapMessage) object;
                    handleClientReadyForMap(connection, readyMessage);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                // Remove from ready clients
                readyClients.remove(connection.getID());

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
//                        gameWorld.removePlayerFromEnvironment(disconnectedPlayer);

                        // Broadcast disconnect message to all remaining clients
                        broadcastPlayerDisconnect(disconnectedPlayer.entityId);

                        broadcastNewPlayerRoster();
                        Log.info("Server", "Player " + disconnectedPlayer.entityId + " disconnected");
                    }
                }
            }
        });

        server.addListener(networkListener);
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
        server.start();
        server.bind(Network.TCP_PORT, Network.UDP_PORT);
        Log.info("Server", "Server started on TCP port " + Network.TCP_PORT + " and UDP port " + Network.UDP_PORT);

        // Attempt to set up port forwarding
        if (Network.setupPortForwarding("Game Server")) {
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
            Network.removePortForwarding();
            server.stop();
            Log.info("Server", "Server stopped and port forwarding rules removed");
        }
    }

    /**
     * @return the underlying KryoNet server instance
     */
    public Server getServer() {
        return server;
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
                server.sendToAllTCP(update);
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
                connection.sendTCP(update);
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
        server.sendToTCP(connection.getID(), assignmentUpdate);

        // Mark this client as ready to receive position updates
        readyClients.add(connection.getID());
        Log.info("GameServer", "Client " + connection.getID() + " marked as ready for position updates");
    }

    public void broadcastPlayerDisconnect(String playerId) {
        if (server != null) {
            PlayerDisconnectUpdate disconnectUpdate = new PlayerDisconnectUpdate(playerId);
            server.sendToAllTCP(disconnectUpdate);
            Log.info("GameServer", "Broadcasting player disconnect for player " + playerId);
        }
    }

    public void sendMapRefreshToUser(Connection connection) {
        String mapId = "map_" + System.currentTimeMillis(); // Unique map ID
        Log.info("Server", "Starting chunked map transfer to client " + connection.getID() + " (mapId: " + mapId + ")");
        
        try {
            // Serialize the map (with caching)
            byte[] mapData = getSerializedMapData();
            int totalChunks = (int) Math.ceil((double) mapData.length / CHUNK_SIZE);
            
            Log.info("Server", "Map size: " + mapData.length + " bytes, " + totalChunks + " chunks");
            
            // Send transfer start message
            MapTransferStartMessage startMessage = new MapTransferStartMessage(mapId, totalChunks, mapData.length);
            server.sendToTCP(connection.getID(), startMessage);
            
            // Send chunks in sequence
            for (int i = 0; i < totalChunks; i++) {
                int offset = i * CHUNK_SIZE;
                int chunkLength = Math.min(CHUNK_SIZE, mapData.length - offset);
                
                byte[] chunkData = new byte[chunkLength];
                System.arraycopy(mapData, offset, chunkData, 0, chunkLength);
                
                MapChunkMessage chunkMessage = new MapChunkMessage(mapId, i, totalChunks, chunkData);
                server.sendToTCP(connection.getID(), chunkMessage);
                
                // Small delay between chunks to avoid overwhelming the network
                if (i > 0 && i % 10 == 0) {
                    Thread.sleep(1); // 1ms pause every 10 chunks
                }
            }
            
            // Send transfer complete message
            MapTransferCompleteMessage completeMessage = new MapTransferCompleteMessage(mapId);
            server.sendToTCP(connection.getID(), completeMessage);
            
            Log.info("Server", "Chunked map transfer completed to client " + connection.getID());
            
        } catch (Exception e) {
            Log.error("Server", "Error sending chunked map data to client " + connection.getID() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Serializes the map data using Kryo and caches it for reuse.
     * @return serialized map data as byte array
     */
    private byte[] getSerializedMapData() throws IOException {
        // Get the current map from the game world (not the constructor field)
        GameMap currentMap = (gameWorld != null && gameWorld.getMapManager() != null) ? 
                            gameWorld.getMapManager() : map;
        
        // Create cache key based on map hash to ensure new maps get fresh serialization
        String cacheKey = "map_" + currentMap.hashCode();
        
        byte[] cachedData = serializedMaps.get(cacheKey);
        if (cachedData != null) {
            Log.info("Server", "Using cached map data for hash " + currentMap.hashCode() + 
                     " (" + cachedData.length + " bytes)");
            return cachedData;
        }
        
        // Serialize the current map using Kryo (same as KryoNet uses)
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            
            // Get the server's Kryo instance (already configured with our registrations)
            Kryo kryo = server.getKryo();
            kryo.writeObject(output, currentMap);
            output.flush();
            
            byte[] mapData = baos.toByteArray();
            serializedMaps.put(cacheKey, mapData); // Cache with hash-based key
            
            Log.info("Server", "Serialized and cached NEW map data with hash " + currentMap.hashCode() + 
                     " (" + mapData.length + " bytes, " + currentMap.getAllTiles().size() + " tiles)");
            return mapData;
        }
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
                MapRegenerationStartMessage startMessage = new MapRegenerationStartMessage(currentRegenerationId, newSeed, reason);
                server.sendToAllTCP(startMessage);
                Log.info("GameServer", "Sent regeneration start message to all clients, waiting for readiness confirmations...");
                
                // Step 2: Wait for all clients to confirm they're ready
                if (!waitForAllClientsReady()) {
                    Log.error("GameServer", "Timeout waiting for clients to be ready, proceeding anyway");
                }
                
                // Step 3: Clear cached map data to force fresh serialization
                serializedMaps.clear();
                
                // Step 4: Ask the game world to regenerate the map
                if (gameWorld != null) {
                    gameWorld.regenerateMap(newSeed);
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
            java.util.ArrayList<curly.octo.map.hints.MapHint> spawnHints = map.getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
            
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
                curly.octo.map.hints.MapHint spawnHint = spawnHints.get(spawnIndex % spawnHints.size());
                curly.octo.map.MapTile spawnTile = map.getTile(spawnHint.tileLookupKey);
                
                Vector3 spawnPosition;
                if (spawnTile != null) {
                    // Spawn well above the tile to avoid clipping into floor
                    spawnPosition = new Vector3(spawnTile.x, spawnTile.y + 5f, spawnTile.z);
                    Log.info("GameServer", "Player spawn position calculated: " + spawnPosition + " (tile Y: " + spawnTile.y + ")");
                } else {
                    Log.warn("GameServer", "Could not find spawn tile for hint, using default position");
                    spawnPosition = new Vector3(15, 25, 15);
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
                    connection.sendTCP(resetMessage);
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
}
