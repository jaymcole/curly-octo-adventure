package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.ClientGameMode;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.game.clientStates.mapTransfer.ui.MapTransferScreen;

/**
 * State responsible for connecting to the bulk transfer server.
 * This happens after map disposal and before chunk transfer begins.
 * Runs asynchronously to avoid blocking the render thread.
 */
public class MapTransferConnectBulkState extends BaseGameStateClient {
    private boolean connectionAttempted = false;
    private boolean connectionEstablished = false;
    private float elapsedTime = 0f;
    private static final float CONNECTION_TIMEOUT = 30f; // 30 second timeout

    public MapTransferConnectBulkState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage("Connecting to transfer server...");
        Log.info("MapTransferConnectBulkState", "Starting bulk transfer connection");

        // Reset flags
        connectionAttempted = false;
        connectionEstablished = false;
        elapsedTime = 0f;

        // CRITICAL: Pause position updates to prevent Kryo corruption during transfer
        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null) {
            clientGameMode.pauseNetworkUpdates();
            clientGameMode.disableInput();
            Log.info("MapTransferConnectBulkState", "Paused network updates and input for map transfer");

            // Connect bulk transfer channel for fast map download
            try {
                Log.info("MapTransferConnectBulkState", "Attempting to connect bulk transfer channel...");
                clientGameMode.getGameClient().connectBulkTransfer();
                connectionAttempted = true;
                Log.info("MapTransferConnectBulkState", "Bulk transfer connection initiated");
            } catch (Exception e) {
                Log.error("MapTransferConnectBulkState", "FATAL: Failed to connect bulk transfer: " + e.getMessage());
                Log.error("MapTransferConnectBulkState", "Exception class: " + e.getClass().getName());
                e.printStackTrace();
                // Don't continue with transfer if bulk connection failed
                throw new RuntimeException("Bulk transfer connection failed", e);
            }
        } else {
            Log.error("MapTransferConnectBulkState", "ClientGameMode is null - cannot connect bulk transfer");
            throw new RuntimeException("ClientGameMode is null");
        }
    }

    @Override
    public void updateState(float delta) {
        elapsedTime += delta;

        // Check for timeout
        if (elapsedTime > CONNECTION_TIMEOUT) {
            Log.error("MapTransferConnectBulkState", "Bulk connection timeout after " + CONNECTION_TIMEOUT + " seconds");
            throw new RuntimeException("Bulk transfer connection timeout");
        }

        // Wait for connection to be established
        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null && clientGameMode.getGameClient() != null) {
            if (clientGameMode.getGameClient().getBulkClient() != null &&
                clientGameMode.getGameClient().getBulkClient().isConnected()) {

                if (!connectionEstablished) {
                    connectionEstablished = true;
                    Log.info("MapTransferConnectBulkState", "Bulk transfer connection established in " +
                        String.format("%.2f", elapsedTime) + " seconds");
                    Log.info("MapTransferConnectBulkState", "Transitioning to transfer state");
                    StateManager.setCurrentState(MapTransferTransferState.class);
                }
            }
        }
    }

    @Override
    public void end() {
        // Reset flags for next time
        connectionAttempted = false;
        connectionEstablished = false;
        elapsedTime = 0f;
    }
}
