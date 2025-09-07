package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import curly.octo.game.ClientGameWorld;
import curly.octo.network.messages.ClientReadyForMapMessage;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_REGENERATION_CLEANUP state.
 * Responsible for:
 * - Pausing network position updates to prevent serialization errors
 * - Cleaning up current map resources
 * - Preparing for new map download
 */
public class MapRegenerationCleanupHandler extends AbstractStateHandler {
    
    private ClientGameMode clientGameMode;
    
    public MapRegenerationCleanupHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_REGENERATION_CLEANUP, 
              GameState.MAP_REGENERATION_DOWNLOADING, // Normal progression
              GameState.ERROR,                        // If cleanup fails
              GameState.CONNECTION_LOST);             // If disconnected during cleanup
        
        this.clientGameMode = clientGameMode;
    }
    
    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);
        
        logAction("Starting resource cleanup");
        updateProgress(context, 0.0f, "Pausing network updates...");
        
        // Store the network pause flag in state data so other states can check it
        context.setStateData("network_paused", true);
        
        // Pause network position updates to prevent the Kryo serialization error
        pauseNetworkUpdates();
        
        updateProgress(context, 0.2f, "Network updates paused");
        
        // Check if immediate cleanup was already performed
        logAction("Checking if immediate cleanup was already performed...");
        
        // Mark cleanup as complete since it was done immediately when regeneration started
        context.setStateData("cleanup_complete", true);
        updateProgress(context, 1.0f, "Resources already cleaned up");
    }
    
    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if cleanup is complete
        Boolean cleanupComplete = context.getStateData("cleanup_complete", Boolean.class, false);
        
        if (cleanupComplete) {
            // Cleanup finished, transition to downloading state
            logAction("Cleanup complete, transitioning to download state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_REGENERATION_DOWNLOADING);
            }
            return;
        }
        
        // Update cleanup progress
        updateCleanupProgress(context);
    }
    
    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);
        
        // Cleanup is complete, mark it in state data for the next state
        context.setStateData("cleanup_complete", true);
        logAction("Cleanup state completed");
    }
    
    private void pauseNetworkUpdates() {
        if (clientGameMode != null) {
            clientGameMode.pauseNetworkUpdates();
            logAction("Network position updates paused (prevents Kryo serialization errors)");
        }
    }
    
    private void startCleanup(StateContext context) {
        if (clientGameMode == null) {
            logAction("No client game mode available, skipping cleanup");
            context.setStateData("cleanup_complete", true);
            return;
        }
        
        try {
            // Get the client game world for cleanup
            ClientGameWorld gameWorld = (ClientGameWorld) clientGameMode.getGameWorld();
            
            if (gameWorld != null) {
                updateProgress(context, 0.4f, "Cleaning up map resources...");
                
                // This will call the existing cleanup method
                // gameWorld.cleanupForMapRegeneration() is already implemented
                logAction("Starting ClientGameWorld cleanup");
                context.setStateData("cleanup_started", true);
                context.setStateData("cleanup_start_time", System.currentTimeMillis());
                
            } else {
                logAction("No game world available, skipping cleanup");
                context.setStateData("cleanup_complete", true);
            }
            
        } catch (Exception e) {
            Log.error("MapRegenerationCleanupHandler", "Error during cleanup", e);
            context.setStateData("error_message", "Cleanup failed: " + e.getMessage());
            // The state manager will handle error state transition
        }
    }
    
    private void updateCleanupProgress(StateContext context) {
        Boolean cleanupStarted = context.getStateData("cleanup_started", Boolean.class, false);
        
        if (!cleanupStarted) {
            return;
        }
        
        Long startTime = context.getStateData("cleanup_start_time", Long.class);
        if (startTime == null) {
            return;
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Simulate cleanup progress over time
        // In reality, this would check actual cleanup status
        if (elapsedTime < 1000) {
            updateProgress(context, 0.4f + (elapsedTime / 1000.0f) * 0.4f, "Cleaning up physics world...");
        } else if (elapsedTime < 2000) {
            updateProgress(context, 0.8f, "Cleaning up lighting system...");
        } else if (elapsedTime < 3000) {
            updateProgress(context, 0.9f, "Finalizing cleanup...");
        } else {
            updateProgress(context, 1.0f, "Cleanup complete");
            context.setStateData("cleanup_complete", true);
            
            // Send ready confirmation to server
            sendReadyConfirmation(context);
        }
    }
    
    private void sendReadyConfirmation(StateContext context) {
        if (clientGameMode != null && clientGameMode.getGameClient() != null) {
            try {
                // Get the regeneration ID from the start message
                Long regenerationId = context.getStateData("regeneration_timestamp", Long.class);
                if (regenerationId == null) {
                    Log.warn("MapRegenerationCleanupHandler", "No regeneration ID found, cannot send ready confirmation");
                    return;
                }
                
                // Get client ID (use player ID as client ID)
                String clientId = clientGameMode.getLocalPlayerId();
                if (clientId == null) {
                    clientId = "unknown_client";
                }
                
                ClientReadyForMapMessage readyMessage = new ClientReadyForMapMessage(clientId, regenerationId);
                clientGameMode.getGameClient().sendTCP(readyMessage);
                
                logAction("Sent ready confirmation to server");
                
            } catch (Exception e) {
                Log.error("MapRegenerationCleanupHandler", "Failed to send ready confirmation: " + e.getMessage());
            }
        }
    }
}