package curly.octo.server.serverStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.server.ServerCoordinator;
import curly.octo.client.clientStates.mapTransferStates.MapTransferCompleteState;
import curly.octo.server.playerManagement.ClientConnectionKey;
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
        // Complete any pending player assignments now that map transfers are done
        // This ensures clients have received game objects before getting assignment messages
        gameServer.completePendingPlayerAssignments();
    }

    @Override
    public void update(float delta) {
        // Fix: If no clients are registered, allClientsReady should be false

        boolean allClientsReady = !serverCoordinator.clientManager.getAllClientProfiles().isEmpty();
        int connectedCount = 0;
        int readyCount = 0;

        for(ClientProfile client : serverCoordinator.clientManager.getAllClientProfiles()) {

            if (client.connectionStatus == ConnectionStatus.CONNECTED) {
                connectedCount++;
                String currentState = client.currentState != null ? client.currentState : "null";
                String targetState = MapTransferCompleteState.class.getSimpleName();
                if (!Objects.equals(currentState, targetState)) {
                    allClientsReady = false;
                } else {
                    readyCount++;
                }
            }
        }

        if (connectedCount > 0) {
            Log.info("ServerWaitForClientsToBeReadyState",
                     "Readiness check: " + readyCount + "/" + connectedCount + " clients ready, allReady=" + allClientsReady);
        }

        if (allClientsReady) {
            Log.info("ServerWaitForClientsToBeReadyState",
                     "ALL CLIENTS READY! Transitioning to ServerPlayingState and sending MapTransferCompleteMessage");
            ServerStateManager.setServerState(ServerPlayingState.class);
            NetworkManager.sendToAllClients(new MapTransferCompleteMessage());
            Log.info("ServerWaitForClientsToBeReadyState", "MapTransferCompleteMessage sent to all clients");
        }
    }

    @Override
    public void end() {

    }
}
