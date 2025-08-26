package curly.octo.network;

import com.badlogic.gdx.math.Quaternion;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.messages.*;
import curly.octo.network.messages.MapReceivedListener;

import java.io.IOException;

/**
 * Handles client-side network operations.
 */
public class GameClient {
    private final Client client;
    private final NetworkListener networkListener;
    private final String host;
    private MapReceivedListener mapReceivedListener;
    private PlayerAssignmentListener playerAssignmentListener;
    private PlayerRosterListener playerRosterListener;
    private PlayerUpdateListener playerUpdateListener;
    private PlayerDisconnectListener playerDisconnectListener;

    /**
     * Creates a new game client that will connect to the specified host.
     * @param host the server hostname or IP address to connect to
     */
    public GameClient(String host) {
        this.host = host;
        // Optimized buffer sizes - large enough for map transfers but not wasteful
        // 5MB write buffer (for map uploads), 1MB read buffer (for regular gameplay)
        this.client = new Client(1000000, 5000000);

        // Register all network classes
        Network.register(client);

        // Create network listener with client reference
        networkListener = new NetworkListener(client);

        networkListener.setPlayerAssignmentListener(playerAssignment -> {
            if (this.playerAssignmentListener != null) {
                this.playerAssignmentListener.onPlayerAssignmentReceived(playerAssignment);
            }
        });

        networkListener.setPlayerRosterListener(playerRoster -> {
            if (this.playerRosterListener != null) {
                this.playerRosterListener.onPlayerRosterReceived(playerRoster);
            }
        });

        networkListener.setPlayerUpdateListener(playerUpdate -> {
            if (this.playerUpdateListener != null) {
                this.playerUpdateListener.onPlayerUpdateReceived(playerUpdate);
            }
        });

        networkListener.setMapReceivedListener(map -> {
            if (this.mapReceivedListener != null) {
                this.mapReceivedListener.onMapReceived(map);
            }
        });

        networkListener.setPlayerDisconnectListener(playerDisconnect -> {
            if (this.playerDisconnectListener != null) {
                this.playerDisconnectListener.onPlayerDisconnected(playerDisconnect);
            }
        });

        // Add network listener
        client.addListener(networkListener);
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
        client.connect(5000, host, Network.TCP_PORT, Network.UDP_PORT);
        connecting = true;
        connectionStartTime = System.currentTimeMillis();

        Log.info("Client", "Starting connection to server at " + host + ":" + Network.TCP_PORT);
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
            Log.info("Client", "Successfully connected to server at " + host + ":" + Network.TCP_PORT);
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
     */
    public void update() throws IOException {
        if (client != null) {
            long startTime = System.nanoTime();
            
            // Profile the KryoNet client update call
            try {
                client.update(0);
                long updateTime = (System.nanoTime() - startTime) / 1_000_000;
                
                // Only log if it takes longer than expected (>50ms is definitely problematic)
                if (updateTime > 50) {
                    Log.warn("GameClient", "KryoNet client.update(0) took " + updateTime + "ms - this should be near-instant!");
                    
                    // Additional diagnostics
                    if (client.isConnected()) {
                        int rtt = client.getReturnTripTime();
                        Log.warn("GameClient", "Connection RTT: " + rtt + "ms, Connected: true");
                    } else {
                        Log.warn("GameClient", "Client not connected during slow update");
                    }
                }
            } catch (Exception e) {
                long errorTime = (System.nanoTime() - startTime) / 1_000_000;
                Log.error("GameClient", "KryoNet client.update() failed after " + errorTime + "ms: " + e.getMessage());
                throw e;
            }
        }
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

    /**
     * @return the underlying KryoNet client instance
     */
    public Client getClient() {
        return client;
    }

    public void setMapReceivedListener(MapReceivedListener listener) {
        this.mapReceivedListener = listener;
        this.networkListener.setMapReceivedListener(listener);
    }

    public void setPlayerAssignmentListener(PlayerAssignmentListener listener) {
        this.playerAssignmentListener = listener;
        this.networkListener.setPlayerAssignmentListener(listener);
    }

    public void setPlayerRosterListener(PlayerRosterListener listener) {
        this.playerRosterListener = listener;
        this.networkListener.setPlayerRosterListener(listener);
    }

    public void setPlayerUpdateListener(PlayerUpdateListener listener) {
        this.playerUpdateListener = listener;
        this.networkListener.setPlayerUpdateListener(listener);
    }

    public void setPlayerDisconnectListener(PlayerDisconnectListener listener) {
        this.playerDisconnectListener = listener;
        this.networkListener.setPlayerDisconnectListener(listener);
    }
}
