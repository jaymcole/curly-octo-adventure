package curly.octo.game.stateV2.MainMenuState;

import curly.octo.game.stateV2.BaseGameState;
import curly.octo.game.stateV2.StateManager;

public class MainMenuState extends BaseGameState {

    public MainMenuState() {
        this.stateScreen = StateManager.getCachedScreen(MainMenuScreen.class);
    }

    @Override
    public void start() {

    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }

}
