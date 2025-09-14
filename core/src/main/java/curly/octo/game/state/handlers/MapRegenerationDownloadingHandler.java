package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_REGENERATION_DOWNLOADING state.
 * Responsible for:
 * - Tracking map download progress from server
 * - Updating progress based on chunked transfer status
 * - Transitioning to rebuilding once download is complete
 */
public class MapRegenerationDownloadingHandler extends AbstractStateHandler {
    
    private ClientGameMode clientGameMode;
    
    public MapRegenerationDownloadingHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_REGENERATION_DOWNLOADING,
              GameState.MAP_REGENERATION_REBUILDING, // Normal progression
              GameState.ERROR,                       // If download fails
              GameState.CONNECTION_LOST);            // If disconnected during download
        
        this.clientGameMode = clientGameMode;
    }
    
    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);
        
        logAction("Starting map download");
        updateProgress(context, 0.0f, "Waiting for new map data...");
        
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
            // Ensure minimum display time for better UX (so users can see regeneration progress)
            Long startTime = context.getStateData("download_start_time", Long.class);
            long minDisplayTimeMs = 1500; // 1.5 seconds minimum display

            if (startTime != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < minDisplayTimeMs) {
                    // Still within minimum display time - show progress but don't transition yet
                    long remaining = minDisplayTimeMs - elapsed;
                    updateProgress(context, 0.9f + (0.1f * elapsed / minDisplayTimeMs),
                        "Download complete, preparing to rebuild... (" + (remaining / 100) / 10.0f + "s)");
                    return;
                }
            }

            logAction("Download complete and minimum display time elapsed, transitioning to rebuilding state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_REGENERATION_REBUILDING);
            }
            return;
        }
        
        // NOTE: Download progress is now handled by ClientGameMode.onChunkReceived()
        // to avoid competing progress updates that cause oscillation
        // updateDownloadProgress(context); // DISABLED
    }
    
    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);
        
        // Ensure download is marked complete
        context.setStateData("download_complete", true);
        logAction("Download state completed");
    }
    
    // NOTE: Network transfer progress is now handled directly in ClientGameMode
    // to ensure proper main-thread execution and single source of truth.
    
    private void updateDownloadProgress(StateContext context) {
        // If we don't have chunk information yet, show a waiting animation
        Integer totalChunks = context.getStateData("total_chunks", Integer.class, 0);
        
        if (totalChunks == 0) {
            // No transfer started yet, show waiting animation
            Long startTime = context.getStateData("download_start_time", Long.class);
            if (startTime != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                
                // Timeout after 30 seconds of no response
                if (elapsed > 30000) {
                    Log.error("MapRegenerationDownloadingHandler", "Timeout waiting for map transfer to start");
                    context.setStateData("error_message", "Timeout waiting for map data from server");
                    return; // State manager will handle error transition
                }
                
                // Show waiting animation
                int dots = (int) ((elapsed / 500) % 4);
                String waiting = "Waiting for map data" + ".".repeat(dots);
                context.setStatusMessage(waiting);
            }
        }
    }
}