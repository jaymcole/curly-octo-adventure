package curly.octo.game.serverStates;

public abstract class BaseGameStateServer {
    public abstract void start();
    public abstract void update(float delta);
    public abstract void end();

}
