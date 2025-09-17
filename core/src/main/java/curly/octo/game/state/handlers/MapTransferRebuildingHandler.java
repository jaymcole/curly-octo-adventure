package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import curly.octo.game.ClientGameWorld;
import curly.octo.map.GameMap;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the MAP_TRANSFER_REBUILDING state.
 * Responsible for:
 * - Loading the transferred map into the game world
 * - Setting up rendering and physics for the new map
 * - Transitioning to PLAYING state when complete
 */
public class MapTransferRebuildingHandler extends AbstractStateHandler {

    private ClientGameMode clientGameMode;

    public MapTransferRebuildingHandler(ClientGameMode clientGameMode) {
        super(GameState.MAP_TRANSFER_REBUILDING,
              GameState.PLAYING,               // Normal progression - go straight to playing
              GameState.ERROR,                 // If rebuilding fails
              GameState.CONNECTION_LOST);      // If disconnected during rebuild

        this.clientGameMode = clientGameMode;
    }

    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);

        logAction("Starting map transfer rebuilding");
        updateProgress(context, 0.1f, "Loading map into game world...");

        // Mark rebuild start time
        context.setStateData("rebuild_start_time", System.currentTimeMillis());
    }

    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Check if we have received map data to process
        GameMap receivedMap = context.getStateData("received_map", GameMap.class);

        if (receivedMap != null) {
            try {
                logAction("Processing received map data");
                updateProgress(context, 0.3f, "Setting up map rendering...");

                // Apply the new map to the game world using the same approach as regeneration
                if (clientGameMode != null && clientGameMode.getGameWorld() != null) {
                    logAction("Setting new map in game world");
                    updateProgress(context, 0.4f, "Loading map into game world...");

                    // Use setMap method like the regeneration handler does
                    ClientGameWorld gameWorld = (ClientGameWorld) clientGameMode.getGameWorld();
                    gameWorld.setMap(receivedMap);

                    updateProgress(context, 0.7f, "Map loaded, reinitializing players...");

                    // Reinitialize players for the new map (same as regeneration)
                    gameWorld.reinitializePlayersAfterMapRegeneration();

                    updateProgress(context, 0.9f, "Finalizing map setup...");

                    // Clear the received map from context since we've processed it
                    context.setStateData("received_map", null);
                    context.setStateData("rebuild_complete", true);

                    updateProgress(context, 1.0f, "Map transfer complete!");

                    // Small delay to show completion before transitioning
                    context.setStateData("completion_time", System.currentTimeMillis());

                    logAction("Map transfer rebuilding completed successfully");
                } else {
                    Log.error("MapTransferRebuildingHandler", "Cannot rebuild - game world is null");
                    context.setStateData("error_message", "Game world not available for map loading");
                    return;
                }

            } catch (Exception e) {
                Log.error("MapTransferRebuildingHandler", "Failed to rebuild map: " + e.getMessage());
                e.printStackTrace();
                context.setStateData("error_message", "Failed to load map: " + e.getMessage());
                return;
            }
        }

        // Check if rebuild is complete and transition after brief delay
        Boolean rebuildComplete = context.getStateData("rebuild_complete", Boolean.class, false);
        if (rebuildComplete) {
            Long completionTime = context.getStateData("completion_time", Long.class);
            if (completionTime != null) {
                long elapsed = System.currentTimeMillis() - completionTime;
                if (elapsed > 500) { // 0.5 second delay to show completion
                    logAction("Transitioning to PLAYING state");
                    if (clientGameMode != null) {
                        clientGameMode.getStateManager().requestStateChange(GameState.PLAYING);
                    }
                }
            }
        }
    }

    @Override
    public void onExitState(StateContext context) {
        super.onExitState(context);

        // Ensure rebuild is marked complete
        context.setStateData("rebuild_complete", true);
        logAction("Map transfer rebuilding state completed");
    }
}