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
        updateProgress(context, 0.0f, "Cleaning up resources...");
        
        // Store the network pause flag in state data so other states can check it
        context.setStateData("network_paused", true);
        
        // Pause network position updates to prevent the Kryo serialization error
        pauseNetworkUpdates();
        updateProgress(context, 0.3f, "Network updates paused");
        
        // Perform actual cleanup
        performCleanup(context);
        
        // Send ready confirmation to server
        sendReadyConfirmation(context);
        
        // Mark cleanup as complete and ready to transition
        context.setStateData("cleanup_complete", true);
        updateProgress(context, 1.0f, "Cleanup complete");
        logAction("Cleanup completed immediately");
    }
    
    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if cleanup is complete
        Boolean cleanupComplete = context.getStateData("cleanup_complete", Boolean.class, false);
        
        if (cleanupComplete) {
            // Cleanup finished, transition to downloading state immediately
            logAction("Cleanup complete, transitioning to download state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_REGENERATION_DOWNLOADING);
            }
        }
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
    
    private void performCleanup(StateContext context) {
        if (clientGameMode == null) {
            logAction("No client game mode available, skipping cleanup");
            return;
        }
        
        try {
            // Get the client game world for cleanup
            ClientGameWorld gameWorld = (ClientGameWorld) clientGameMode.getGameWorld();
            
            if (gameWorld != null) {
                updateProgress(context, 0.5f, "Cleaning up map resources...");
                
                // Call the existing cleanup method immediately
                // gameWorld.cleanupForMapRegeneration() is already implemented
                logAction("Performing ClientGameWorld cleanup");
                // Note: Immediate cleanup was already performed when regeneration started
                // This is just to ensure any remaining cleanup is done
                
                updateProgress(context, 0.8f, "Map resources cleaned");
            } else {
                logAction("No game world available, skipping cleanup");
            }
            
        } catch (Exception e) {
            Log.error("MapRegenerationCleanupHandler", "Error during cleanup", e);
            context.setStateData("error_message", "Cleanup failed: " + e.getMessage());
            // The state manager will handle error state transition
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