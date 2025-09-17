package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_TRANSFER_DOWNLOADING state.
 * Responsible for:
 * - Tracking map download progress from server during client join
 * - Updating progress based on chunked transfer status
 * - Transitioning to rebuilding once download is complete
 */
public class MapTransferDownloadingHandler extends AbstractStateHandler {

    private ClientGameMode clientGameMode;

    public MapTransferDownloadingHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_TRANSFER_DOWNLOADING,
              GameState.MAP_TRANSFER_REBUILDING,    // Normal progression
              GameState.ERROR,                      // If download fails
              GameState.CONNECTION_LOST);           // If disconnected during download

        this.clientGameMode = clientGameMode;
    }

    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);

        logAction("Starting map transfer download");
        updateProgress(context, 0.0f, "Downloading map from server...");

        // Mark that we're ready to receive map data
        context.setStateData("download_started", true);
        context.setStateData("download_start_time", System.currentTimeMillis());

        // Initialize download tracking
        context.setStateData("bytes_received", 0L);
        context.setStateData("total_bytes", 0L);
        context.setStateData("chunks_received", 0);
        context.setStateData("total_chunks", 0);
    }

    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if download is complete
        Boolean downloadComplete = context.getStateData("download_complete", Boolean.class, false);

        if (downloadComplete) {
            // Ensure minimum display time for better UX (so users can see transfer progress)
            Long startTime = context.getStateData("download_start_time", Long.class);
            long minDisplayTimeMs = 1000; // 1 second minimum display for map transfer

            if (startTime != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < minDisplayTimeMs) {
                    // Still within minimum display time - show progress but don't transition yet
                    long remaining = minDisplayTimeMs - elapsed;
                    updateProgress(context, 0.9f + (0.1f * elapsed / minDisplayTimeMs),
                        "Download complete, loading map... (" + (remaining / 100) / 10.0f + "s)");
                    return;
                }
            }

            logAction("Map transfer download complete, transitioning to rebuilding state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_TRANSFER_REBUILDING);
            }
            return;
        }

        // Download progress is handled by ClientGameMode.onChunkReceived()
        // to avoid competing progress updates that cause oscillation
    }

    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);

        // Ensure download is marked complete
        context.setStateData("download_complete", true);
        logAction("Map transfer download state completed");
    }
}