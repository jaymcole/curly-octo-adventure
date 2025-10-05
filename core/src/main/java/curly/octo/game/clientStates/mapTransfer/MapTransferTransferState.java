package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.network.messages.legacyMessages.MapChunkMessage;

public class MapTransferTransferState extends BaseGameStateClient {
    private byte[][] chunks;
    private int chunksReceived = 0;
    private int totalChunks = 0;

    public MapTransferTransferState(BaseScreen screen) {
        super(screen);
    }

    public void handleMapChunk(MapChunkMessage message) {
        // Initialize on first chunk
        if (chunks == null) {
            totalChunks = message.totalChunks;
            chunks = new byte[totalChunks][];
            Log.info("MapTransferTransferState", "Initialized chunk storage for " + totalChunks + " chunks");
        }

        // Store the chunk
        chunks[message.chunkIndex] = message.chunkData;
        chunksReceived++;

        Log.info("MapTransferTransferState", "Received chunk " + message.chunkIndex + "/" + totalChunks +
                " (" + chunksReceived + " total received, " + (int)((float)chunksReceived / totalChunks * 100) + "%)");

        // Auto-transition when complete
        if (chunksReceived == totalChunks) {
            Log.info("MapTransferTransferState", "All chunks received, transitioning to complete state");

            // Pass chunks to complete state
            MapTransferCompleteState completeState = (MapTransferCompleteState) StateManager.getCachedState(MapTransferCompleteState.class);
            completeState.setChunksForReassembly(chunks);

            // Reset for next transfer
            chunks = null;
            chunksReceived = 0;
            totalChunks = 0;

            StateManager.setCurrentState(MapTransferCompleteState.class);
        }
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferTransferState.class.getSimpleName());
    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
