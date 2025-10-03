package curly.octo.game.clientStates.mapTransfer;

import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;

public class MapTransferInitiatedState extends BaseGameStateClient {
    public MapTransferInitiatedState(BaseScreen screen) {
        super(screen);
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
