package curly.octo.client;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.network.KryoNetwork;
import curly.octo.server.NetworkListener;

import java.io.IOException;

/**
 * Dedicated KryoNet client for bulk data transfers (map transfers).
 * Uses large buffers (64KB) for maximum throughput.
 * Created on-demand during map transfers, disconnected after completion.
 */
public class BulkTransferClient {
    private final String host;
    private Client client;
    private final NetworkListener networkListener;
    private boolean connected = false;

    public BulkTransferClient(String host) {
        this.host = host;

        // Create client with large buffers for bulk transfers
        this.client = new Client(
            Constants.BULK_TRANSFER_BUFFER_SIZE,
            Constants.BULK_TRANSFER_BUFFER_SIZE
        );

        // Register network classes
        KryoNetwork.register(client);

        // Create listener (no server callbacks needed for client)
        this.networkListener = new NetworkListener(null);
        client.addListener(networkListener);

        Log.info("BulkTransferClient", "Initialized with " +
            Constants.BULK_TRANSFER_BUFFER_SIZE + "B buffers");
    }

    /**
     * Connect to the bulk transfer server.
     * Blocks until connection established or timeout.
     */
    public void connect() throws IOException {
        if (connected) {
            Log.warn("BulkTransferClient", "Already connected");
            return;
        }

        try {
            client.start();
            Log.info("BulkTransferClient", "Client started, connecting to " + host + ":" +
                Constants.BULK_TRANSFER_TCP_PORT + "/" + Constants.BULK_TRANSFER_UDP_PORT);

            client.connect(5000, host,
                Constants.BULK_TRANSFER_TCP_PORT,
                Constants.BULK_TRANSFER_UDP_PORT);

            connected = true;
            Log.info("BulkTransferClient", "Successfully connected to bulk transfer server");
        } catch (IOException e) {
            Log.error("BulkTransferClient", "Failed to connect: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Disconnect from the bulk transfer server and clean up resources.
     */
    public void disconnect() {
        if (!connected) {
            return;
        }

        Log.info("BulkTransferClient", "Disconnecting from bulk transfer server");

        if (client != null) {
            client.stop();
            connected = false;
        }

        Log.info("BulkTransferClient", "Disconnected");
    }

    /**
     * Update the client. Must be called regularly while connected.
     */
    public void update() throws IOException {
        if (client != null && connected) {
            client.update(0);
        }
    }

    /**
     * Get the underlying KryoNet client for direct message handling.
     */
    public Client getClient() {
        return client;
    }

    /**
     * Get the connection object (returns null - client doesn't track its own connection).
     * Use getClient() instead to access the Client directly for sending messages.
     */
    public Connection getConnection() {
        // KryoNet Client doesn't expose getConnections() - it has only one connection
        // The connection is internal and managed by the client itself
        return null;
    }

    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        disconnect();
        if (client != null) {
            try {
                client.dispose();
            } catch (Exception e) {
                Log.error("BulkTransferClient", "Error disposing client: " + e.getMessage());
            }
            client = null;
        }
    }
}
