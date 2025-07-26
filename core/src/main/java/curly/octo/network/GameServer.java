package curly.octo.network;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.network.messages.PlayerAssignmentUpdate;
import curly.octo.network.messages.PlayerRosterUpdate;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.player.PlayerController;
import curly.octo.player.PlayerUtilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handles server-side network operations.
 */
public class GameServer {
    private final Server server;
    private final NetworkListener networkListener;
    private final GameMap map;
    private final List<PlayerController> players;
    private final Map<Integer, Long> connectionToPlayerMap = new HashMap<>();

    public GameServer(Random random, GameMap map, List<PlayerController> players) {
        this.map = map;
        this.players = players;
        this.server = new Server(655360, 655360);
        this.networkListener = new NetworkListener(server);

        // Register all network classes
        Network.register(server);

        // Set up network listener
//        networkListener.setRotationListener(rotation -> {
//            if (this.rotationListener != null) {
//                this.rotationListener.onCubeRotationUpdate(rotation);
//            }
//        });

        // Add connection listener for logging
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                sendMapRefreshToUser(connection);
                PlayerController newPlayer = PlayerUtilities.createPlayerController(random);
                players.add(newPlayer);
                connectionToPlayerMap.put(connection.getID(), newPlayer.getPlayerId());
                broadcastNewPlayerRoster();
                assignPlayer(connection, newPlayer.getPlayerId());

                Log.info("Server", "Player " + newPlayer.getPlayerId() + " connected from " +
                    connection.getRemoteAddressTCP().getAddress());
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof PlayerUpdate) {
                    // Received a player position update, broadcast to all other clients
                    PlayerUpdate update = (PlayerUpdate) object;
                    Log.debug("Server", "Received position update for player " + update.playerId + ": " +
                        update.x + ", " + update.y + ", " + update.z);

                    // Update the player's position in our local list
                    for (PlayerController player : players) {
                        if (player.getPlayerId() == update.playerId) {
                            player.setPlayerPosition(update.x, update.y, update.z);
                            break;
                        }
                    }

                    // Broadcast to all clients (including the sender for host player visibility)
                    server.sendToAllUDP(update);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                // Find and remove the disconnected player using the connection mapping
                Long playerId = connectionToPlayerMap.remove(connection.getID());
                if (playerId != null) {
                    PlayerController disconnectedPlayer = null;
                    for (PlayerController player : players) {
                        if (player.getPlayerId() == playerId) {
                            disconnectedPlayer = player;
                            break;
                        }
                    }
                    
                    if (disconnectedPlayer != null) {
                        players.remove(disconnectedPlayer);
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
    public void broadcastPlayerPosition(long playerId, Vector3 position) {
        if (server != null) {
            PlayerUpdate update = new PlayerUpdate(playerId, position);
            server.sendToAllUDP(update); // Using UDP for faster, less reliable but faster updates
            Log.debug("Server", "Broadcasting position update for player " + playerId + ": " +
                position.x + ", " + position.y + ", " + position.z);
        }
    }

    public void broadcastNewPlayerRoster() {
        if (server != null) {
            PlayerRosterUpdate update = new PlayerRosterUpdate();
            PlayerController[] playerRoster = new PlayerController[players.size()];
            for(int i = 0; i < playerRoster.length; i++) {
                playerRoster[i] = players.get(i);
            }
            update.players = playerRoster;
            server.sendToAllTCP(update);
        }
    }

    public void assignPlayer(Connection connection, long playerId)
    {
        PlayerAssignmentUpdate assignmentUpdate = new PlayerAssignmentUpdate();
        assignmentUpdate.playerId = playerId;
        server.sendToTCP(connection.getID(), assignmentUpdate);
    }

    public void sendMapRefreshToUser(Connection connection) {
        Log.info("Server", "Sending map data to client " + connection.getID());
        MapDataUpdate mapUpdate = new MapDataUpdate(map);
        Log.info("Server", "Created MapDataUpdate with map size: " + map.getWidth() + "x" + map.getHeight() + "x" + map.getDepth());
        server.sendToTCP(connection.getID(), mapUpdate);
        Log.info("Server", "Map data sent to client " + connection.getID());

    }
}
