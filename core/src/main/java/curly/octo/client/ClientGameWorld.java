package curly.octo.client;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.GL20;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameWorld;
import curly.octo.common.map.GameMap;
import curly.octo.client.rendering.GameMapRenderer;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.PlayerObject;
import curly.octo.common.map.hints.SpawnPointHint;

import java.util.ArrayList;

import java.util.Random;
import java.util.UUID;

import static com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight;

/**
 * Client-specific game world that handles client-side rendering and physics.
 * Includes full graphics, physics simulation, and player interaction.
 */
public class ClientGameWorld extends GameWorld {

    // Flag to temporarily disable physics during regeneration
    private volatile boolean physicsDisabled = false;

    public ClientGameWorld(Random random) {
        super(random, false); // false = full client mode with graphics
        Log.info("ClientGameWorld", "Created client game world");
    }

    @Override
    public void setMap(GameMap map) {
        Log.info("ClientGameWorld", "Setting new map - current mapRenderer: " +
                (mapRenderer != null ? "exists" : "null") +
                ", current mapManager: " + (mapManager != null ? "exists" : "null"));

        // Call parent setMap which should recreate everything
        try {
            Log.info("ClientGameWorld", "Calling parent setMap to initialize new map");
            super.setMap(map);
            Log.info("ClientGameWorld", "Parent setMap completed successfully");
        } catch (Exception e) {
            Log.error("ClientGameWorld", "ERROR in setMap: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to prevent silent failures
        }

        Log.info("ClientGameWorld", "Map set complete - new mapRenderer: " +
                (mapRenderer != null ? "created" : "still null") +
                ", new mapManager: " + (mapManager != null ? "set" : "still null"));

        // Re-enable physics after successful map setup
        physicsDisabled = false;
        Log.info("ClientGameWorld", "Physics RE-ENABLED after map setup");

        // Additional verification and recovery
        if (mapRenderer != null && mapManager != null) {
            Log.info("ClientGameWorld", "Map regeneration successful - renderer and manager ready");

            // CRITICAL: Verify renderer is properly initialized for rendering
            try {
                // Force renderer to prepare for first render to catch any initialization issues
                if (mapRenderer.getBloomFrameBuffer() == null) {
                    Log.warn("ClientGameWorld", "Renderer bloom buffer null, forcing re-initialization");
                    // Trigger renderer resize to ensure proper OpenGL resource creation
                    com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                        if (mapRenderer != null) {
                            int width = com.badlogic.gdx.Gdx.graphics.getWidth();
                            int height = com.badlogic.gdx.Gdx.graphics.getHeight();
                            Log.info("ClientGameWorld", "Forcing renderer resize to fix initialization: " + width + "x" + height);
                            mapRenderer.resize(width, height);
                        }
                    });
                }
            } catch (Exception e) {
                Log.error("ClientGameWorld", "Error verifying renderer state: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            Log.error("ClientGameWorld", "Map regeneration failed - missing components");
            Log.error("ClientGameWorld", "MapRenderer: " + (mapRenderer != null ? "OK" : "NULL"));
            Log.error("ClientGameWorld", "MapManager: " + (mapManager != null ? "OK" : "NULL"));

            // CRITICAL: Attempt recovery by forcing re-initialization
            if (mapRenderer == null && mapManager != null) {
                Log.warn("ClientGameWorld", "Attempting to recover missing renderer");
                try {
                    // Force renderer creation on OpenGL thread
                    com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                        try {
                            Log.info("ClientGameWorld", "Creating emergency renderer for map recovery");

                            // Manually create renderer since automatic creation failed
                            mapRenderer = new GameMapRenderer(gameObjectManager);
                            Log.info("ClientGameWorld", "Emergency renderer created manually");

                            if (mapRenderer != null) {
                                Log.info("ClientGameWorld", "Emergency renderer creation successful");

                                // CRITICAL: Ensure proper initialization on OpenGL thread
                                int width = com.badlogic.gdx.Gdx.graphics.getWidth();
                                int height = com.badlogic.gdx.Gdx.graphics.getHeight();
                                Log.info("ClientGameWorld", "Initializing emergency renderer: " + width + "x" + height);
                                mapRenderer.resize(width, height);

                                // Additional initialization steps to prevent black screen
                                try {
                                    // Force OpenGL state reset by clearing screen
                                    com.badlogic.gdx.Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

                                    // Reset viewport to ensure proper rendering area
                                    com.badlogic.gdx.Gdx.gl.glViewport(0, 0, width, height);

                                    Log.info("ClientGameWorld", "Emergency renderer OpenGL state reset");
                                } catch (Exception e) {
                                    Log.warn("ClientGameWorld", "Could not reset OpenGL state: " + e.getMessage());
                                }

                            } else {
                                Log.error("ClientGameWorld", "Emergency renderer creation failed");
                            }
                        } catch (Exception e) {
                            Log.error("ClientGameWorld", "Error in emergency renderer creation: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    Log.error("ClientGameWorld", "Error scheduling emergency renderer creation: " + e.getMessage());
                }
            }
        }
    }

    public void setupLocalPlayer() {
        if (getGameObjectManager().localPlayer == null) {
            Log.info("ClientGameWorld", "Creating local player object");
            getGameObjectManager().localPlayer = new PlayerObject(UUID.randomUUID().toString());

            // Graphics initialization happens asynchronously on OpenGL thread
            Log.info("ClientGameWorld", "Graphics initialization scheduled for local player");

            getGameObjectManager().add(getGameObjectManager().localPlayer);
            getPlayers().add(getGameObjectManager().localPlayer);
            Log.info("ClientGameWorld", "Local player object created with ID: " + getGameObjectManager().localPlayer.entityId + " and added to players list");
        }

        if (getMapManager() != null) {
            // Check if player physics is already set up
            if (getMapManager().getPlayerController() == null) {
                // Add player to physics world only if not already added
                float playerRadius = 1.0f;
                float playerHeight = 5.0f;
                float playerMass = 10.0f;
                Vector3 playerStart = new Vector3(15, 25, 15);
                ArrayList<MapHint> spawnHints = getMapManager().getAllHintsOfType(SpawnPointHint.class);
                if (!spawnHints.isEmpty()) {
                    MapTile spawnTile = getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                    if (spawnTile != null) {
                        // Spawn directly on the tile - physics will handle proper ground positioning
                        playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
                    }
                }

                getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
            }

            // Link the PlayerObject to the physics character controller
            getGameObjectManager().localPlayer.setGameMap(getMapManager());
            getGameObjectManager().localPlayer.setCharacterController(getMapManager().getPlayerController());

            // Set spawn position
            Vector3 playerStart = new Vector3(15, 25, 15);
            ArrayList<MapHint> spawnHints = getMapManager().getAllHintsOfType(SpawnPointHint.class);
            if (!spawnHints.isEmpty()) {
                MapTile spawnTile = getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                if (spawnTile != null) {
                    playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
                }
            }
            getGameObjectManager().localPlayer.setPosition(new Vector3(playerStart.x, playerStart.y, playerStart.z));
        }
    }

    @Override
    public void update(float deltaTime) {
        // Skip all physics updates during regeneration to prevent crashes
        if (physicsDisabled) {
            return; // Skip everything until map regeneration is complete
        }

        // Update physics
        if (getMapManager() != null && getGameObjectManager().localPlayer != null) {
            getMapManager().stepPhysics(deltaTime);

            // Only sync physics position if NOT in fly mode
            if (!getGameObjectManager().localPlayer.isFlyModeEnabled()) {
                Vector3 bulletPlayerPos = getMapManager().getPlayerPosition();
                getGameObjectManager().localPlayer.setPosition(bulletPlayerPos);
            }
        }

        // Update local player
        if (getGameObjectManager().localPlayer != null) {
            getGameObjectManager().update(deltaTime);
        }

    }

    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        // Store local reference to prevent null pointer if renderer is disposed during rendering
        GameMapRenderer renderer = getMapRenderer();

        if (renderer != null && camera != null) {
            // Set post-processing effect based on local player's head/camera tile (not feet)
            if (getGameObjectManager().localPlayer != null) {
                renderer.setPostProcessingEffect(getGameObjectManager().localPlayer.getHeadTileFillType());
            }

            // DEBUG: DISABLE BLOOM TEMPORARILY TO TEST WATER SHADER
            // Step 1: Render scene with bloom effects first
            // renderer.beginBloomRender();  // DISABLED FOR DEBUG

            // Collect other players' ModelInstances for shadow casting
            Array<ModelInstance> playerInstances = new Array<>();
            if (getGameObjectManager().activePlayers != null && getGameObjectManager().localPlayer != null) {
                for (PlayerObject player : getGameObjectManager().activePlayers) {
                    if (!player.entityId.equals(getGameObjectManager().localPlayer.entityId)) {
                        // Get the player's ModelInstance for shadow casting
                        ModelInstance playerModel = player.getModelInstance();
                        if (playerModel != null) {
                            // Don't manually position here - PlayerObject.update() handles this with bounds-aware positioning
                            playerInstances.add(playerModel);
                        }
                    }
                }
            }

            // Collect all WorldObjects from GameObjectManager
            Array<ModelInstance> allInstances = new Array<>(playerInstances);
            allInstances.addAll(getGameObjectManager().getRenderQueue());

            // DEBUG: Render directly to screen, no framebuffer
            // Render the map with players and WorldObjects
            renderer.render(camera, getEnvironment(), null, allInstances);  // null = render to screen

            // Render physics debug information if enabled
            if (getMapManager() != null) {
                getMapManager().renderPhysicsDebug(camera);
            }

            // Apply post-processing effects to the rendered scene
            // Only apply post-processing if we have an effect to apply
            if (getGameObjectManager().localPlayer != null &&
                getGameObjectManager().localPlayer.getHeadTileFillType() != MapTileFillType.AIR) {

                // Apply post-processing overlay to the current screen
                renderer.applyPostProcessingToScreen();
            }
        }
    }

    public void resize(int width, int height) {
        if (getMapRenderer() != null) {
            getMapRenderer().resize(width, height);
        }
    }

    @Override
    public void regenerateMap(long newSeed) {
        Log.info("ClientGameWorld", "Client-side map regeneration not implemented - " +
                 "clients receive new maps from server via network transfer");
        // Clients don't generate their own maps - they receive them from the server
        // The actual cleanup and map replacement happens in the network listeners
    }

    /**
     * Performs complete resource cleanup for map regeneration.
     * This method cleans up all resources associated with the current map
     * before a new one is loaded.
     */
    public void cleanupForMapRegeneration() {
        Log.info("ClientGameWorld", "Starting complete resource cleanup for map regeneration");

        try {
            // Step 1: CRITICAL - Disable all physics updates before cleanup
            physicsDisabled = true;
            Log.info("ClientGameWorld", "Physics DISABLED for safe cleanup");

            if (mapManager != null) {
                Log.info("ClientGameWorld", "Preparing to safely remove all physics references");
            }

            // Step 2: Remove all player physics safely BEFORE disposing world
            if (gameObjectManager != null && gameObjectManager.localPlayer != null) {
                Log.info("ClientGameWorld", "Safely removing local player from physics world");
                try {
                    // Remove player from physics world first, then reset state
                    if (mapManager != null && mapManager.getPlayerController() != null) {
                        gameObjectManager.localPlayer.setCharacterController(null);
                        gameObjectManager.localPlayer.setGameMap(null);
                    }
                    gameObjectManager.localPlayer.resetPhysicsState();
                } catch (Exception e) {
                    Log.error("ClientGameWorld", "Error safely removing local player physics: " + e.getMessage());
                }
            }

            // Step 3: Remove all player physics but keep player data
            if (players != null) {
                Log.info("ClientGameWorld", "Safely removing physics for " + players.size() + " player objects");
                for (PlayerObject player : players) {
                    try {
                        // Remove from physics world first, then reset
                        if (mapManager != null) {
                            player.setCharacterController(null);
                            player.setGameMap(null);
                        }
                        player.resetPhysicsState(); // Reset physics but preserve player
                        Log.info("ClientGameWorld", "Safely removed physics for player " + player.entityId);
                    } catch (Exception e) {
                        Log.error("ClientGameWorld", "Error safely removing physics for player " + player.entityId + ": " + e.getMessage());
                    }
                }
                // DON'T clear players - we want to keep them for the new map
                Log.info("ClientGameWorld", "Preserved " + players.size() + " player objects for new map");
            }

            // Step 4: Clean up map renderer (textures, models, shaders, etc.)
            // NOTE: This should only be called from OpenGL thread (via MapTransferDisposeState)
            if (mapRenderer != null) {
                Log.info("ClientGameWorld", "Disposing map renderer");
                try {
                    mapRenderer.disposeAll();
                    mapRenderer = null;

                    // Force OpenGL state cleanup after disposal
                    com.badlogic.gdx.Gdx.gl.glFlush();
                    com.badlogic.gdx.Gdx.gl.glFinish();

                    Log.info("ClientGameWorld", "Renderer disposal completed successfully");
                } catch (Exception e) {
                    Log.error("ClientGameWorld", "Error during renderer disposal: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Step 5: Clean up map manager (physics world, collision shapes, etc.)
            // Physics is already disabled via physicsDisabled flag, safe to dispose immediately
            if (mapManager != null) {
                Log.info("ClientGameWorld", "Disposing map manager safely - ALL REFERENCES REMOVED");
                try {
                    mapManager.dispose();
                    Log.info("ClientGameWorld", "Map manager disposed successfully");
                } catch (Exception e) {
                    Log.error("ClientGameWorld", "Error disposing map manager: " + e.getMessage());
                    e.printStackTrace();
                }
                mapManager = null;
            }

            // Step 7: Clear old map lights but preserve players
            if (gameObjectManager != null) {
                // Clear all lights from the old map
                gameObjectManager.clearAllLights();

                // DON'T clear activePlayers or set localPlayer to null - we want to keep them
                Log.info("ClientGameWorld", "Preserved game object manager with " +
                        gameObjectManager.activePlayers.size() + " active players");
                if (gameObjectManager.localPlayer != null) {
                    Log.info("ClientGameWorld", "Local player preserved: " + gameObjectManager.localPlayer.entityId);
                }
            }

            // Step 8: Reset environment to clear any residual lighting state
            if (environment != null) {
                Log.info("ClientGameWorld", "Resetting environment for map regeneration");
                environment.clear();
                // Re-setup basic environment (ambient light)
                environment.set(new ColorAttribute(AmbientLight, 0.0f, 0.0f, 0.0f, 1f));
            }

            // Step 9: Force garbage collection to clean up disposed resources
            System.gc();

            Log.info("ClientGameWorld", "Resource cleanup completed successfully");

        } catch (Exception e) {
            Log.error("ClientGameWorld", "Error during resource cleanup: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - we want to continue with map loading even if cleanup had issues
        }
    }

    /**
     * Resets the local player to a new spawn position after map regeneration.
     *
     * @param spawnPosition New spawn position
     * @param spawnYaw New spawn orientation
     */
    public void resetLocalPlayerToSpawn(Vector3 spawnPosition, float spawnYaw) {
        Log.info("ClientGameWorld", "Resetting local player to spawn position: " + spawnPosition);

        try {
            if (gameObjectManager != null && gameObjectManager.localPlayer != null) {
                PlayerObject localPlayer = gameObjectManager.localPlayer;

                // CRITICAL: Always recreate physics body to ensure clean state
                if (mapManager != null) {
                    try {
                        // Get current physics position for logging
                        Vector3 currentPhysicsPos = new Vector3(0, 0, 0);
                        if (mapManager.getPlayerController() != null) {
                            currentPhysicsPos = mapManager.getPlayerPosition();
                        }
                        Log.info("ClientGameWorld", "Current physics position: " + currentPhysicsPos + ", target spawn: " + spawnPosition);

                        // Always recreate physics body at spawn position for clean state
                        // addPlayer() automatically removes old player physics first
                        Log.info("ClientGameWorld", "Recreating physics body to ensure clean state");

                        float playerRadius = 1.0f;
                        float playerHeight = 5.0f;
                        float playerMass = 10.0f;

                        // This will clean up old physics and create new at spawn position
                        mapManager.addPlayer(spawnPosition.x, spawnPosition.y, spawnPosition.z,
                                           playerRadius, playerHeight, playerMass);

                        // Relink character controller
                        localPlayer.setCharacterController(mapManager.getPlayerController());

                        Log.info("ClientGameWorld", "Recreated physics body at spawn position");

                    } catch (Exception e) {
                        Log.error("ClientGameWorld", "Error recreating physics body: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // Reset PlayerObject position and state
                localPlayer.setPosition(spawnPosition);
                localPlayer.setYaw(spawnYaw);
                localPlayer.resetPhysicsState();

                Log.info("ClientGameWorld", "Local player reset to spawn successfully");
            } else {
                Log.warn("ClientGameWorld", "Cannot reset player - local player is null");
            }

        } catch (Exception e) {
            Log.error("ClientGameWorld", "Failed to reset local player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reinitializes player physics after map regeneration.
     * This should be called after the new map is loaded to restore player physics integration.
     */
    public void reinitializePlayersAfterMapRegeneration() {
        Log.info("ClientGameWorld", "Reinitializing player physics for new map");

        try {
            // IMPORTANT: Give physics world time to fully initialize collision geometry
            try {
                Thread.sleep(200); // Allow collision geometry to settle
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Reinitialize local player physics with new map
            if (gameObjectManager != null && gameObjectManager.localPlayer != null && mapManager != null) {
                PlayerObject localPlayer = gameObjectManager.localPlayer;

                // CRITICAL: Recreate the physics body in the new physics world
                if (mapManager.getPlayerController() == null) {
                    float playerRadius = 1.0f;
                    float playerHeight = 5.0f;
                    float playerMass = 10.0f;

                    // Use a safe spawn position instead of current position (which might be falling)
                    Vector3 safeSpawnPos = getSafeSpawnPosition();
                    Log.info("ClientGameWorld", "Recreating player physics body at safe spawn position: " + safeSpawnPos);

                    // Create new physics body in the new physics world
                    mapManager.addPlayer(safeSpawnPos.x, safeSpawnPos.y, safeSpawnPos.z, playerRadius, playerHeight, playerMass);

                    // CRITICAL: Reset player state to prevent falling/invalid physics state
                    localPlayer.resetPhysicsState();
                    localPlayer.setPosition(safeSpawnPos);

                    Log.info("ClientGameWorld", "Created new player physics body for: " + localPlayer.entityId);
                } else {
                    Log.info("ClientGameWorld", "Player controller already exists, reusing existing physics body");
                }

                // Link the PlayerObject to the physics character controller
                localPlayer.setGameMap(mapManager);
                localPlayer.setCharacterController(mapManager.getPlayerController());

                // Additional safety: Step physics once to ensure proper initialization
                if (mapManager != null) {
                    mapManager.stepPhysics(0.016f); // One frame step
                }

                Log.info("ClientGameWorld", "Reinitialized local player physics: " + localPlayer.entityId);
            }

            // For remote players, just link them to the map (they don't need physics bodies on client)
            if (players != null && mapManager != null) {
                for (PlayerObject player : players) {
                    try {
                        if (player != gameObjectManager.localPlayer) {
                            // Remote players only need map reference, not physics bodies
                            player.setGameMap(mapManager);
                            Log.info("ClientGameWorld", "Reinitialized remote player map reference: " + player.entityId);
                        }
                    } catch (Exception e) {
                        Log.error("ClientGameWorld", "Error reinitializing player " + player.entityId + ": " + e.getMessage());
                    }
                }
            }

            Log.info("ClientGameWorld", "Player physics reinitialization completed");

        } catch (Exception e) {
            Log.error("ClientGameWorld", "Error during player physics reinitialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets a safe spawn position for player reinitialization.
     * Uses spawn hints if available, otherwise a high fallback position.
     */
    private Vector3 getSafeSpawnPosition() {
        Vector3 safePos = new Vector3(15, 25, 15); // High fallback position

        if (mapManager != null) {
            try {
                ArrayList<MapHint> spawnHints = mapManager.getAllHintsOfType(SpawnPointHint.class);
                if (!spawnHints.isEmpty()) {
                    MapTile spawnTile = mapManager.getTile(spawnHints.get(0).tileLookupKey);
                    if (spawnTile != null) {
                        // Use spawn position well above the tile
                        safePos = new Vector3(spawnTile.x, spawnTile.y + 3f, spawnTile.z); // Minimal height for safety
                        Log.info("ClientGameWorld", "Using spawn hint position: " + safePos);
                    }
                }
            } catch (Exception e) {
                Log.warn("ClientGameWorld", "Could not get spawn hint, using fallback position: " + e.getMessage());
            }
        }

        return safePos;
    }
}
