package curly.octo.server.serverStates;

import curly.octo.server.HostGameWorld;
import curly.octo.server.GameServer;

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
