package curly.octo.game.clientStates.mapTransfer;

import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;

public class MapTransferCompleteState extends BaseGameStateClient {
    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());

    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
