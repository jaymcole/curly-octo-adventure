package curly.octo.game.clientStates.playing;

import com.esotericsoftware.minlog.Log;
import curly.octo.game.ClientGameMode;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;

public class ClientPlayingState extends BaseGameStateClient {
    public ClientPlayingState(BaseScreen screen) {
        super(screen);
        this.renderGameInBackground = true;
    }

    @Override
    public void start() {
        Log.info("ClientPlayingState", "Entering playing state - enabling gameplay");

        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null) {
            // Enable player input (movement, camera, etc.)
            clientGameMode.enableInput();

            // Resume network position updates
            clientGameMode.resumeNetworkUpdates();

            // Set the active flag so ClientGameMode renders the game
            clientGameMode.setActiveFlag(true);

            Log.info("ClientPlayingState", "Gameplay enabled - player can now play");
        } else {
            Log.error("ClientPlayingState", "ClientGameMode not set in StateManager - cannot enable gameplay");
        }
    }

    @Override
    public void updateState(float delta) {
        // Game updates happen in ClientGameMode.update() and ClientGameWorld.update()
        // This is called from Main.render() when gameIsPlaying is true
    }

    @Override
    public void end() {
        Log.info("ClientPlayingState", "Exiting playing state - disabling gameplay");

        ClientGameMode clientGameMode = StateManager.getClientGameMode();
        if (clientGameMode != null) {
            // Disable player input
            clientGameMode.disableInput();

            Log.info("ClientPlayingState", "Gameplay disabled");
        }
    }
}
