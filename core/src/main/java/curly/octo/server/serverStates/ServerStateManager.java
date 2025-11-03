package curly.octo.server.serverStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.HostGameWorld;
import curly.octo.server.serverStates.mapTransfer.ServerMapTransferState;
import curly.octo.server.serverStates.mapTransfer.ServerWaitForClientsToBeReadyState;
import curly.octo.server.serverStates.playing.ServerPlayingState;
import curly.octo.server.GameServer;

import java.util.HashMap;

public class ServerStateManager {

    private static BaseGameStateServer currentState;

    private static HashMap<Class, BaseGameStateServer> cachedServerStates;

    private static GameServer gameServer;
    private static HostGameWorld hostGameWorld;

    public static void initializeManager(GameServer server, HostGameWorld world) {
        gameServer = server;
        hostGameWorld = world;

        cachedServerStates = new HashMap<>();
        cachedServerStates.put(ServerMapTransferState.class, new ServerMapTransferState(gameServer, hostGameWorld));
        cachedServerStates.put(ServerWaitForClientsToBeReadyState.class, new ServerWaitForClientsToBeReadyState(gameServer, hostGameWorld));
        cachedServerStates.put(ServerPlayingState.class, new ServerPlayingState(gameServer, hostGameWorld));
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

