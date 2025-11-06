package curly.octo.client.clientStates.mapTransferStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameMode;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;

public class MapTransferCompleteState extends BaseGameStateClient {
    private static final float TIMEOUT_SECONDS = 30.0f;
    private static final float RETRY_INTERVAL_SECONDS = 10.0f;
    private static final int MAX_RETRIES = 3;

    private float timeWaiting = 0.0f;
    private int retryCount = 0;
    private float timeSinceLastRetry = 0.0f;

    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());
        Log.info("MapTransferCompleteState", "Map transfer complete");

        // Disconnect bulk transfer channel - no longer needed
        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null && clientGameMode.getGameClient() != null) {
            clientGameMode.getGameClient().disconnectBulkTransfer();
            Log.info("MapTransferCompleteState", "Bulk transfer channel disconnected");
        }

        Log.info("MapTransferCompleteState", "Client ready - waiting for server to send MapTransferCompleteMessage");
        timeWaiting = 0.0f;
        retryCount = 0;
        timeSinceLastRetry = 0.0f;
    }

    @Override
    public void updateState(float delta) {
        timeWaiting += delta;
        timeSinceLastRetry += delta;

        // Check if we should retry sending our state
        if (timeSinceLastRetry >= RETRY_INTERVAL_SECONDS && retryCount < MAX_RETRIES) {
            retryCount++;
            timeSinceLastRetry = 0.0f;

            Log.warn("MapTransferCompleteState", "Still waiting for server after " + (int)timeWaiting +
                     " seconds. Retry attempt " + retryCount + "/" + MAX_RETRIES);
            Log.warn("MapTransferCompleteState", "Re-sending state change notification to server...");

            // Re-send state change notification
            StateManager.resendCurrentStateToServer();
        }

        // Check for final timeout
        if (timeWaiting >= TIMEOUT_SECONDS) {
            Log.error("MapTransferCompleteState", "=".repeat(80));
            Log.error("MapTransferCompleteState", "CRITICAL ERROR: Timeout waiting for MapTransferCompleteMessage from server");
            Log.error("MapTransferCompleteState", "Waited " + (int)timeWaiting + " seconds with " +
                      retryCount + " retry attempts");
            Log.error("MapTransferCompleteState", "=".repeat(80));
            Log.error("MapTransferCompleteState", "");
            Log.error("MapTransferCompleteState", "Possible causes:");
            Log.error("MapTransferCompleteState", "  1. Server is not in ServerWaitForClientsToBeReadyState");
            Log.error("MapTransferCompleteState", "  2. Client profile not properly registered on server");
            Log.error("MapTransferCompleteState", "  3. Network connection lost");
            Log.error("MapTransferCompleteState", "  4. Server crashed or hung");
            Log.error("MapTransferCompleteState", "");
            Log.error("MapTransferCompleteState", "Client will remain in this state indefinitely.");
            Log.error("MapTransferCompleteState", "Check server logs for more information.");
            Log.error("MapTransferCompleteState", "=".repeat(80));

            // Reset timeout to avoid spamming logs
            timeWaiting = 0.0f;
        }
    }

    @Override
    public void end() {

    }
}
