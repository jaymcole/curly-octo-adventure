package curly.octo.game.clientStates.mapTransfer;

import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;

public class MapTransferBuildAssetsState extends BaseGameStateClient {
    public MapTransferBuildAssetsState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferBuildAssetsState.class.getSimpleName());

    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
