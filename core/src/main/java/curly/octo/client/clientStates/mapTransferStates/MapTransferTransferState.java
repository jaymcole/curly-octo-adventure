package curly.octo.client.clientStates.mapTransferStates;

import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.network.messages.legacyMessages.MapChunkMessage;

import static curly.octo.client.clientStates.mapTransferStates.MapTransferSharedStatics.chunks;

public class MapTransferTransferState extends BaseGameStateClient {

    public MapTransferTransferState(BaseScreen screen) {
        super(screen);
    }
    public void handleMapChunk(MapChunkMessage message) {

        // Initialize on first chunk
        if (chunks == null) {
            MapTransferSharedStatics.setTotalChunks(message.totalChunks);
            chunks = new byte[MapTransferSharedStatics.getTotalChunks()][];
        }

        // Only process if this chunk hasn't been received yet (avoid counting duplicates)
        if (chunks[message.chunkIndex] == null) {
            // Store the chunk
            chunks[message.chunkIndex] = message.chunkData;
            MapTransferSharedStatics.setChunksReceived(MapTransferSharedStatics.getChunksReceived() + 1);

            if (MapTransferSharedStatics.getChunksReceived() == MapTransferSharedStatics.getTotalChunks()) {
                StateManager.setCurrentState(MapTransferReassemblyState.class);
            }
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
