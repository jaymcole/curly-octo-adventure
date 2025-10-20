package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.game.HostGameWorld;
import curly.octo.game.serverObjects.ClientProfile;
import curly.octo.network.GameServer;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.legacyMessages.MapChunkMessage;
import curly.octo.network.messages.mapTransferMessages.MapTransferAllClientProgressMessage;
import curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage;

/**
 * Handles map transfer for a single client.
 * Manages chunked transfer with rate limiting to prevent network buffer overflow.
 * Uses dedicated bulk transfer connection with large buffers (64KB).
 */
public class MapTransferWorker {
    private String clientUniqueId;  // Client unique ID to match across gameplay and bulk servers (may be null initially)
    private final int gameplayConnectionId;  // For logging and profile lookup
    private final GameServer gameServer;
    private final HostGameWorld hostGameWorld;
    private final byte[] mapData;
    private final String mapId;
    private final int totalChunks;

    public int currentChunkIndex = 0;
    private boolean transferComplete = false;
    private boolean hasLoggedMissingId = false;  // Track if we've logged the missing ID warning
    private boolean hasStarted = false;  // Track if start() has been successfully called

    // AGGRESSIVE settings for dedicated bulk transfer connection (64KB buffers, no gameplay interference)
    private static final int MAX_CHUNKS_PER_FRAME = 500; // Max chunks per update (10x original)
    // Buffer threshold: 88% of 64KB bulk transfer buffer
    // Much higher than gameplay connection since this is dedicated to map transfer
    private static final int MAX_BUFFER_THRESHOLD = 57344; // ~56KB (88% of 64KB, leaves 8KB safety margin)

    public MapTransferWorker(Connection gameplayConnection, GameServer gameServer, HostGameWorld hostGameWorld, byte[] mapData, String mapId) {
        this.gameplayConnectionId = gameplayConnection.getID();  // For logging
        this.gameServer = gameServer;
        this.hostGameWorld = hostGameWorld;
        this.mapData = mapData;
        this.mapId = mapId;
        this.totalChunks = (int) Math.ceil((double) mapData.length / Constants.NETWORK_CHUNK_SIZE);

        // Try to get clientUniqueId from gameplay connection's profile
        // May be null initially if client hasn't sent identification yet - will retry in update()
        String clientKey = gameServer.constructClientProfileKey(gameplayConnection);
        ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
        this.clientUniqueId = profile != null ? profile.clientUniqueId : null;

        if (clientUniqueId == null) {
            Log.info("MapTransferWorker", "Client unique ID not yet available for connection " + gameplayConnectionId +
                " - will retry when identification received");
        }
    }

    public void start() {
        if (clientUniqueId == null) {
            Log.warn("MapTransferWorker", "Cannot start transfer yet - client has no unique ID (will retry in update)");
            return;
        }

        if (hasStarted) {
            Log.debug("MapTransferWorker", "Transfer already started for client " + clientUniqueId);
            return;
        }

        // Send begin message via GAMEPLAY connection to trigger client to connect bulk transfer
        // Client will receive this, enter MapTransferInitiatedState, and connect bulk channel
        // After that, we can send chunks via the bulk connection in update()
        Connection gameplayConn = null;
        Connection[] gameplayConns = gameServer.getServer().getConnections().toArray(new Connection[0]);
        for (Connection c : gameplayConns) {
            if (c.getID() == gameplayConnectionId) {
                gameplayConn = c;
                break;
            }
        }

        if (gameplayConn == null) {
            Log.error("MapTransferWorker", "Cannot start transfer - gameplay connection not found: " +
                gameplayConnectionId);
            return;
        }

        MapTransferBeginMessage beginMsg = new MapTransferBeginMessage(mapId, totalChunks, mapData.length);
        gameplayConn.sendTCP(beginMsg);  // Send via GAMEPLAY connection
        Log.info("MapTransferWorker", "Sent MapTransferBeginMessage to client " + clientUniqueId +
                " (" + totalChunks + " chunks, " + mapData.length + " bytes) via gameplay connection");
        Log.info("MapTransferWorker", "Waiting for client to connect bulk transfer channel...");
        hasStarted = true;  // Mark as started
    }

    public void update(float delta) {
        if (transferComplete) return;

        // Retry getting clientUniqueId if it was null initially (race condition with identification)
        if (clientUniqueId == null) {
            Connection[] gameplayConns = gameServer.getServer().getConnections().toArray(new Connection[0]);
            for (Connection c : gameplayConns) {
                if (c.getID() == gameplayConnectionId) {
                    String clientKey = gameServer.constructClientProfileKey(c);
                    ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
                    if (profile != null && profile.clientUniqueId != null) {
                        this.clientUniqueId = profile.clientUniqueId;
                        Log.info("MapTransferWorker", "Successfully retrieved client unique ID: " + clientUniqueId);
                        break;
                    }
                }
            }

            // If still null, log warning and return (will retry next frame)
            if (clientUniqueId == null) {
                if (!hasLoggedMissingId) {
                    Log.warn("MapTransferWorker", "Waiting for client " + gameplayConnectionId + " to send identification...");
                    hasLoggedMissingId = true;
                }
                return;
            }

            // Successfully retrieved clientUniqueId - now call start() to send MapTransferBeginMessage
            start();
        }

        // Get bulk transfer connection (REQUIRED for chunk transfer - chunks too large for gameplay buffer)
        Connection bulkConn = gameServer.getBulkServer().getConnectionByClientId(clientUniqueId);

        if (bulkConn == null) {
            // Bulk connection not yet established - wait for client to connect
            // Only log occasionally to avoid spam
            if (System.currentTimeMillis() % 1000 < 100) {
                Log.warn("MapTransferWorker", "Waiting for bulk connection from client " + clientUniqueId);
            }
            return;
        }

        // Get gameplay connection for state checks
        Connection gameplayConn = null;
        Connection[] gameplayConns = gameServer.getServer().getConnections().toArray(new Connection[0]);
        for (Connection c : gameplayConns) {
            if (c.getID() == gameplayConnectionId) {
                gameplayConn = c;
                break;
            }
        }

        if (gameplayConn == null) {
            Log.error("MapTransferWorker", "Gameplay connection lost for client " + clientUniqueId);
            return;
        }

        // Check client's current state via gameplay connection - only send chunks if they're in the transfer state
        if (gameplayConn != null) {
            String clientKey = gameServer.constructClientProfileKey(gameplayConn);
            ClientProfile profile = hostGameWorld.getClientProfile(clientKey);

            if (profile != null && profile.currentState != null) {
                // If client completed the transfer, mark this worker as complete
                if (profile.currentState.equals("MapTransferCompleteState")) {
                    Log.info("MapTransferWorker", "Client " + clientUniqueId + " completed transfer");
                    complete();
                    return;
                }

                // Only send chunks when client is in the transfer state
                if (!profile.currentState.equals("MapTransferTransferState")) {
                    return;
                }
            }
        }

        int chunksSentThisFrame = 0;

        // Send chunks with rate limiting and buffer monitoring on BULK connection
        while (currentChunkIndex < totalChunks && chunksSentThisFrame < MAX_CHUNKS_PER_FRAME) {
            // Check bulk connection TCP write buffer
            int pendingBytes = bulkConn.getTcpWriteBufferSize();
            if (pendingBytes >= MAX_BUFFER_THRESHOLD) {
                if (chunksSentThisFrame == 0 && currentChunkIndex % 20 == 0) {
                    Log.debug("MapTransferWorker", "Bulk buffer full (" + pendingBytes + " bytes), waiting");
                }
                break;
            }

            sendChunk(bulkConn, currentChunkIndex);
            currentChunkIndex++;
            chunksSentThisFrame++;
        }

        // Check if complete
        if (currentChunkIndex >= totalChunks && !transferComplete) {
            complete();
        }
    }

    private void sendChunk(Connection bulkConn, int chunkIndex) {
        int offset = chunkIndex * Constants.NETWORK_CHUNK_SIZE;
        int chunkLength = Math.min(Constants.NETWORK_CHUNK_SIZE, mapData.length - offset);

        byte[] chunkData = new byte[chunkLength];
        System.arraycopy(mapData, offset, chunkData, 0, chunkLength);

        MapChunkMessage chunkMsg = new MapChunkMessage(mapId, chunkIndex, totalChunks, chunkData);
        bulkConn.sendTCP(chunkMsg);  // Send via BULK connection (required - too large for gameplay buffer)

        if (chunkIndex % 50 == 0) {  // Log less frequently since transfer is much faster
            Log.debug("MapTransferWorker", "Sent chunk " + chunkIndex + "/" + totalChunks +
                    " to client " + clientUniqueId + " via bulk connection");
        }
    }

    private void complete() {
        transferComplete = true;
        Log.info("MapTransferWorker", "Transfer complete to client " + clientUniqueId);
    }

    public boolean isComplete() {
        return transferComplete;
    }

    public String getClientUniqueId() {
        return clientUniqueId;
    }

    public int getGameplayConnectionId() {
        return gameplayConnectionId;
    }
}
