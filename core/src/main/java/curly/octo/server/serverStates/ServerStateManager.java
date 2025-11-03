package curly.octo.server.serverStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.ServerCoordinator;
import curly.octo.server.serverStates.mapTransfer.ServerMapTransferState;
import curly.octo.server.serverStates.mapTransfer.ServerWaitForClientsToBeReadyState;
import curly.octo.server.serverStates.playing.ServerPlayingState;
import curly.octo.server.GameServer;

import java.util.HashMap;

public class ServerStateManager {

    private static BaseGameStateServer currentState;

    private static HashMap<Class, BaseGameStateServer> cachedServerStates;

    private static GameServer gameServer;
    private static ServerCoordinator serverCoordinator;

    public static void initializeManager(GameServer server, ServerCoordinator coordinator) {
        gameServer = server;
        serverCoordinator = coordinator;

        cachedServerStates = new HashMap<>();
        cachedServerStates.put(ServerMapTransferState.class, new ServerMapTransferState(gameServer, serverCoordinator));
        cachedServerStates.put(ServerWaitForClientsToBeReadyState.class, new ServerWaitForClientsToBeReadyState(gameServer, serverCoordinator));
        cachedServerStates.put(ServerPlayingState.class, new ServerPlayingState(gameServer, serverCoordinator));
    }

    public static void update(float delta) {
        if (currentState != null) {
            currentState.update(delta);
        }
    }

    public static BaseGameStateServer getCurrentState() {
        return currentState;
    }

    public static void setServerState(Class newState) {
        if (currentState != null) {
            currentState.end();
        }

        if (cachedServerStates.containsKey(newState)) {
            currentState = cachedServerStates.get(newState);
            currentState.start();
        } else {
            Log.error("setServerState", "Server is missing cached state: " + newState.getName());
        }

    }
}

