package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
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
