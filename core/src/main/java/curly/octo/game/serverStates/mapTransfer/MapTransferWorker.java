package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.network.GameServer;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.legacyMessages.MapChunkMessage;
import curly.octo.network.messages.legacyMessages.MapTransferCompleteMessage;
import curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage;

/**
 * Handles map transfer for a single client.
 * Manages chunked transfer with rate limiting to prevent network buffer overflow.
 */
public class MapTransferWorker {
    private final Connection connection;
    private final GameServer gameServer;
    private final byte[] mapData;
    private final String mapId;
    private final int totalChunks;

    private int currentChunkIndex = 0;
    private boolean transferComplete = false;
    private int chunksSentThisFrame = 0;
    private static final int MAX_CHUNKS_PER_FRAME = 5; // Rate limiting

    public MapTransferWorker(Connection connection, GameServer gameServer, byte[] mapData) {
        this.connection = connection;
        this.gameServer = gameServer;
        this.mapData = mapData;
        this.mapId = "map_" + System.currentTimeMillis() + "_" + connection.getID();
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

        chunksSentThisFrame = 0;

        // Send chunks with rate limiting
        while (currentChunkIndex < totalChunks && chunksSentThisFrame < MAX_CHUNKS_PER_FRAME) {
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
        MapTransferCompleteMessage completeMsg = new MapTransferCompleteMessage(mapId);
        gameServer.getServer().sendToTCP(connection.getID(), completeMsg);
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
