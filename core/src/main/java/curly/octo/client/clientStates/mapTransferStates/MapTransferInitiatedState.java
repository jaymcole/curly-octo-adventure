package curly.octo.client.clientStates.mapTransferStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameWorld;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferBeginMessage;

public class MapTransferInitiatedState extends BaseGameStateClient {

    public MapTransferBeginMessage message;

    public MapTransferInitiatedState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferInitiatedState.class.getSimpleName());

        ClientGameWorld clientWorld = StateManager.getClientGameWorld();
        String incomingMapId = (message != null) ? message.mapId : null;
        String currentMapId = (clientWorld != null && clientWorld.getMapManager() != null)
            ? clientWorld.getMapManager().getMapId() : null;

        if (incomingMapId != null && incomingMapId.equals(currentMapId)) {
            Log.info("MapTransferInitiatedState", "Already have map " + incomingMapId + " - skipping transfer entirely");
            Log.info("MapTransferInitiatedState", "Transitioning directly to complete state");
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

            // Transition to disposal state first to clean up old map on OpenGL thread
            StateManager.setCurrentState(MapTransferDisposeState.class);
        }
    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
