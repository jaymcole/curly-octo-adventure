package curly.octo.game.clientStates;

public abstract class BaseGameStateClient {

    protected BaseScreen stateScreen;
    protected boolean renderGameInBackground;

    public BaseGameStateClient(BaseScreen screen) {
        this.stateScreen = screen;
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
