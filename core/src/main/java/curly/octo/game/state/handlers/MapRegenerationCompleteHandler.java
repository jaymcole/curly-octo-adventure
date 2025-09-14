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
              GameState.LOBBY,           // For initial generation awaiting player assignment
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
            // Only log once when finalization completes
            Boolean finalizationLogged = context.getStateData("finalization_logged", Boolean.class, false);
            if (!finalizationLogged) {
                logAction("Finalization complete, ready to play");
                context.setStateData("finalization_logged", true);
            }
            
            // Only attempt transition once
            Boolean transitionAttempted = context.getStateData("transition_attempted", Boolean.class, false);
            if (!transitionAttempted) {
                context.setStateData("transition_attempted", true);

                // Regeneration complete - transition directly to PLAYING state
                logAction("Map regeneration complete - transitioning to PLAYING state");
                if (clientGameMode != null) {
                    // Mark both flags as true for proper game activation
                    clientGameMode.setMapReceivedFlag(true);

                    Boolean isInitialGeneration = context.getStateData("is_initial_generation", Boolean.class, false);
                    if (isInitialGeneration) {
                        // For initial generation, we need to wait for player assignment
                        // But we still transition to PLAYING - the PLAYING handler will check if ready
                        logAction("Initial generation complete - transitioning to PLAYING (player assignment pending)");
                    } else {
                        // For normal regeneration, everything should be ready
                        logAction("Normal regeneration complete - transitioning to PLAYING");
                    }

                    clientGameMode.getStateManager().requestStateChange(GameState.PLAYING);
                }
            }
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
            
            // Show progress over time instead of completing immediately
            Thread finalizationThread = new Thread(() -> {
                try {
                    updateProgress(context, 0.3f, "Verifying regeneration...");
                    Thread.sleep(300);

                    updateProgress(context, 0.6f, "Preparing to resume...");
                    Thread.sleep(300);

                    updateProgress(context, 1.0f, "Ready to resume gameplay!");
                    Thread.sleep(500);

                    // Mark finalization as complete
                    context.setStateData("finalization_complete", true);
                    logAction("Finalization completed successfully");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    context.setStateData("finalization_complete", true);
                }
            });
            finalizationThread.start();
            
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
            "finalization_complete", "finalization_logged", "transition_attempted",
            "needs_player_reinit_after_assignment",
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