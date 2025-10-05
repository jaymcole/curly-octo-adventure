package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.legacyMessages.MapTransferCompleteMessage;

public class MapTransferCompleteState extends BaseGameStateClient {

    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());
        try {
            Log.info("MapTransferCompleteState", "Signaling server that client is ready");
            MapTransferCompleteMessage message = new MapTransferCompleteMessage();
            message.mapId = MapTransferSharedStatics.getMapId();
            NetworkManager.sendToServer(message);
            Log.info("MapTransferCompleteState", "Client ready signal sent to server");
        } catch (Exception e) {
            Log.error("MapTransferCompleteState", "Error sending completion signal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void updateState(float delta) {
        // This state just waits for server to transition us to the next phase
        // Could add timeout logic here if needed
    }

    @Override
    public void end() {

    }
}
