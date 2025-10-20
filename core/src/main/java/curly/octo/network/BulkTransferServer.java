package curly.octo.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.network.messages.ClientIdentificationMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated KryoNet server for bulk data transfers (map transfers).
 * Uses large buffers (64KB) for maximum throughput.
 * Runs alongside the main gameplay server on separate ports.
 * Tracks clients by their unique ID to match with gameplay connections.
 */
public class BulkTransferServer {
    private Server server;
    private final NetworkListener networkListener;
    private boolean running = false;
    private curly.octo.game.HostGameWorld hostGameWorld = null;  // Set by GameServer

    // Track bulk connections by client unique ID
    private final Map<String, Connection> clientIdToConnection = new HashMap<>();

    public BulkTransferServer() {
        // Create server with large buffers for bulk transfers
        this.server = new Server(
            Constants.BULK_TRANSFER_BUFFER_SIZE,
            Constants.BULK_TRANSFER_BUFFER_SIZE
        );

        // Register network classes
        Network.register(server);

        // Create listener (will need GameServer reference for callbacks)
        this.networkListener = new NetworkListener(null);
        server.addListener(networkListener);

        // Add listener for client identification messages
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Log.info("BulkTransferServer", "Client connected - waiting for identification (connection ID: " +
                    connection.getID() + ")");
            }

            @Override
            public void received(Connection connection, Object object) {
                Log.debug("BulkTransferServer", "Received message: " + object.getClass().getSimpleName() +
                    " from connection " + connection.getID());

                if (object instanceof ClientIdentificationMessage) {
                    ClientIdentificationMessage msg = (ClientIdentificationMessage) object;
                    registerClient(msg.clientUniqueId, connection);
                    Log.info("BulkTransferServer", "Registered bulk connection for client: " + msg.clientUniqueId +
                        " (connection ID: " + connection.getID() + ")");
                } else {
                    Log.warn("BulkTransferServer", "Received non-identification message before client registered: " +
                        object.getClass().getName());
                }
            }

            @Override
            public void disconnected(Connection connection) {
                String clientUniqueId = unregisterClient(connection);
                if (clientUniqueId != null) {
                    Log.info("BulkTransferServer", "Client disconnected and cleaned up: " + clientUniqueId +
                        " (connection ID: " + connection.getID() + ")");
                } else {
                    Log.info("BulkTransferServer", "Unidentified client disconnected (connection ID: " +
                        connection.getID() + ")");
                }
            }
        });

        Log.info("BulkTransferServer", "Initialized with " +
            Constants.BULK_TRANSFER_BUFFER_SIZE + "B buffers");
    }

    /**
     * Set the HostGameWorld reference (called by GameServer).
     */
    public void setHostGameWorld(curly.octo.game.HostGameWorld hostGameWorld) {
        this.hostGameWorld = hostGameWorld;
    }

    /**
     * Register a client's bulk connection by their unique ID.
     */
    private synchronized void registerClient(String clientUniqueId, Connection connection) {
        clientIdToConnection.put(clientUniqueId, connection);

        // Update ClientProfile with bulk connection ID
        if (hostGameWorld != null) {
            // Find the profile with this clientUniqueId
            for (curly.octo.game.serverObjects.ClientProfile profile : hostGameWorld.getClientProfiles().values()) {
                if (profile != null && clientUniqueId.equals(profile.clientUniqueId)) {
                    profile.bulkConnectionId = connection.getID();
                    Log.info("BulkTransferServer", "Set bulk connection ID " + connection.getID() +
                        " for client " + clientUniqueId);
                    break;
                }
            }
        }
    }

    /**
     * Unregister a client's bulk connection when they disconnect.
     * Cleans up the clientIdToConnection map and clears bulkConnectionId in profile.
     * @param connection the disconnected connection
     * @return the clientUniqueId that was removed, or null if connection was never registered
     */
    private synchronized String unregisterClient(Connection connection) {
        // Find the clientUniqueId by searching the map for this connection
        String clientUniqueId = null;
        for (Map.Entry<String, Connection> entry : clientIdToConnection.entrySet()) {
            if (entry.getValue() == connection || entry.getValue().getID() == connection.getID()) {
                clientUniqueId = entry.getKey();
                break;
            }
        }

        if (clientUniqueId == null) {
            // Connection was never registered (disconnected before sending identification)
            return null;
        }

        // Remove from map
        clientIdToConnection.remove(clientUniqueId);

        // Clear bulkConnectionId in ClientProfile
        if (hostGameWorld != null) {
            for (curly.octo.game.serverObjects.ClientProfile profile : hostGameWorld.getClientProfiles().values()) {
                if (profile != null && clientUniqueId.equals(profile.clientUniqueId)) {
                    profile.bulkConnectionId = null;
                    Log.info("BulkTransferServer", "Cleared bulk connection ID for client " + clientUniqueId);
                    break;
                }
            }
        }

        return clientUniqueId;
    }

    /**
     * Get a bulk connection by client unique ID (reliable cross-server matching).
     */
    public synchronized Connection getConnectionByClientId(String clientUniqueId) {
        return clientIdToConnection.get(clientUniqueId);
    }

    /**
     * Start the bulk transfer server and bind to ports.
     */
    public void start() throws IOException {
        if (running) {
            Log.warn("BulkTransferServer", "Already running");
            return;
        }

        server.start();
        server.bind(
            Constants.BULK_TRANSFER_TCP_PORT,
            Constants.BULK_TRANSFER_UDP_PORT
        );

        running = true;
        Log.info("BulkTransferServer", "Started on TCP port " +
            Constants.BULK_TRANSFER_TCP_PORT + " and UDP port " +
            Constants.BULK_TRANSFER_UDP_PORT);
    }

    /**
     * Stop the bulk transfer server.
     */
    public void stop() {
        if (!running) {
            return;
        }

        Log.info("BulkTransferServer", "Stopping bulk transfer server");

        if (server != null) {
            server.stop();
            running = false;
        }

        Log.info("BulkTransferServer", "Stopped");
    }

    /**
     * Get the underlying KryoNet server.
     */
    public Server getServer() {
        return server;
    }

    /**
     * Get a connection by ID (matches gameplay server connection ID).
     * KryoNet doesn't guarantee matching IDs across different server instances,
     * but connections are created in order, so this is a best-effort match.
     */
    public Connection getConnection(int connectionId) {
        if (server == null) {
            return null;
        }

        Connection[] connections = server.getConnections().toArray(new Connection[0]);
        for (Connection conn : connections) {
            if (conn.getID() == connectionId) {
                return conn;
            }
        }

        return null;
    }

    /**
     * Get all active connections as array.
     */
    public Connection[] getConnections() {
        if (server == null) {
            return new Connection[0];
        }
        return server.getConnections().toArray(new Connection[0]);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        stop();
        if (server != null) {
            try {
                server.dispose();
            } catch (Exception e) {
                Log.error("BulkTransferServer", "Error disposing server: " + e.getMessage());
            }
            server = null;
        }
    }
}
