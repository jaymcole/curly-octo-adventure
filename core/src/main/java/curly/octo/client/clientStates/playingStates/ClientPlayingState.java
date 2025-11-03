package curly.octo.client.clientStates.playingStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameMode;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;

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
