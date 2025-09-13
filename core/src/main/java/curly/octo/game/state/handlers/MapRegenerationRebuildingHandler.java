package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import curly.octo.game.ClientGameWorld;
import curly.octo.map.GameMap;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_REGENERATION_REBUILDING state.
 * Responsible for:
 * - Rebuilding the game world from downloaded map data
 * - Recreating physics world and lighting systems
 * - Restoring player state and position
 * - Transitioning to complete when ready
 */
public class MapRegenerationRebuildingHandler extends AbstractStateHandler {
    
    private ClientGameMode clientGameMode;
    
    public MapRegenerationRebuildingHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_REGENERATION_REBUILDING,
              GameState.MAP_REGENERATION_COMPLETE, // Normal progression
              GameState.ERROR,                     // If rebuilding fails
              GameState.CONNECTION_LOST);          // If disconnected during rebuilding
        
        this.clientGameMode = clientGameMode;
    }
    
    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);
        
        logAction("Starting world rebuilding");
        updateProgress(context, 0.0f, "Initializing new map data...");
        
        // Start the rebuilding process
        startRebuilding(context);
    }
    
    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if rebuilding is complete
        Boolean rebuildingComplete = context.getStateData("rebuilding_complete", Boolean.class, false);
        
        if (rebuildingComplete) {
            logAction("Rebuilding complete, transitioning to completion state");
            if (clientGameMode != null) {
                clientGameMode.getStateManager().requestStateChange(GameState.MAP_REGENERATION_COMPLETE);
            }
        }
    }
    
    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);
        
        // Mark rebuilding as complete
        context.setStateData("rebuilding_complete", true);
        logAction("Rebuilding state completed");
    }
    
    private void startRebuilding(StateContext context) {
        if (clientGameMode == null) {
            logAction("No client game mode available, skipping rebuilding");
            context.setStateData("rebuilding_complete", true);
            return;
        }
        
        try {
            // Get the received map from the download state (stored by the new map listener)
            Object receivedMapObj = context.getStateData("received_map", Object.class);
            if (receivedMapObj == null) {
                Log.error("MapRegenerationRebuildingHandler", "No map data found from download");
                context.setStateData("error_message", "No map data available for rebuilding");
                return;
            }
            
            updateProgress(context, 0.1f, "Processing new map data...");
            
            // Actually process the map now (this is what was happening immediately before)
            ClientGameWorld gameWorld = (ClientGameWorld) clientGameMode.getGameWorld();
            if (gameWorld != null) {
                logAction("Setting new map in game world");
                updateProgress(context, 0.3f, "Creating new game world...");
                
                // Cast the received map object to GameMap
                gameWorld.setMap((GameMap) receivedMapObj);
                
                updateProgress(context, 0.6f, "Reinitializing players...");
                logAction("Reinitializing players for new map");
                gameWorld.reinitializePlayersAfterMapRegeneration();
                
                updateProgress(context, 0.9f, "Finalizing world setup...");
                
                // Store completion in state for other systems
                context.setStateData("received_map_id", "map_" + System.currentTimeMillis());
            }
            
            // Mark rebuilding as started
            context.setStateData("rebuilding_started", true);
            context.setStateData("rebuilding_start_time", System.currentTimeMillis());
            
            logAction("Map rebuilding process completed successfully");

            // Add a small delay to show completion progress
            Thread rebuildingCompletionThread = new Thread(() -> {
                try {
                    Thread.sleep(500); // Half second delay to show completion
                    updateProgress(context, 1.0f, "World rebuilding complete");
                    Thread.sleep(500); // Another half second before marking complete
                    context.setStateData("rebuilding_complete", true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    context.setStateData("rebuilding_complete", true);
                }
            });
            rebuildingCompletionThread.start();
            
        } catch (Exception e) {
            Log.error("MapRegenerationRebuildingHandler", "Error starting rebuilding", e);
            context.setStateData("error_message", "Failed to start rebuilding: " + e.getMessage());
        }
    }
    
    
    /**
     * This method would be called by the actual map loading system
     * when each phase of rebuilding is complete
     */
    public void onRebuildingPhaseComplete(String phase, float progress, String message) {
        if (clientGameMode != null) {
            StateContext context = clientGameMode.getStateManager().getStateContext();
            
            if (context.isInState(GameState.MAP_REGENERATION_REBUILDING)) {
                updateProgress(context, progress, message);
                logAction(String.format("Rebuilding phase '%s' complete: %.1f%%", phase, progress * 100));
                
                if (progress >= 1.0f) {
                    context.setStateData("rebuilding_complete", true);
                }
            }
        }
    }
}