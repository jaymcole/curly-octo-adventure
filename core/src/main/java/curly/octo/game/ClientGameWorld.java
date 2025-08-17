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
import curly.octo.gameobjects.PlayerObject;

import java.util.Random;

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
        if (getGameObjectManager().localPlayer == null) {
            Log.info("ClientGameWorld", "Creating local player object");
            getGameObjectManager().localPlayer = new PlayerObject("localPlayer");
            
            // Ensure graphics are initialized for local player immediately
            Log.info("ClientGameWorld", "Waiting for graphics initialization...");
            long startTime = System.currentTimeMillis();
            while (!getGameObjectManager().localPlayer.isGraphicsInitialized() && (System.currentTimeMillis() - startTime) < 5000) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (getGameObjectManager().localPlayer.isGraphicsInitialized()) {
                Log.info("ClientGameWorld", "Graphics initialized successfully");
            } else {
                Log.error("ClientGameWorld", "Graphics initialization timed out!");
            }
            
            getGameObjectManager().add(getGameObjectManager().localPlayer);
            getPlayers().add(getGameObjectManager().localPlayer);
            Log.info("ClientGameWorld", "Local player object created with ID: " + getGameObjectManager().localPlayer.getPlayerId() + " and added to players list");
        }

        if (getMapManager() != null) {
            // Add player to physics world
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 25, 15);
            if (!getMapManager().spawnTiles.isEmpty()) {
                MapTile spawnTile = getMapManager().spawnTiles.get(0);
                // Spawn above the tile, not at the tile position
                playerStart = new Vector3(spawnTile.x, spawnTile.y + 3, spawnTile.z);
            }

            getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);

            // Link the PlayerObject to the physics character controller
            getGameObjectManager().localPlayer.setGameMap(getMapManager());
            getGameObjectManager().localPlayer.setCharacterController(getMapManager().getPlayerController());
            getGameObjectManager().localPlayer.setPosition(new Vector3(playerStart.x, playerStart.y, playerStart.z));

            Log.info("ClientGameWorld", "Setup local player at position: " + playerStart);
        }
    }

    @Override
    public void update(float deltaTime) {
        // Update physics
        if (getMapManager() != null && getGameObjectManager().localPlayer != null) {
            getMapManager().stepPhysics(deltaTime);
            Vector3 bulletPlayerPos = getMapManager().getPlayerPosition();
            getGameObjectManager().localPlayer.setPosition(bulletPlayerPos);
        }

        // Update local player
        if (getGameObjectManager().localPlayer != null) {
            getGameObjectManager().update(deltaTime);
        }

        // Update position update timer
        incrementPositionUpdateTimer(deltaTime);
    }

    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (getMapRenderer() != null && camera != null) {
            // Set post-processing effect based on local player's current tile
            if (getGameObjectManager().localPlayer != null) {
                getMapRenderer().setPostProcessingEffect(getGameObjectManager().localPlayer.getCurrentTileFillType());
            }

            // Step 1: Render scene with bloom effects first
            getMapRenderer().beginBloomRender();

            // Collect other players' ModelInstances for shadow casting
            Array<ModelInstance> playerInstances = new Array<>();
            if (getGameObjectManager().activePlayers != null && getGameObjectManager().localPlayer != null) {
                for (PlayerObject player : getGameObjectManager().activePlayers) {
                    if (!player.getPlayerId().equals(getGameObjectManager().localPlayer.getPlayerId())) {
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
            if (getGameObjectManager().localPlayer != null &&
                getGameObjectManager().localPlayer.getCurrentTileFillType() != MapTileFillType.AIR) {

                // Apply post-processing overlay to the current screen (with bloom)
                getMapRenderer().applyPostProcessingToScreen();
            }
        }
    }

    public void resize(int width, int height) {
        if (getMapRenderer() != null) {
            getMapRenderer().resize(width, height);
        }
    }
}