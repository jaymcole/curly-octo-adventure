package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.HostGameWorld;
import curly.octo.game.clientStates.mapTransfer.MapTransferCompleteState;
import curly.octo.game.serverObjects.ClientProfile;
import curly.octo.game.serverStates.BaseGameStateServer;
import curly.octo.game.serverStates.ServerStateManager;
import curly.octo.game.serverStates.playing.ServerPlayingState;
import curly.octo.network.GameServer;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.mapTransferMessages.MapTransferCompleteMessage;

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
        for(ClientProfile client : hostGameWorld.clientProfiles.values()) {
            // TODO: include connection status. We shouldn't wait on disconnected clients
            Log.info("update","Client Current State: " + client.currentState);
            if (!Objects.equals(client.currentState, MapTransferCompleteState.class.getSimpleName())) {
                allClientsReady = false;
                break;
            }
        }

        if (allClientsReady) {
            ServerStateManager.setServerState(ServerPlayingState.class);
            NetworkManager.sendToAllClients(new MapTransferCompleteMessage());
        }
    }

    @Override
    public void end() {

    }
}
