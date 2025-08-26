package curly.octo.network;

import com.badlogic.gdx.math.Quaternion;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.messages.*;
import curly.octo.network.messages.MapReceivedListener;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.ConcurrentHashMap;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

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
    
    // Chunked map transfer support
    private final ConcurrentHashMap<String, MapTransferState> activeTransfers = new ConcurrentHashMap<>();
    
    // Inner class to track map transfer state
    private static class MapTransferState {
        final String mapId;
        final int totalChunks;
        final long totalSize;
        final byte[][] chunks;
        int chunksReceived = 0;
        
        MapTransferState(String mapId, int totalChunks, long totalSize) {
            this.mapId = mapId;
            this.totalChunks = totalChunks;
            this.totalSize = totalSize;
            this.chunks = new byte[totalChunks][];
        }
        
        boolean isComplete() {
            return chunksReceived == totalChunks;
        }
        
        float getProgress() {
            return (float) chunksReceived / totalChunks;
        }
    }

    /**
     * Creates a new game client that will connect to the specified host.
     * @param host the server hostname or IP address to connect to
     */
    public GameClient(String host) {
        this.host = host;
        // Small buffers for fast network operations - maps will be transferred in chunks
        this.client = new Client(16384, 16384); // 16KB read/write buffers

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

        // Handle chunked map transfer messages
        networkListener.setMapTransferStartListener(this::handleMapTransferStart);
        networkListener.setMapChunkListener(this::handleMapChunk);
        networkListener.setMapTransferCompleteListener(this::handleMapTransferComplete);
        
        // Keep old map listener for backwards compatibility (though it won't be used)
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
    
    /**
     * Handles the start of a chunked map transfer
     */
    private void handleMapTransferStart(MapTransferStartMessage message) {
        Log.info("GameClient", "Starting map transfer: " + message.mapId + 
                " (" + message.totalChunks + " chunks, " + message.totalSize + " bytes)");
        
        MapTransferState state = new MapTransferState(message.mapId, message.totalChunks, message.totalSize);
        activeTransfers.put(message.mapId, state);
    }
    
    /**
     * Handles receiving a chunk of map data
     */
    private void handleMapChunk(MapChunkMessage message) {
        MapTransferState state = activeTransfers.get(message.mapId);
        if (state == null) {
            Log.warn("GameClient", "Received chunk for unknown transfer: " + message.mapId);
            return;
        }
        
        // Store the chunk
        state.chunks[message.chunkIndex] = message.chunkData;
        state.chunksReceived++;
        
        Log.info("GameClient", "Received chunk " + message.chunkIndex + "/" + message.totalChunks + 
                " for " + message.mapId + " (" + (int)(state.getProgress() * 100) + "% complete)");
    }
    
    /**
     * Handles completion of a chunked map transfer
     */
    private void handleMapTransferComplete(MapTransferCompleteMessage message) {
        MapTransferState state = activeTransfers.remove(message.mapId);
        if (state == null) {
            Log.warn("GameClient", "Received completion for unknown transfer: " + message.mapId);
            return;
        }
        
        if (!state.isComplete()) {
            Log.error("GameClient", "Transfer marked complete but missing chunks: " + 
                     state.chunksReceived + "/" + state.totalChunks);
            return;
        }
        
        Log.info("GameClient", "Map transfer complete: " + message.mapId + ", reassembling...");
        
        try {
            // Reassemble the chunks into the original map data
            int totalLength = 0;
            for (byte[] chunk : state.chunks) {
                totalLength += chunk.length;
            }
            
            byte[] completeData = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : state.chunks) {
                System.arraycopy(chunk, 0, completeData, offset, chunk.length);
                offset += chunk.length;
            }
            
            // Deserialize the map using Kryo (same as KryoNet uses)
            try (ByteArrayInputStream bais = new ByteArrayInputStream(completeData);
                 Input input = new Input(bais)) {
                
                // Get the client's Kryo instance (already configured with our registrations)
                Kryo kryo = client.getKryo();
                curly.octo.map.GameMap map = kryo.readObject(input, curly.octo.map.GameMap.class);
                
                Log.info("GameClient", "Map successfully deserialized using Kryo, notifying listener");
                
                // Notify the listener that the map is ready
                if (this.mapReceivedListener != null) {
                    this.mapReceivedListener.onMapReceived(map);
                }
            }
            
        } catch (Exception e) {
            Log.error("GameClient", "Error reassembling map from chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
