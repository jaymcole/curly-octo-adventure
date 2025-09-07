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
            logAction("Download complete, transitioning to rebuilding state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_REGENERATION_REBUILDING);
            }
            return;
        }
        
        // Update download progress based on current transfer status
        updateDownloadProgress(context);
    }
    
    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);
        
        // Ensure download is marked complete
        context.setStateData("download_complete", true);
        logAction("Download state completed");
    }
    
    /**
     * Called by the network layer when map transfer starts
     */
    public void onMapTransferStart(String mapId, int totalChunks, long totalSize) {
        if (clientGameMode != null) {
            StateContext context = clientGameMode.getStateManager().getStateContext();
            
            if (context.isInState(GameState.MAP_REGENERATION_DOWNLOADING)) {
                context.setStateData("map_id", mapId);
                context.setStateData("total_chunks", totalChunks);
                context.setStateData("total_bytes", totalSize);
                
                logAction(String.format("Map transfer started: %s (%d chunks, %d bytes)", 
                    mapId, totalChunks, totalSize));
                    
                updateProgress(context, 0.1f, String.format("Receiving map data (%d chunks)...", totalChunks));
            }
        }
    }
    
    /**
     * Called by the network layer when a chunk is received
     */
    public void onChunkReceived(String mapId, int chunkIndex, byte[] chunkData) {
        if (clientGameMode != null) {
            StateContext context = clientGameMode.getStateManager().getStateContext();
            
            if (context.isInState(GameState.MAP_REGENERATION_DOWNLOADING)) {
                int chunksReceived = context.getStateData("chunks_received", Integer.class, 0) + 1;
                long bytesReceived = context.getStateData("bytes_received", Long.class, 0L) + chunkData.length;
                
                context.setStateData("chunks_received", chunksReceived);
                context.setStateData("bytes_received", bytesReceived);
                
                // Update progress based on chunks received
                Integer totalChunks = context.getStateData("total_chunks", Integer.class, 0);
                if (totalChunks > 0) {
                    float progress = 0.1f + (chunksReceived / (float) totalChunks) * 0.8f;
                    updateProgress(context, progress, 
                        String.format("Received %d/%d chunks...", chunksReceived, totalChunks));
                }
                
                // Check if download is complete
                if (totalChunks > 0 && chunksReceived >= totalChunks) {
                    updateProgress(context, 1.0f, "Map download complete");
                    context.setStateData("download_complete", true);
                    logAction("All chunks received, download complete");
                }
            }
        }
    }
    
    /**
     * Called by the network layer when transfer is complete
     */
    public void onMapTransferComplete(String mapId) {
        if (clientGameMode != null) {
            StateContext context = clientGameMode.getStateManager().getStateContext();
            
            if (context.isInState(GameState.MAP_REGENERATION_DOWNLOADING)) {
                updateProgress(context, 1.0f, "Map transfer complete");
                context.setStateData("download_complete", true);
                context.setStateData("received_map_id", mapId);
                
                logAction("Map transfer completed: " + mapId);
            }
        }
    }
    
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