package curly.octo.server.serverStates.mapTransfer;

import curly.octo.server.ServerCoordinator;
import curly.octo.client.clientStates.mapTransferStates.MapTransferCompleteState;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.playerManagement.ConnectionStatus;
import curly.octo.server.serverStates.BaseGameStateServer;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.server.serverStates.playing.ServerPlayingState;
import curly.octo.server.GameServer;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferCompleteMessage;

import java.util.Map;
import java.util.Objects;

public class ServerWaitForClientsToBeReadyState extends BaseGameStateServer {
    public ServerWaitForClientsToBeReadyState(GameServer gameServer, ServerCoordinator serverCoordinator) {
        super(gameServer, serverCoordinator);
    }

    @Override
    public void start() {

    }

    @Override
    public void update(float delta) {

        boolean allClientsReady = true;
        for(Map.Entry<String, ClientProfile> client : serverCoordinator.clientProfiles.entrySet()) {
            if (client.getValue().connectionStatus == ConnectionStatus.CONNECTED) {
                if (!Objects.equals(client.getValue().currentState, MapTransferCompleteState.class.getSimpleName())) {
                    allClientsReady = false;
                    break;
                }
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
