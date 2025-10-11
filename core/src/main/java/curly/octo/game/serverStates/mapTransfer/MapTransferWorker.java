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
 */
public class MapTransferWorker {
    private final Connection connection;
    private final GameServer gameServer;
    private final HostGameWorld hostGameWorld;
    private final byte[] mapData;
    private final String mapId;
    private final int totalChunks;

    public int currentChunkIndex = 0;
    private boolean transferComplete = false;
    private int chunksSentThisFrame = 0;
    private static final int MAX_CHUNKS_PER_FRAME = 5; // Rate limiting
    private static final int MAX_BUFFER_THRESHOLD = 16384; // Only send if TCP buffer < 16KB

    public MapTransferWorker(Connection connection, GameServer gameServer, HostGameWorld hostGameWorld, byte[] mapData, String mapId) {
        this.connection = connection;
        this.gameServer = gameServer;
        this.hostGameWorld = hostGameWorld;
        this.mapData = mapData;
        this.mapId = mapId;
        this.totalChunks = (int) Math.ceil((double) mapData.length / Constants.NETWORK_CHUNK_SIZE);
    }

    public void start() {
        // Send begin message to this specific client
        MapTransferBeginMessage beginMsg = new MapTransferBeginMessage(mapId, totalChunks, mapData.length);
        NetworkManager.sendToClient(connection.getID(), beginMsg);
        Log.info("MapTransferWorker", "Started transfer to client " + connection.getID() +
                " (" + totalChunks + " chunks, " + mapData.length + " bytes)");
    }

    public void update(float delta) {
        if (transferComplete) return;

        // Check client's current state - only send chunks if they're in the transfer state
        String clientKey = gameServer.constructClientProfileKey(connection);
        ClientProfile profile = hostGameWorld.getClientProfile(clientKey);

        if (profile != null && profile.currentState != null) {
            // If client completed the transfer, mark this worker as complete
            if (profile.currentState.equals("MapTransferCompleteState")) {
                Log.info("MapTransferWorker", "Client " + connection.getID() + " completed transfer (entered MapTransferCompleteState)");
                complete();
                return;
            }

            // Only send chunks when client is in the transfer state
            if (!profile.currentState.equals("MapTransferTransferState")) {
                // Client is in another state (Initiated, Reassembly, etc.) - wait
                return;
            }
        }

        chunksSentThisFrame = 0;

        // Send chunks with rate limiting and buffer monitoring
        while (currentChunkIndex < totalChunks && chunksSentThisFrame < MAX_CHUNKS_PER_FRAME) {
            // Check if TCP write buffer has room for another chunk
            int pendingBytes = connection.getTcpWriteBufferSize();
            if (pendingBytes >= MAX_BUFFER_THRESHOLD) {
                // Buffer is too full, wait for it to drain
                if (chunksSentThisFrame == 0 && currentChunkIndex % 20 == 0) {
                    Log.debug("MapTransferWorker", "Buffer full (" + pendingBytes + " bytes), waiting for drain");
                }
                break;
            }
            // Progress is now broadcast to all clients by ServerMapTransferState
            sendChunk(currentChunkIndex);
            currentChunkIndex++;
            chunksSentThisFrame++;
        }

        // Check if complete
        if (currentChunkIndex >= totalChunks && !transferComplete) {
            complete();
        }
    }

    private void sendChunk(int chunkIndex) {
        int offset = chunkIndex * Constants.NETWORK_CHUNK_SIZE;
        int chunkLength = Math.min(Constants.NETWORK_CHUNK_SIZE, mapData.length - offset);

        byte[] chunkData = new byte[chunkLength];
        System.arraycopy(mapData, offset, chunkData, 0, chunkLength);

        MapChunkMessage chunkMsg = new MapChunkMessage(mapId, chunkIndex, totalChunks, chunkData);
        gameServer.getServer().sendToTCP(connection.getID(), chunkMsg);

        if (chunkIndex % 10 == 0) {
            Log.debug("MapTransferWorker", "Sent chunk " + chunkIndex + "/" + totalChunks +
                    " to client " + connection.getID());
        }
    }

    private void complete() {
        transferComplete = true;
        Log.info("MapTransferWorker", "Transfer complete to client " + connection.getID());
    }

    public boolean isComplete() {
        return transferComplete;
    }

    public int getConnectionId() {
        return connection.getID();
    }
}
