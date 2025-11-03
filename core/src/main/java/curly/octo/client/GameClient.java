package curly.octo.client;

import curly.octo.Main;
import curly.octo.client.clientStates.mapTransferStates.MapTransferBuildAssetsState;
import curly.octo.client.clientStates.mapTransferStates.MapTransferReassemblyState;
import curly.octo.client.clientStates.mapTransferStates.MapTransferSharedStatics;
import curly.octo.client.clientStates.mapTransferStates.MapTransferTransferState;
import curly.octo.common.Constants;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.MapTransferInitiatedState;
import curly.octo.client.clientStates.playingStates.ClientPlayingState;
import curly.octo.common.network.KryoNetwork;
import curly.octo.server.NetworkListener;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.ClientIdentificationMessage;
import curly.octo.common.network.messages.legacyMessages.MapChunkMessage;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferAllClientProgressMessage;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferBeginMessage;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferCompleteMessage;

import java.io.IOException;
import java.util.UUID;

/**
 * Handles client-side network operations.
 */
public class GameClient {
    private final Client client;  // Gameplay connection (small buffers)
    private BulkTransferClient bulkClient;  // Map transfer connection (large buffers, on-demand)
    private final NetworkListener networkListener;
    private final String host;

    /**
     * Creates a new game client that will connect to the specified host.
     * Uses dual connections: gameplay (small buffers) and bulk transfer (large buffers).
     * @param host the server hostname or IP address to connect to
     */
    public GameClient(String host) {
        this.host = host;
        // Small buffers for gameplay connection (low latency for position updates)
        this.client = new Client(Constants.GAMEPLAY_BUFFER_SIZE, Constants.GAMEPLAY_BUFFER_SIZE); // 8KB read/write buffers

        // Register all network classes
        KryoNetwork.register(client);

        // Initialize the new NetworkManager with gameplay connection
        NetworkManager.initialize(client);

        // Create network listener with client reference
        networkListener = new NetworkListener(null); // Client doesn't need server callbacks

        // Gameplay messages handled on gameplay connection (handled by ClientGameMode via NetworkManager)

        // Map transfer messages on GAMEPLAY connection (state management + fallback chunk delivery):
        NetworkManager.onReceive(MapTransferBeginMessage.class, this::handleMapTransferBegin);  // Triggers bulk connection
        NetworkManager.onReceive(MapChunkMessage.class, this::handleMapChunk);  // Chunks (bulk preferred, gameplay fallback)
        NetworkManager.onReceive(MapTransferCompleteMessage.class, this::handleMapTransferComplete);  // Releases client to play
        NetworkManager.onReceive(MapTransferAllClientProgressMessage.class, this::handleMapTransferAllClientProgressMessage);  // Progress updates

        // NOTE: Bulk connection is used for SENDING chunks (server->client) for performance
        // but all MESSAGE HANDLING stays on gameplay connection for simplicity and reliability

        // Add network listener
        client.addListener(networkListener);
        this.bulkClient = null;  // Created on-demand

        Log.info("GameClient", "Initialized with dual-connection architecture (gameplay: 8KB, bulk: on-demand 64KB)");
    }

    private boolean connecting = false;
    private long connectionStartTime = 0;
    private int connectionTimeout = 5000;

    /**
     * Starts the connection process to the server.
     * Call update() repeatedly until isConnected() returns true.
     * @param timeout the connection timeout in milliseconds
     * @throws IOException if the connection fails to start
     */
    public void connect(int timeout) throws IOException {
        this.connectionTimeout = timeout;
        client.start();

        // Use non-blocking connection
        client.connect(5000, host, KryoNetwork.TCP_PORT, KryoNetwork.UDP_PORT);
        connecting = true;
        connectionStartTime = System.currentTimeMillis();

        Log.info("Client", "Starting connection to server at " + host + ":" + KryoNetwork.TCP_PORT);
    }

    /**
     * Updates the client connection state. Must be called regularly during connection.
     * @return true if connection is complete (success or failure)
     */
    public boolean updateConnection() throws IOException {
        if (!connecting) {
            return true; // Not connecting or already connected
        }

        // Update the client to process connection
        client.update(0);

        // Check if connected
        if (client.isConnected()) {
            connecting = false;
            Log.info("Client", "Successfully connected to server at " + host + ":" + KryoNetwork.TCP_PORT);
            // Immediately send client identification
            sendClientIdentification();
            return true;
        }

        // Check for timeout
        if (System.currentTimeMillis() - connectionStartTime > connectionTimeout) {
            connecting = false;
            Log.error("Client", "Connection timeout after " + connectionTimeout + "ms");
            return true;
        }

        return false; // Still connecting
    }

    /**
     * @return true if currently in the process of connecting
     */
    public boolean isConnecting() {
        return connecting;
    }

    /**
     * @return true if connected to the server
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * Disconnects from the server and releases all resources.
     */
    public void disconnect() {
        if (client != null) {
            try {
                // Close connection immediately without waiting for graceful shutdown
                client.close();
                Log.info("Client", "Disconnected from server (immediate)");
            } catch (Exception e) {
                Log.warn("Client", "Exception during immediate disconnect: " + e.getMessage());
            }

            // Then stop the client in a separate thread to avoid blocking
            Thread stopThread = new Thread(() -> {
                try {
                    client.stop();
                    Log.info("Client", "Client stopped");
                } catch (Exception e) {
                    Log.warn("Client", "Exception during client stop: " + e.getMessage());
                }
            }, "ClientStopThread");
            stopThread.setDaemon(true); // Don't keep JVM alive
            stopThread.start();
        }
    }

    /**
     * Updates the client. Must be called regularly.
     * Updates both gameplay and bulk transfer connections (if active).
     */
    public void update() throws IOException {
        // Update gameplay connection
        if (client != null) {
            long startTime = System.nanoTime();

            try {
                client.update(0);
                long updateTime = (System.nanoTime() - startTime) / 1_000_000;

                if (updateTime > 50) {
                    Log.warn("GameClient", "Gameplay client.update(0) took " + updateTime + "ms");
                    if (client.isConnected()) {
                        int rtt = client.getReturnTripTime();
                        Log.warn("GameClient", "Connection RTT: " + rtt + "ms");
                    }
                }
            } catch (Exception e) {
                long errorTime = (System.nanoTime() - startTime) / 1_000_000;
                Log.error("GameClient", "Gameplay client.update() failed after " + errorTime + "ms: " + e.getMessage());
                throw e;
            }
        }

        // Update bulk transfer connection if active
        if (bulkClient != null && bulkClient.isConnected()) {
            try {
                bulkClient.update();
            } catch (Exception e) {
                Log.error("GameClient", "Bulk transfer client.update() failed: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Connect the bulk transfer channel for map transfers.
     * Call before starting a map transfer.
     */
    public void connectBulkTransfer() throws IOException {
        if (bulkClient != null && bulkClient.isConnected()) {
            Log.warn("GameClient", "Bulk transfer already connected");
            return;
        }

        Log.info("GameClient", "Connecting bulk transfer channel...");
        bulkClient = new BulkTransferClient(host);
        bulkClient.connect();

        Log.info("GameClient", "Bulk client connected: " + bulkClient.isConnected());
        Log.info("GameClient", "Preparing to send ClientIdentificationMessage with ID: " + Main.clientUniqueId);

        // Send client identification on bulk connection (same UUID as gameplay connection)
        ClientIdentificationMessage identMsg = new ClientIdentificationMessage(Main.clientUniqueId, "Jay");
        bulkClient.getClient().sendTCP(identMsg);
        Log.info("GameClient", "Sent ClientIdentificationMessage on bulk connection: " + Main.clientUniqueId);

        // NOTE: Bulk connection is SEND-ONLY (server -> client for chunks)
        // All message handlers remain on gameplay connection for simplicity
        // No listeners needed on bulk connection

        Log.info("GameClient", "Bulk transfer channel connected and identified (send-only mode)");
    }

    /**
     * Disconnect the bulk transfer channel after map transfer completes.
     */
    public void disconnectBulkTransfer() {
        if (bulkClient == null) {
            return;
        }

        Log.info("GameClient", "Disconnecting bulk transfer channel...");
        bulkClient.disconnect();
        bulkClient.dispose();
        bulkClient = null;
        Log.info("GameClient", "Bulk transfer channel disconnected");
    }

    /**
     * Get the bulk transfer client (for map transfer messages).
     * @return bulk client or null if not connected
     */
    public BulkTransferClient getBulkClient() {
        return bulkClient;
    }

    /**
     * Sends a TCP message to the server.
     * @param message the message to send
     */
    public void sendTCP(Object message) {
        if (client != null && client.isConnected()) {
            client.sendTCP(message);
        }
    }

    /**
     * Sends a UDP message to the server.
     * @param message the message to send
     */
    public void sendUDP(Object message) {
        client.sendUDP(message);
    }



    public void sendClientIdentification() {
        if (client != null && client.isConnected()) {
            ClientIdentificationMessage message =
                new ClientIdentificationMessage(Main.clientUniqueId, Main.clientPreferredName);
            client.sendTCP(message);
            Log.info("GameClient", "Sent client identification: " + Main.clientUniqueId + " (" + Main.clientPreferredName + ")");
        } else {
            Log.warn("GameClient", "Cannot send identification - client not connected");
        }
    }

    /**
     * @return the underlying KryoNet client instance
     */
    public Client getClient() {
        return client;
    }

    private void handleMapTransferBegin(MapTransferBeginMessage message) {
        // State-aware message filtering: ignore if already in an active transfer state
        BaseGameStateClient currentState = StateManager.getCurrentState();
        if (currentState != null) {
            // If we're actively transferring, ignore new transfer messages
            // This prevents disruption when another client joins mid-transfer
            if (currentState instanceof MapTransferInitiatedState ||
                currentState instanceof MapTransferTransferState ||
                currentState instanceof MapTransferReassemblyState ||
                currentState instanceof MapTransferBuildAssetsState) {
                Log.info("GameClient", "Ignoring MapTransferBeginMessage - already in transfer state: " +
                    currentState.getClass().getSimpleName());
                return;
            }
        }

        // Accept the message and transition to transfer state
        MapTransferInitiatedState state = (MapTransferInitiatedState) StateManager.getCachedState(MapTransferInitiatedState.class);
        state.message = message;
        StateManager.setCurrentState(MapTransferInitiatedState.class);
    }

    private void handleMapChunk(MapChunkMessage message) {
        MapTransferTransferState state = (MapTransferTransferState) StateManager.getCachedState(MapTransferTransferState.class);
        state.handleMapChunk(message);
    }

    private void handleMapTransferComplete(MapTransferCompleteMessage message) {
        StateManager.setCurrentState(ClientPlayingState.class);
    }

    private void handleMapTransferAllClientProgressMessage(MapTransferAllClientProgressMessage message) {
        MapTransferSharedStatics.updateAllClientProgress(message.clientToChunkProgress);
    }

}
