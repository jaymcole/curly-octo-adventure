package curly.octo.network;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.GameWorld;
import curly.octo.map.GameMap;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.network.messages.PlayerAssignmentUpdate;
import curly.octo.network.messages.PlayerDisconnectUpdate;
import curly.octo.network.messages.PlayerObjectRosterUpdate;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.gameobjects.PlayerObject;
import curly.octo.player.PlayerUtilities;

import java.io.IOException;
import java.util.*;

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

    public GameServer(Random random, GameMap map, List<PlayerObject> players, GameWorld gameWorld) {
        this.map = map;
        this.players = players;
        this.gameWorld = gameWorld;
        // Increased buffer sizes for large map transfers (5MB each)
        this.server = new Server(50000000, 50000000);
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

                connectionToPlayerMap.put(connection.getID(), newPlayer.getPlayerId());
                broadcastNewPlayerRoster();
                // Also send roster directly to the new connection to ensure they get it
                sendPlayerRosterToConnection(connection);
                assignPlayer(connection, newPlayer.getPlayerId());

                // Send another roster after a delay to test if timing is the issue
                new Thread(() -> {
                    try {
                        Thread.sleep(1000); // Wait 1 second
                        Log.info("GameServer", "Sending delayed roster to connection " + connection.getID());
                        sendPlayerRosterToConnection(connection);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                // Safe logging of connection address
                String address = "unknown";
                try {
                    if (connection.getRemoteAddressTCP() != null) {
                        address = connection.getRemoteAddressTCP().getAddress().toString();
                    }
                } catch (Exception e) {
                    Log.warn("Server", "Could not get remote address: " + e.getMessage());
                }
                Log.info("Server", "Player " + newPlayer.getPlayerId() + " connected from " + address);
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof PlayerUpdate) {
                    // Received a player position update, broadcast to all other clients
                    PlayerUpdate update = (PlayerUpdate) object;
                    // Log.debug("Server", "Received position update for player " + update.playerId + ": " +
                    //     update.x + ", " + update.y + ", " + update.z);

                    // Update the player's position in our local list
                    for (PlayerObject player : players) {
                        if (player.getPlayerId().equals(update.playerId)) {
                            player.setPosition(new Vector3(update.x, update.y, update.z));
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
                        if (player.getPlayerId().equals(playerId)) {
                            disconnectedPlayer = player;
                            break;
                        }
                    }

                    if (disconnectedPlayer != null) {
                        players.remove(disconnectedPlayer);

                        // Remove the disconnected player's light from the server's environment
//                        gameWorld.removePlayerFromEnvironment(disconnectedPlayer);

                        // Broadcast disconnect message to all remaining clients
                        broadcastPlayerDisconnect(disconnectedPlayer.getPlayerId());

                        broadcastNewPlayerRoster();
                        Log.info("Server", "Player " + disconnectedPlayer.getPlayerId() + " disconnected");
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
            server.sendToAllUDP(update); // Using UDP for faster, less reliable but faster updates
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
        Log.info("Server", "Sending map data to client " + connection.getID());
        MapDataUpdate mapUpdate = new MapDataUpdate(map);
        server.sendToTCP(connection.getID(), mapUpdate);
        Log.info("Server", "Map data sent to client " + connection.getID());

    }
}
