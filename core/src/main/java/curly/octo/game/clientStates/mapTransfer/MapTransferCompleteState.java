package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.mapTransfer.ui.MapTransferScreen;

public class MapTransferCompleteState extends BaseGameStateClient {

    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());
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
