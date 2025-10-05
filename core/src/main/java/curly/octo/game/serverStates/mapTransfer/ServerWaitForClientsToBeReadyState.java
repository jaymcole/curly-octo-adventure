package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.HostGameWorld;
import curly.octo.game.clientStates.mapTransfer.MapTransferCompleteState;
import curly.octo.game.serverObjects.ClientProfile;
import curly.octo.game.serverObjects.ConnectionStatus;
import curly.octo.game.serverStates.BaseGameStateServer;
import curly.octo.game.serverStates.ServerStateManager;
import curly.octo.game.serverStates.playing.ServerPlayingState;
import curly.octo.network.GameServer;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.mapTransferMessages.MapTransferCompleteMessage;

import java.util.Map;
import java.util.Objects;

public class ServerWaitForClientsToBeReadyState extends BaseGameStateServer {
    public ServerWaitForClientsToBeReadyState(GameServer gameServer, HostGameWorld hostGameWorld) {
        super(gameServer, hostGameWorld);
    }

    @Override
    public void start() {

    }

    @Override
    public void update(float delta) {

        boolean allClientsReady = true;
        for(Map.Entry<String, ClientProfile> client : hostGameWorld.clientProfiles.entrySet()) {

            Log.info("update", "Client Current State: " + client.getKey());
            Log.info("update", "Client Current State: " + client.getValue().currentState);
            Log.info("update", "Client Current State: " + client.getValue().connectionStatus);
            if (client.getValue().connectionStatus == ConnectionStatus.CONNECTED) {
                if (!Objects.equals(client.getValue().currentState, MapTransferCompleteState.class.getSimpleName())) {
                    allClientsReady = false;
    //                break;
                }
            }
        }

        Log.info("update", "");
        Log.info("update", "");
        Log.info("update", "");
        Log.info("update", "");


        if (allClientsReady) {
            ServerStateManager.setServerState(ServerPlayingState.class);
            NetworkManager.sendToAllClients(new MapTransferCompleteMessage());
        }
    }

    @Override
    public void end() {

    }
}
