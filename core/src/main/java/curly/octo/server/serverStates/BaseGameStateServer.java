package curly.octo.server.serverStates;

import curly.octo.server.ServerCoordinator;
import curly.octo.server.GameServer;

public abstract class BaseGameStateServer {
    protected GameServer gameServer;
    protected ServerCoordinator serverCoordinator;

    public BaseGameStateServer(GameServer gameServer, ServerCoordinator serverCoordinator) {
        this.gameServer = gameServer;
        this.serverCoordinator = serverCoordinator;
    }

    public abstract void start();
    public abstract void update(float delta);
    public abstract void end();

}
