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

        // OPTIMIZATION: Check if we already have this map (e.g., player joining existing session)
        // If we do, skip the entire transfer process and jump to ready state
        ClientGameWorld clientWorld = StateManager.getClientGameWorld();
        String incomingMapId = (message != null) ? message.mapId : null;
        String currentMapId = (clientWorld != null && clientWorld.getMapManager() != null)
            ? clientWorld.getMapManager().getMapId() : null;

        if (incomingMapId != null && incomingMapId.equals(currentMapId)) {
            Log.info("MapTransferInitiatedState", "Already have map " + incomingMapId + " - skipping transfer entirely");
            Log.info("MapTransferInitiatedState", "Transitioning directly to ready state");
            StateManager.setCurrentState(MapTransferCompleteState.class);
            return;
        }

        // Different map or no existing map - proceed with normal transfer flow
        Log.info("MapTransferInitiatedState", "Map IDs differ (incoming: " + incomingMapId +
            ", current: " + currentMapId + ") - performing full transfer");

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
