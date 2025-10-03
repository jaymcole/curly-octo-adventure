package curly.octo.game.clientStates.mapTransfer;

import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;

public class MapTransferDisposeState extends BaseGameStateClient {
    public MapTransferDisposeState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferDisposeState.class.getSimpleName());

    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
