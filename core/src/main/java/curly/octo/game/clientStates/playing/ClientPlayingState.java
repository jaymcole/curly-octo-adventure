package curly.octo.game.clientStates.playing;

import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;

public class ClientPlayingState extends BaseGameStateClient {
    public ClientPlayingState(BaseScreen screen) {
        super(screen);
        this.renderGameInBackground = true;
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
