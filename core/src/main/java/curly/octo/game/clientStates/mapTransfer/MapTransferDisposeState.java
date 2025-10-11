package curly.octo.game.clientStates.mapTransfer;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.ClientGameWorld;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.game.clientStates.mapTransfer.ui.MapTransferScreen;

public class MapTransferDisposeState extends BaseGameStateClient {
    private boolean disposalScheduled = false;
    private boolean disposalComplete = false;

    public MapTransferDisposeState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage("Disposing old map...");
        Log.info("MapTransferDisposeState", "Starting old map disposal");

        // Reset flags
        disposalScheduled = false;
        disposalComplete = false;

        ClientGameWorld clientWorld = StateManager.getClientGameWorld();
        if (clientWorld != null && clientWorld.getMapManager() != null) {
            // Schedule disposal on OpenGL thread to avoid context errors
            Gdx.app.postRunnable(() -> {
                try {
                    Log.info("MapTransferDisposeState", "Disposing old map on OpenGL thread");
                    clientWorld.cleanupForMapRegeneration();
                    Log.info("MapTransferDisposeState", "Old map disposal complete");
                    disposalComplete = true;
                } catch (Exception e) {
                    Log.error("MapTransferDisposeState", "Error disposing old map: " + e.getMessage());
                    e.printStackTrace();
                    disposalComplete = true; // Mark complete even on error to avoid hanging
                }
            });
            disposalScheduled = true;
            Log.info("MapTransferDisposeState", "Old map disposal scheduled on OpenGL thread");
        } else {
            Log.info("MapTransferDisposeState", "No old map to dispose");
            disposalComplete = true; // Nothing to dispose
        }
    }

    @Override
    public void updateState(float delta) {
        // Wait for disposal to complete, then transition to transfer state
        if (disposalScheduled && disposalComplete) {
            Log.info("MapTransferDisposeState", "Disposal complete, transitioning to transfer state");
            StateManager.setCurrentState(MapTransferTransferState.class);
        } else if (!disposalScheduled && disposalComplete) {
            // No disposal needed, transition immediately
            Log.info("MapTransferDisposeState", "No disposal needed, transitioning to transfer state");
            StateManager.setCurrentState(MapTransferTransferState.class);
        }
    }

    @Override
    public void end() {
        // Reset flags for next time
        disposalScheduled = false;
        disposalComplete = false;
    }
}
