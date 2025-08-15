package curly.octo.game;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.player.PlayerController;

import java.util.Random;

import static curly.octo.player.PlayerUtilities.createPlayerController;

/**
 * Client-specific game world that handles client-side rendering and physics.
 * Includes full graphics, physics simulation, and player interaction.
 */
public class ClientGameWorld extends GameWorld {

    public ClientGameWorld(Random random) {
        super(random, false); // false = full client mode with graphics
        Log.info("ClientGameWorld", "Created client game world");
    }

    @Override
    public void setMap(GameMap map) {
        super.setMap(map);
        Log.info("ClientGameWorld", "Set map from network");
    }

    public void setupLocalPlayer() {
        if (getGameObjectManager().localPlayerController == null) {
            Log.info("ClientGameWorld", "Creating local player controller");
            getGameObjectManager().localPlayerController = createPlayerController();
            getPlayers().add(getGameObjectManager().localPlayerController);
            Log.info("ClientGameWorld", "Local player controller created with ID: " + getGameObjectManager().localPlayerController.getPlayerId() + " and added to players list");
        }

        if (getMapManager() != null) {
            // Add player to physics world
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 25, 15);
            if (!getMapManager().spawnTiles.isEmpty()) {
                MapTile spawnTile = getMapManager().spawnTiles.get(0);
                playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
            }

            getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);

            getGameObjectManager().localPlayerController.setGameMap(getMapManager());
            getGameObjectManager().localPlayerController.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z, 0);

            Log.info("ClientGameWorld", "Setup local player at position: " + playerStart);
        }
    }

    @Override
    public void update(float deltaTime) {
        // Update physics
        if (getMapManager() != null && getGameObjectManager().localPlayerController != null) {
            getMapManager().stepPhysics(deltaTime);
            Vector3 bulletPlayerPos = getMapManager().getPlayerPosition();
            getGameObjectManager().localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z, deltaTime);
        }

        // Update local player
        if (getGameObjectManager().localPlayerController != null) {
            getGameObjectManager().update(deltaTime);
        }

        // Update position update timer
        incrementPositionUpdateTimer(deltaTime);
    }

    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (getMapRenderer() != null && camera != null) {
            // Set post-processing effect based on local player's current tile
            if (getGameObjectManager().localPlayerController != null) {
                getMapRenderer().setPostProcessingEffect(getGameObjectManager().localPlayerController.getCurrentTileFillType());
            }

            // Step 1: Render scene with bloom effects first
            getMapRenderer().beginBloomRender();

            // Collect other players' ModelInstances for shadow casting
            Array<ModelInstance> playerInstances = new Array<>();
            if (getGameObjectManager().activePlayers != null && getGameObjectManager().localPlayerController != null) {
                for (PlayerController player : getGameObjectManager().activePlayers) {
                    if (!player.getPlayerId().equals(getGameObjectManager().localPlayerController.getPlayerId())) {
                        // Get the player's ModelInstance for shadow casting
                        ModelInstance playerModel = player.getModelInstance();
                        if (playerModel != null) {
                            // Update the model position to match player position
                            Vector3 playerPos = player.getPosition();
                            playerModel.transform.idt();
                            playerModel.transform.setToTranslation(playerPos.x, playerPos.y + 2.5f, playerPos.z);
                            playerInstances.add(playerModel);
                        }
                    }
                }
            }

            // Render the map with other players included in shadow casting
            getMapRenderer().render(camera, getEnvironment(), getMapRenderer().getBloomFrameBuffer(), playerInstances);

            // Render physics debug information if enabled
            if (getMapManager() != null) {
                getMapManager().renderPhysicsDebug(camera);
            }

            // End bloom render (this renders bloom result to screen)
            getMapRenderer().endBloomRender();

            // Step 2: Apply post-processing effects to the bloom result
            // Only apply post-processing if we have an effect to apply
            if (getGameObjectManager().localPlayerController != null &&
                getGameObjectManager().localPlayerController.getCurrentTileFillType() != MapTileFillType.AIR) {

                // Apply post-processing overlay to the current screen (with bloom)
                getMapRenderer().applyPostProcessingToScreen();
            }
        }
    }

    public void resize(int width, int height) {
        if (getMapRenderer() != null) {
            getMapRenderer().resize(width, height);
        }
        if (getGameObjectManager().localPlayerController != null) {
            getGameObjectManager().localPlayerController.resize(width, height);
        }
    }
}