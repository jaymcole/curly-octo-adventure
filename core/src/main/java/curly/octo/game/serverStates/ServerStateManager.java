package curly.octo.game.serverStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.serverStates.mapTransfer.ServerMapTransferState;

import java.util.HashMap;

public class ServerStateManager {

    private static BaseGameStateServer currentState;

    private static HashMap<Class, BaseGameStateServer> cachedServerStates;

    public static void initializeManager() {
        cachedServerStates = new HashMap<>();
        cachedServerStates.put(ServerMapTransferState.class, new ServerMapTransferState());
    }

    public static void update(float delta) {
        if (currentState != null) {
            currentState.update(delta);
        }
    }

    public static void setServerState(Class newState) {
        if (currentState != null) {
            currentState.end();
        }

        if (cachedServerStates.containsKey(newState)) {
            currentState = cachedServerStates.get(newState);
            currentState.start();
        } else {
            Log.info("setServerState", "Server is missing cached state: " + newState.getClass());
        }

    }
}

