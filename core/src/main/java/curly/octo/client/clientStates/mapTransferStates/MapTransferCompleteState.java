package curly.octo.client.clientStates.mapTransferStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameMode;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;

public class MapTransferCompleteState extends BaseGameStateClient {

    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());
        Log.info("MapTransferCompleteState", "Map transfer complete");

        // Disconnect bulk transfer channel - no longer needed
        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null && clientGameMode.getGameClient() != null) {
            clientGameMode.getGameClient().disconnectBulkTransfer();
            Log.info("MapTransferCompleteState", "Bulk transfer channel disconnected");
        }

        Log.info("MapTransferCompleteState", "Client ready - waiting for server");
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
