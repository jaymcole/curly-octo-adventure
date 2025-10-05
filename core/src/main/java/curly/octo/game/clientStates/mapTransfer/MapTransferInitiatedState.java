package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage;

public class MapTransferInitiatedState extends BaseGameStateClient {
    private String mapId;
    private int totalChunks;
    private long totalSize;

    public MapTransferInitiatedState(BaseScreen screen) {
        super(screen);
    }

    public void handleMapTransferBegin(MapTransferBeginMessage message) {
        this.mapId = message.mapId;
        this.totalChunks = message.totalChunks;
        this.totalSize = message.totalSize;

        Log.info("MapTransferInitiatedState", "Map transfer initiated: " + mapId +
                " (" + totalChunks + " chunks, " + totalSize + " bytes)");

        // Activate this state if not already
        StateManager.setCurrentState(MapTransferInitiatedState.class);

        // Auto-transition to transfer state
        StateManager.setCurrentState(MapTransferTransferState.class);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferInitiatedState.class.getSimpleName());
    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
