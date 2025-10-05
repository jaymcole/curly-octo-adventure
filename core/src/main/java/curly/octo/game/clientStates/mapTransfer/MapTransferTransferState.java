package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.network.messages.legacyMessages.MapChunkMessage;

import static curly.octo.game.clientStates.mapTransfer.MapTransferSharedStatics.chunks;

public class MapTransferTransferState extends BaseGameStateClient {

    public MapTransferTransferState(BaseScreen screen) {
        super(screen);
    }
    public void handleMapChunk(MapChunkMessage message) {

        // Initialize on first chunk
        if (chunks == null) {
            MapTransferSharedStatics.setTotalChunks(message.totalChunks);
            chunks = new byte[MapTransferSharedStatics.getTotalChunks()][];
            Log.info("MapTransferTransferState", "Initialized chunk storage for " + MapTransferSharedStatics.getTotalChunks() + " chunks");
        }

        // Store the chunk
        chunks[message.chunkIndex] = message.chunkData;
        MapTransferSharedStatics.setChunksReceived(MapTransferSharedStatics.getChunksReceived() + 1);

        Log.info("MapTransferTransferState", "Received chunk " + message.chunkIndex + "/" + MapTransferSharedStatics.getTotalChunks() +
                " (" + MapTransferSharedStatics.getChunksReceived() + " total received, " + (int)((float)MapTransferSharedStatics.getChunksReceived() / MapTransferSharedStatics.getTotalChunks() * 100) + "%)");

        if (MapTransferSharedStatics.getChunksReceived() == MapTransferSharedStatics.getTotalChunks()) {
            Log.info("MapTransferTransferState", "All chunks received, transitioning to reassembly state");
            StateManager.setCurrentState(MapTransferReassemblyState.class);
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
