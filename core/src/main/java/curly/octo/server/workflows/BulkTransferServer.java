package curly.octo.server.workflows;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.network.messages.ClientIdentificationMessage;
import curly.octo.server.NetworkListener;
import curly.octo.server.ServerCoordinator;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.common.network.KryoNetwork;
import curly.octo.server.playerManagement.ClientUniqueId;

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
    private ServerCoordinator serverCoordinator = null;  // Set by GameServer

    // Track bulk connections by client unique ID
    private final Map<ClientUniqueId, Connection> clientIdToConnection = new HashMap<>();

    public BulkTransferServer() {
        // Create server with large buffers for bulk transfers
        this.server = new Server(
            Constants.BULK_TRANSFER_BUFFER_SIZE,
            Constants.BULK_TRANSFER_BUFFER_SIZE
        );

        // Register network classes
        KryoNetwork.register(server);

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
                Log.info("BulkTransferServer", "Received message: " + object.getClass().getSimpleName() +
                    " from connection " + connection.getID());

                if (object instanceof ClientIdentificationMessage) {
                    ClientIdentificationMessage msg = (ClientIdentificationMessage) object;
                    registerClient(new ClientUniqueId(msg.clientUniqueId), connection);
                    Log.info("BulkTransferServer", "Registered bulk connection for client: " + msg.clientUniqueId +
                        " (connection ID: " + connection.getID() + ")");
                } else {
                    Log.warn("BulkTransferServer", "Received non-identification message before client registered: " +
                        object.getClass().getName());
                }
            }

            @Override
            public void disconnected(Connection connection) {
                ClientUniqueId clientUniqueId = unregisterClient(connection);
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
     * Set the ServerCoordinator reference (called by GameServer).
     */
    public void setServerCoordinator(ServerCoordinator serverCoordinator) {
        this.serverCoordinator = serverCoordinator;
    }

    /**
     * Register a client's bulk connection by their unique ID.
     */
    private synchronized void registerClient(ClientUniqueId clientUniqueId, Connection connection) {
        clientIdToConnection.put(clientUniqueId, connection);

        // Update ClientProfile with bulk connection ID
        if (serverCoordinator != null) {
            // Find the profile with this clientUniqueId
            for (ClientProfile profile : serverCoordinator.clientManager.getAllClientProfiles()) {
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
    private synchronized ClientUniqueId unregisterClient(Connection connection) {
        // Find the clientUniqueId by searching the map for this connection
        ClientUniqueId clientUniqueId = null;
        for (Map.Entry<ClientUniqueId, Connection> entry : clientIdToConnection.entrySet()) {
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
        if (serverCoordinator != null) {
            for (ClientProfile profile : serverCoordinator.clientManager.getAllClientProfiles()) {
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
    public synchronized Connection getConnectionByClientId(ClientUniqueId clientUniqueId) {
        return clientIdToConnection.get(clientUniqueId);
    }

    /**
     * Check if a connection is a bulk transfer connection.
     * Used to prevent bulk connections from creating duplicate ClientProfiles.
     * @param connection the connection to check
     * @return true if this is a bulk connection, false if it's a gameplay connection
     */
    public synchronized boolean isBulkConnection(Connection connection) {
        if (connection == null) {
            return false;
        }

        // Check if this connection is in our bulk connection tracking
        int connectionId = connection.getID();
        for (Connection bulkConn : clientIdToConnection.values()) {
            if (bulkConn != null && bulkConn.getID() == connectionId) {
                return true;
            }
        }

        return false;
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
