package curly.octo.client.clientStates.mapTransferStates;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameWorld;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.map.GameMap;

public class MapTransferBuildAssetsState extends BaseGameStateClient {
    private boolean buildStarted = false;
    private boolean buildComplete = false;

    public MapTransferBuildAssetsState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferBuildAssetsState.class.getSimpleName());

        // Reset flags for new build
        buildStarted = false;
        buildComplete = false;

        // Start building assets on the OpenGL thread
        scheduleAssetBuilding();
    }

    private void scheduleAssetBuilding() {
        Gdx.app.postRunnable(() -> {
            try {
                Log.info("MapTransferBuildAssetsState", "Starting asset building on OpenGL thread...");

                // Get the deserialized map from reassembly state
                MapTransferReassemblyState reassemblyState = (MapTransferReassemblyState)
                    StateManager.getCachedState(MapTransferReassemblyState.class);
                GameMap receivedMap = reassemblyState.getReceivedMap();

                if (receivedMap == null) {
                    Log.error("MapTransferBuildAssetsState", "No map available from reassembly state!");
                    return;
                }

                // Get ClientGameWorld from StateManager
                ClientGameWorld clientWorld = StateManager.getClientGameWorld();
                if (clientWorld == null) {
                    Log.error("MapTransferBuildAssetsState", "ClientGameWorld not set in StateManager!");
                    return;
                }

                Log.info("MapTransferBuildAssetsState", "Building map renderer and physics...");
                clientWorld.setMap(receivedMap);

                Log.info("MapTransferBuildAssetsState", "Setting up local player...");
                clientWorld.setupLocalPlayer();

                Log.info("MapTransferBuildAssetsState", "Asset building complete!");
                buildComplete = true;

            } catch (Exception e) {
                Log.error("MapTransferBuildAssetsState", "Error building assets: " + e.getMessage());
                e.printStackTrace();
            }
        });

        buildStarted = true;
    }

    @Override
    public void updateState(float delta) {
        // Poll for build completion and transition when done
        if (buildStarted && buildComplete) {
            Log.info("MapTransferBuildAssetsState", "Assets ready, transitioning to MapTransferCompleteState");
            StateManager.setCurrentState(MapTransferCompleteState.class);
        }
    }

    @Override
    public void end() {

    }
}
