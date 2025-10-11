package curly.octo.game.serverStates;

import curly.octo.game.HostGameWorld;
import curly.octo.network.GameServer;

public abstract class BaseGameStateServer {
    protected GameServer gameServer;
    protected HostGameWorld hostGameWorld;

    public BaseGameStateServer(GameServer gameServer, HostGameWorld hostGameWorld) {
        this.gameServer = gameServer;
        this.hostGameWorld = hostGameWorld;
    }

    public abstract void start();
    public abstract void update(float delta);
    public abstract void end();

}
