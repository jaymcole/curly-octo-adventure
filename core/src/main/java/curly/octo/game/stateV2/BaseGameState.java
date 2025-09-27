package curly.octo.game.stateV2;

public abstract class BaseGameState {
    public abstract void beginState(State previousState);
    public abstract void updateState(float delta);
    public abstract void endState(State nextState);
}
