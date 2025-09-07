package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_REGENERATION_COMPLETE state.
 * Responsible for:
 * - Resuming network position updates
 * - Final verification that everything is ready
 * - Transitioning back to normal PLAYING state
 */
public class MapRegenerationCompleteHandler extends AbstractStateHandler {
    
    private ClientGameMode clientGameMode;
    
    public MapRegenerationCompleteHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_REGENERATION_COMPLETE,
              GameState.PLAYING,         // Normal progression back to game
              GameState.ERROR,           // If final checks fail
              GameState.CONNECTION_LOST); // If disconnected during final steps
        
        this.clientGameMode = clientGameMode;
    }
    
    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);
        
        logAction("Map regeneration complete, finalizing");
        updateProgress(context, 0.0f, "Preparing to resume gameplay...");
        
        // Start the finalization process
        startFinalization(context);
    }
    
    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if finalization is complete
        Boolean finalizationComplete = context.getStateData("finalization_complete", Boolean.class, false);
        
        if (finalizationComplete) {
            logAction("Finalization complete, ready to play");
            // Auto-transition to PLAYING state after a brief delay
            Long completionTime = context.getStateData("completion_time", Long.class);
            if (completionTime != null && System.currentTimeMillis() - completionTime > 500) {
                // Give the user a moment to see the completion message, then transition
                if (clientGameMode != null) {
                    clientGameMode.getStateManager().requestStateChange(GameState.PLAYING);
                }
                return;
            }
        } else {
            // Update finalization progress
            updateFinalizationProgress(context);
        }
    }
    
    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);
        
        // Resume network updates - this is critical to fix the original Kryo error
        resumeNetworkUpdates(context);
        
        // Re-enable player input now that regeneration is complete
        reEnableInput(context);
        
        // Clear all regeneration-related state data
        clearRegenerationStateData(context);
        
        logAction("Map regeneration process fully completed");
    }
    
    private void startFinalization(StateContext context) {
        try {
            logAction("Starting finalization checks");
            
            // Verify that all previous steps completed successfully
            if (!verifyPreviousSteps(context)) {
                Log.error("MapRegenerationCompleteHandler", "Previous steps verification failed");
                context.setStateData("error_message", "Map regeneration verification failed");
                return;
            }
            
            updateProgress(context, 0.3f, "Verifying world integrity...");
            
            // Mark finalization as started
            context.setStateData("finalization_started", true);
            context.setStateData("finalization_start_time", System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.error("MapRegenerationCompleteHandler", "Error during finalization", e);
            context.setStateData("error_message", "Finalization failed: " + e.getMessage());
        }
    }
    
    private boolean verifyPreviousSteps(StateContext context) {
        // Check that cleanup was completed
        Boolean cleanupComplete = context.getStateData("cleanup_complete", Boolean.class, false);
        if (!cleanupComplete) {
            Log.error("MapRegenerationCompleteHandler", "Cleanup was not completed");
            return false;
        }
        
        // Check that download was completed
        Boolean downloadComplete = context.getStateData("download_complete", Boolean.class, false);
        if (!downloadComplete) {
            Log.error("MapRegenerationCompleteHandler", "Download was not completed");
            return false;
        }
        
        // Check that rebuilding was completed
        Boolean rebuildingComplete = context.getStateData("rebuilding_complete", Boolean.class, false);
        if (!rebuildingComplete) {
            Log.error("MapRegenerationCompleteHandler", "Rebuilding was not completed");
            return false;
        }
        
        // Check that we have a valid map ID
        String mapId = context.getStateData("received_map_id", String.class);
        if (mapId == null || mapId.trim().isEmpty()) {
            Log.error("MapRegenerationCompleteHandler", "No valid map ID found");
            return false;
        }
        
        logAction("All previous steps verified successfully");
        return true;
    }
    
    private void updateFinalizationProgress(StateContext context) {
        Boolean finalizationStarted = context.getStateData("finalization_started", Boolean.class, false);
        
        if (!finalizationStarted) {
            return;
        }
        
        Long startTime = context.getStateData("finalization_start_time", Long.class);
        if (startTime == null) {
            return;
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Short finalization process
        if (elapsedTime < 500) {
            updateProgress(context, 0.3f + (elapsedTime / 500.0f) * 0.4f, "Verifying world integrity...");
        } else if (elapsedTime < 1000) {
            updateProgress(context, 0.7f + ((elapsedTime - 500) / 500.0f) * 0.2f, "Preparing network resume...");
        } else {
            updateProgress(context, 1.0f, "Ready to resume gameplay!");
            context.setStateData("finalization_complete", true);
            context.setStateData("completion_time", System.currentTimeMillis());
            logAction("Finalization completed successfully");
        }
    }
    
    private void resumeNetworkUpdates(StateContext context) {
        // This is the key fix for the original Kryo serialization error
        Boolean networkPaused = context.getStateData("network_paused", Boolean.class, false);
        
        if (networkPaused) {
            if (clientGameMode != null) {
                clientGameMode.resumeNetworkUpdates();
                logAction("Network position updates resumed (fixes Kryo serialization issue)");
            }
            
            // Remove the pause flag
            context.setStateData("network_paused", false);
        }
    }
    
    private void reEnableInput(StateContext context) {
        // Re-enable player input after regeneration completes
        if (clientGameMode != null) {
            clientGameMode.enableInput();
            logAction("Player input re-enabled (map regeneration complete)");
        }
    }
    
    private void clearRegenerationStateData(StateContext context) {
        // Clean up all the state data used during regeneration
        String[] keysToRemove = {
            "cleanup_complete", "cleanup_started", "cleanup_start_time",
            "download_complete", "download_started", "download_start_time",
            "bytes_received", "total_bytes", "chunks_received", "total_chunks",
            "map_id", "received_map_id",
            "rebuilding_complete", "rebuilding_started", "rebuilding_start_time",
            "finalization_complete", "finalization_started", "finalization_start_time",
            "completion_time",
            // Client synchronization data
            "regeneration_timestamp", "new_map_seed", "regeneration_reason",
            // Map processing data
            "received_map", "map_received_time"
        };
        
        for (String key : keysToRemove) {
            context.setStateData(key, null);
        }
        
        logAction("Regeneration state data cleared");
    }
}