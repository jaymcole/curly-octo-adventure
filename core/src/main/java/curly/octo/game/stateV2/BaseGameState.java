package curly.octo.game.stateV2;

import com.badlogic.gdx.Screen;

public abstract class BaseGameState {

    protected BaseScreen stateScreen;
    private boolean renderGameInBackground;

    public BaseGameState() {
    }

    public BaseScreen getStateScreen() {
        return stateScreen;
    }

    public boolean getGamePlaying() {
        return renderGameInBackground;
    }

    public abstract void start();
    public abstract void updateState(float delta);
    public abstract void end();

    public void dispose() {

    }
}
