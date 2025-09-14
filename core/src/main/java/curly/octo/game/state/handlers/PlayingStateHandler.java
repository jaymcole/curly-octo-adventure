package curly.octo.game.state.handlers;

import curly.octo.game.state.AbstractStateHandler;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.game.ClientGameMode;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the PLAYING state.
 * Responsible for:
 * - Checking that all prerequisites are met (map received, player assigned)
 * - Activating the game (setting active=true, input controller setup)
 * - Handling the case where player assignment is still pending
 */
public class PlayingStateHandler extends AbstractStateHandler {

    private ClientGameMode clientGameMode;

    public PlayingStateHandler(ClientGameMode clientGameMode) {
        super(GameState.PLAYING,
              GameState.LOBBY,           // Can go back to lobby if not ready
              GameState.ERROR,           // If activation fails
              GameState.CONNECTION_LOST); // If disconnected

        this.clientGameMode = clientGameMode;
    }

    @Override
    public void onEnterState(StateContext context) {
        super.onEnterState(context);

        logAction("Entering PLAYING state - checking if game can be activated");

        // Check if we can activate immediately or need to wait
        checkAndActivateGame();
    }

    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // If game isn't active yet, keep checking if we can activate
        if (!clientGameMode.isActive()) {
            checkAndActivateGame();
        }
    }

    private void checkAndActivateGame() {
        if (clientGameMode == null) {
            logAction("Cannot activate - no client game mode");
            return;
        }

        boolean mapReceived = clientGameMode.isMapReceived();
        boolean playerAssigned = clientGameMode.isPlayerAssigned();
        boolean active = clientGameMode.isActive();

        logAction("Activation check - mapReceived: " + mapReceived +
                 ", playerAssigned: " + playerAssigned +
                 ", active: " + active);

        if (mapReceived && playerAssigned && !active) {
            logAction("All conditions met - activating game");
            activateGame();
        } else if (!mapReceived) {
            logAction("Waiting for map to be received...");
        } else if (!playerAssigned) {
            logAction("Waiting for player assignment...");
        }
    }

    private void activateGame() {
        try {
            logAction("Activating game mode");

            // Set the active flag
            clientGameMode.setActiveFlag(true);

            // Set up input controller if we have a local player
            if (clientGameMode.getGameWorld().getGameObjectManager().localPlayer != null) {
                // Ensure player physics and spawn positioning are properly set up
                // This handles both physics body creation and spawn positioning
                if (clientGameMode.getGameWorld() instanceof curly.octo.game.ClientGameWorld) {
                    logAction("Reinitializing player physics and spawn position");
                    ((curly.octo.game.ClientGameWorld) clientGameMode.getGameWorld()).reinitializePlayersAfterMapRegeneration();
                }

                clientGameMode.getInputController().setPossessionTarget(
                    clientGameMode.getGameWorld().getGameObjectManager().localPlayer);
                logAction("Input controller set up successfully");
                logAction("Game activated successfully - ready to play!");
            } else {
                logAction("Game activated but no local player yet - will complete setup when player is assigned");
            }

        } catch (Exception e) {
            Log.error("PlayingStateHandler", "Failed to activate game: " + e.getMessage());
            e.printStackTrace();
        }
    }
}