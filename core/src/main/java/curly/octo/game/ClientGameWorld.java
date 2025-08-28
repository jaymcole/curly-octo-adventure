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
import curly.octo.map.hints.MapHint;
import curly.octo.gameobjects.PlayerObject;

import java.util.ArrayList;

import java.util.Random;
import java.util.UUID;

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
        
        // Force garbage collection to ensure old physics objects are cleaned up
        // before creating new ones to prevent Bullet Physics conflicts
        Log.info("ClientGameWorld", "Forcing garbage collection before new map setup");
        System.gc();
        
        // Add a small delay to ensure cleanup is complete
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
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
        
        // Additional verification
        if (mapRenderer != null && mapManager != null) {
            Log.info("ClientGameWorld", "Map regeneration successful - renderer and manager ready");
        } else {
            Log.error("ClientGameWorld", "Map regeneration failed - missing components");
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
                ArrayList<MapHint> spawnHints = getMapManager().getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
                if (!spawnHints.isEmpty()) {
                    MapTile spawnTile = getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                    if (spawnTile != null) {
                        // Spawn above the tile, not at the tile position
                        playerStart = new Vector3(spawnTile.x, spawnTile.y + 3, spawnTile.z);
                    }
                }

                getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
            }

            // Link the PlayerObject to the physics character controller
            getGameObjectManager().localPlayer.setGameMap(getMapManager());
            getGameObjectManager().localPlayer.setCharacterController(getMapManager().getPlayerController());

            // Set spawn position
            Vector3 playerStart = new Vector3(15, 25, 15);
            ArrayList<MapHint> spawnHints = getMapManager().getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
            if (!spawnHints.isEmpty()) {
                MapTile spawnTile = getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                if (spawnTile != null) {
                    playerStart = new Vector3(spawnTile.x, spawnTile.y + 3, spawnTile.z);
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
            Vector3 bulletPlayerPos = getMapManager().getPlayerPosition();
            getGameObjectManager().localPlayer.setPosition(bulletPlayerPos);
        }

        // Update local player
        if (getGameObjectManager().localPlayer != null) {
            getGameObjectManager().update(deltaTime);
        }

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

            // Render the map with players and WorldObjects
            getMapRenderer().render(camera, getEnvironment(), getMapRenderer().getBloomFrameBuffer(), allInstances);

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
            if (mapRenderer != null) {
                Log.info("ClientGameWorld", "Disposing map renderer");
                mapRenderer.disposeAll();
                mapRenderer = null;
            }
            
            // Step 5: CRITICAL DELAY - Allow physics to settle before disposal
            Log.info("ClientGameWorld", "Waiting for physics to settle before disposal");
            try {
                Thread.sleep(100); // Give physics time to complete any ongoing operations
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Step 6: Clean up map manager (physics world, collision shapes, etc.) LAST
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
                environment.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight, 
                    0.0f, 0.0f, 0.0f, 1f));
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
                // Reset player position
                gameObjectManager.localPlayer.setPosition(spawnPosition);
                gameObjectManager.localPlayer.setYaw(spawnYaw);
                
                // Reset player state (velocity, etc.)
                gameObjectManager.localPlayer.resetPhysicsState();
                
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
            // Reinitialize local player physics with new map
            if (gameObjectManager != null && gameObjectManager.localPlayer != null && mapManager != null) {
                PlayerObject localPlayer = gameObjectManager.localPlayer;
                localPlayer.setGameMap(mapManager);
                localPlayer.setCharacterController(mapManager.getPlayerController());
                Log.info("ClientGameWorld", "Reinitialized local player physics: " + localPlayer.entityId);
            }
            
            // Reinitialize all active players with new map physics
            if (players != null && mapManager != null) {
                for (PlayerObject player : players) {
                    try {
                        player.setGameMap(mapManager);
                        if (player == gameObjectManager.localPlayer) {
                            player.setCharacterController(mapManager.getPlayerController());
                        }
                        Log.info("ClientGameWorld", "Reinitialized player physics: " + player.entityId);
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
}
