package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.ClientGameWorld;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage;

public class MapTransferInitiatedState extends BaseGameStateClient {

    public MapTransferBeginMessage message;

    public MapTransferInitiatedState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferInitiatedState.class.getSimpleName());

        // CRITICAL: Pause position updates to prevent Kryo corruption during transfer
        curly.octo.game.ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null) {
            clientGameMode.pauseNetworkUpdates();
            clientGameMode.disableInput();
            Log.info("MapTransferInitiatedState", "Paused network updates and input for map transfer");
        }

        // CRITICAL: Dispose old map assets BEFORE starting new map transfer
        // This prevents crashes from physics/rendering conflicts between old and new maps
        // BUT: Only if we actually have an existing map (not first transfer)
        ClientGameWorld clientWorld = StateManager.getClientGameWorld();
        if (clientWorld != null && clientWorld.getMapManager() != null) {
            Log.info("MapTransferInitiatedState", "Disposing old map assets before transfer");
            clientWorld.cleanupForMapRegeneration();
            Log.info("MapTransferInitiatedState", "Old map assets disposed successfully");
        } else {
            Log.info("MapTransferInitiatedState", "No existing map to clean up (first transfer)");
        }

        if (message != null) {
            MapTransferSharedStatics.resetProgressVariables();
            Log.info("MapTransferInitiatedState", "Map transfer initiated: " + message.mapId +
                " (" + message.totalChunks + " chunks, " + message.totalSize + " bytes)");
            MapTransferSharedStatics.setMapId(message.mapId);
            MapTransferSharedStatics.setTotalChunks(message.totalChunks);
            MapTransferSharedStatics.setTotalSize(message.totalSize);
            StateManager.setCurrentState(MapTransferTransferState.class);
        }
    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
