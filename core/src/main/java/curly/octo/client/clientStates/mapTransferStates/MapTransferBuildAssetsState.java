package curly.octo.client.clientStates.mapTransferStates;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.ClientGameWorld;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.GameObject;
import curly.octo.common.character.WalkingCharacter;
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

                // Clear ALL existing objects before adding new ones from transfer
                // This prevents accumulation of old players/objects and duplicates
                clientWorld.getGameObjectManager().clearAllObjects();
                Log.info("MapTransferBuildAssetsState", "Cleared all existing objects before receiving new transfer payload");

                // Add game objects from transfer
                if (receivedGameObjects == null) {
                    Log.error("MapTransferBuildAssetsState", "receivedGameObjects is NULL!");
                } else {
                    Log.info("MapTransferBuildAssetsState", "Adding " + receivedGameObjects.size() +
                            " game objects to GameObjectManager...");
                    // Add all received game objects to the client's GameObjectManager
                    for (GameObject obj : receivedGameObjects) {
                        Log.info("MapTransferBuildAssetsState", "About to add " + obj.getClass().getSimpleName() +
                                " with ID: " + obj.entityId);
                        clientWorld.getGameObjectManager().add(obj);
                        Log.info("MapTransferBuildAssetsState", "Successfully added " + obj.getClass().getSimpleName() +
                                " with ID: " + obj.entityId);
                    }
                }

                // Restore local player reference and setup physics
                curly.octo.client.ClientGameMode clientGameMode = StateManager.getClientGameMode();
                if (clientGameMode != null) {
                    String localPlayerId = clientGameMode.getLocalPlayerId();
                    if (localPlayerId != null) {
                        GameObject obj = clientWorld.getGameObjectManager().getObjectById(localPlayerId);
                        if (obj instanceof WalkingCharacter) {
                            WalkingCharacter localPlayer = (WalkingCharacter) obj;
                            clientWorld.getGameObjectManager().localPlayer = localPlayer;
                            Log.info("MapTransferBuildAssetsState", "Restored local player reference: " + localPlayerId);

                            // Ensure physics is set up for the local player
                            if (localPlayer.getCharacterController() == null) {
                                Log.info("MapTransferBuildAssetsState", "Setting up physics for restored local player...");
                                clientGameMode.setupPlayerPhysics(localPlayer);
                            }

                            // CRITICAL: Re-possess the new player object instance
                            // The InputController might be holding a reference to the old (now cleared) player object
                            if (clientGameMode.getInputController() != null) {
                                Log.info("MapTransferBuildAssetsState", "Re-possessing local player to update input controller reference");
                                clientGameMode.getInputController().setPossessionTarget(localPlayer);
                            }
                        } else {
                            Log.warn("MapTransferBuildAssetsState", "Local player ID " + localPlayerId + " not found in received objects!");
                        }
                    } else {
                        Log.info("MapTransferBuildAssetsState", "No local player ID assigned yet.");
                    }
                } else {
                    Log.warn("MapTransferBuildAssetsState", "ClientGameMode not available in StateManager");
                }

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
