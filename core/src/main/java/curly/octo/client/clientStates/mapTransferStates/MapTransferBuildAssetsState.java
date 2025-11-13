package curly.octo.client.clientStates.mapTransferStates;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameWorld;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.GameObject;
import curly.octo.common.map.GameMap;

import java.util.List;

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

                // Get the deserialized map and game objects from reassembly state
                MapTransferReassemblyState reassemblyState = (MapTransferReassemblyState)
                    StateManager.getCachedState(MapTransferReassemblyState.class);
                GameMap receivedMap = reassemblyState.getReceivedMap();
                List<GameObject> receivedGameObjects = reassemblyState.getReceivedGameObjects();

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

                // Check if local player needs physics setup now that map is loaded
                // This handles the race condition where PlayerAssignmentUpdate arrives before map is ready
                curly.octo.client.ClientGameMode clientGameMode = StateManager.getClientGameMode();
                if (clientGameMode != null) {
                    curly.octo.common.PlayerObject localPlayer = clientGameMode.getLocalPlayer();
                    if (localPlayer != null) {
                        Log.info("MapTransferBuildAssetsState", "Local player exists after map load - checking physics setup...");
                        // Check if physics needs to be set up (characterController will be null if physics wasn't initialized)
                        if (localPlayer.getCharacterController() == null) {
                            Log.info("MapTransferBuildAssetsState", "Local player has no physics controller - setting up now...");
                            clientGameMode.setupPlayerPhysics(localPlayer);
                        } else {
                            Log.info("MapTransferBuildAssetsState", "Local player physics already initialized");
                        }
                    } else {
                        Log.info("MapTransferBuildAssetsState", "No local player assigned yet - physics will be set up when player is assigned");
                    }
                } else {
                    Log.warn("MapTransferBuildAssetsState", "ClientGameMode not available in StateManager");
                }

                // Clear existing players before adding new ones from transfer
                // This prevents accumulation of old players with new players
                clientWorld.getGameObjectManager().activePlayers.clear();
                Log.info("MapTransferBuildAssetsState", "Cleared existing players before receiving new transfer payload");

                Log.info("MapTransferBuildAssetsState", "Adding " + receivedGameObjects.size() +
                        " game objects to GameObjectManager...");
                // Add all received game objects to the client's GameObjectManager
                for (GameObject obj : receivedGameObjects) {
                    clientWorld.getGameObjectManager().add(obj);
                    Log.info("MapTransferBuildAssetsState", "Added " + obj.getClass().getSimpleName() +
                            " with ID: " + obj.entityId);
                }

                // Don't create local player here - it's already in the transfer payload
                // Will be assigned when server sends PlayerAssignmentUpdate
                Log.info("MapTransferBuildAssetsState", "Received " + receivedGameObjects.size() +
                        " game objects from transfer (players will be assigned by server)");

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
