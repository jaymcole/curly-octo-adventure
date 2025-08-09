package curly.octo.game;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.GameMapRenderer;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.player.PlayerController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static curly.octo.player.PlayerUtilities.createPlayerController;

/**
 * Shared game world that contains the map, physics, and environment.
 * This is used by both server and client game modes.
 */
public class GameWorld {

    private GameMap mapManager;
    private GameMapRenderer mapRenderer;
    private Environment environment;
    private Array<PointLight> dungeonLights;
    private PointLight playerLantern;
    private List<PlayerController> players;
    private long localPlayerId;
    private Random random;
    private GameObjectManager gameObjectManager;

    // Physics update timer
    private float positionUpdateTimer = 0;
    private static final float POSITION_UPDATE_INTERVAL = 1/60f; // 60 updates per second
    private boolean disposed = false;

    public GameWorld(Random random) {
        this.random = random;
        this.players = new ArrayList<>();
        this.environment = new Environment();
        this.dungeonLights = new Array<>();
        this.gameObjectManager = new GameObjectManager();
        setupEnvironment();
    }

    /**
     * Server-only constructor that creates minimal GameWorld for network coordination only.
     * Skips graphics initialization entirely.
     */
    public GameWorld(Random random, boolean serverOnly) {
        this.random = random;
        if (serverOnly) {
            // Server only needs minimal objects for network sync
            this.players = new ArrayList<>();
            this.gameObjectManager = new GameObjectManager();
            // Skip: environment, dungeonLights, setupEnvironment() - all graphics related
            Log.info("GameWorld", "Created server-only GameWorld (no graphics)");
        } else {
            // Normal client initialization
            this.players = new ArrayList<>();
            this.environment = new Environment();
            this.dungeonLights = new Array<>();
            this.gameObjectManager = new GameObjectManager();
            setupEnvironment();
        }
    }

    private void setupEnvironment() {
        // Extremely low ambient light for very hard shadows
//        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.02f, 0.02f, 0.03f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.0f, .0f, .0f, 1f));
    }

    public void initializeMap() {
        if (mapManager == null) {
            // Moderate map size with increased network buffers
            int size = 50;
            int height = 10;

            mapManager = new GameMap(size, height, size, System.currentTimeMillis());
            Log.info("GameWorld", "Created moderate map ("+size+"x"+height+"x"+size+" = " + (size*height*size) + " tiles) with 5MB network buffers");
            mapRenderer = new GameMapRenderer();
            mapRenderer.updateMap(mapManager);

            // Add lights from map LightHints to the environment
            mapRenderer.addMapLightsToEnvironment(environment);

            Log.info("GameWorld", "Initialized new map");
        }
    }

    /**
     * Server-only map initialization that skips rendering components.
     * Used by HostedGameMode to create a map for network distribution without graphics overhead.
     */
    public void initializeMapServerOnly() {
        if (mapManager == null) {
            // Same map generation as client but no rendering setup or physics
            int size = 50;
            int height = 10;

            mapManager = new GameMap(size, height, size, System.currentTimeMillis(), true); // true = server-only
            Log.info("GameWorld", "Created server-only map ("+size+"x"+height+"x"+size+" = " + (size*height*size) + " tiles) - no rendering, no physics");
            
            // Server doesn't need renderer or environment lights
            // mapRenderer = null (stays null)
            
            Log.info("GameWorld", "Initialized server-only map");
        }
    }

    public void setMap(GameMap map) {
        this.mapManager = map;
        if (mapRenderer == null) {
            mapRenderer = new GameMapRenderer();
        }
        mapRenderer.updateMap(mapManager);

        // Add lights from map LightHints to the environment
        mapRenderer.addMapLightsToEnvironment(environment);

        Log.info("GameWorld", "Set map from network");
    }

    public void setupLocalPlayer() {
        if (gameObjectManager.localPlayerController == null) {
            Log.info("GameWorld", "Creating local player controller");
            gameObjectManager.localPlayerController = createPlayerController();
            // Don't set localPlayerId here - it will be set by the server assignment
            players.add(gameObjectManager.localPlayerController);
            Log.info("GameWorld", "Local player controller created with ID: " + gameObjectManager.localPlayerController.getPlayerId() + " and added to players list");
        }

        if (mapManager != null) {
            // Add player to physics world
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 25, 15);
            if (!mapManager.spawnTiles.isEmpty()) {
                MapTile spawnTile = mapManager.spawnTiles.get(0);
                playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
            }

            mapManager.addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);

            gameObjectManager.localPlayerController.setGameMap(mapManager);
            gameObjectManager.localPlayerController.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z, 0);

            Log.info("GameWorld", "Setup local player at position: " + playerStart);
        }
    }

    public void update(float deltaTime) {
        // Update torch flicker effect
        updateDungeonLights(deltaTime);

        // Update physics
        if (mapManager != null && gameObjectManager.localPlayerController != null) {
            mapManager.stepPhysics(deltaTime);
            Vector3 bulletPlayerPos = mapManager.getPlayerPosition();
            gameObjectManager.localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z, deltaTime);
        }

        // Update local player
        if (gameObjectManager.localPlayerController != null) {
            gameObjectManager.update(deltaTime);
        }

        // Update position update timer
        positionUpdateTimer += deltaTime;
    }

    /**
     * Server-only update that skips physics simulation and rendering preparation.
     * Used by HostedGameMode to avoid duplicate physics calculations.
     */
    public void updateServerOnly(float deltaTime) {
        // Server only needs to update game objects for network synchronization
        // Skip physics (client handles authoritative physics)
        // Skip light flickering (purely visual)
        // Skip player controller updates (client handles input/camera)

        // Update server-side game objects if any
        // Note: Currently minimal since we don't have many server-only objects yet

        // Keep position update timer for network sync timing
        positionUpdateTimer += deltaTime;
    }

    private void updateDungeonLights(float deltaTime) {
        // Add subtle flickering to torch lights for atmosphere
        for (int i = 0; i < dungeonLights.size; i++) {
            PointLight light = dungeonLights.get(i);
            if (i < 3) { // First 3 are torches
                float flicker = 0.9f + 0.1f * (float) Math.sin(System.currentTimeMillis() * 0.005f + i);
                light.intensity = flicker;
            }
        }
    }

    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (mapRenderer != null && camera != null) {
            // Set post-processing effect based on local player's current tile
            if (gameObjectManager.localPlayerController != null) {
                mapRenderer.setPostProcessingEffect(gameObjectManager.localPlayerController.getCurrentTileFillType());
            }

            // Step 1: Render scene with bloom effects first
            mapRenderer.beginBloomRender();

            // Render the map with bloom framebuffer
            mapRenderer.render(camera, environment, mapRenderer.getBloomFrameBuffer());

            // Render all other players
            if (players != null && gameObjectManager.localPlayerController != null) {
                for (PlayerController player : players) {
                    if (!player.getPlayerId().equals(gameObjectManager.localPlayerController.getPlayerId())) {
                        player.render(modelBatch, environment, gameObjectManager.localPlayerController.getCamera());
                    }
                }
            }

            // Render physics debug information if enabled
            if (mapManager != null) {
                mapManager.renderPhysicsDebug(camera);
            }

            // End bloom render (this renders bloom result to screen)
            mapRenderer.endBloomRender();

            // Step 2: Apply post-processing effects to the bloom result
            // Only apply post-processing if we have an effect to apply
            if (gameObjectManager.localPlayerController != null &&
                gameObjectManager.localPlayerController.getCurrentTileFillType() != MapTileFillType.AIR) {

                // Apply post-processing overlay to the current screen (with bloom)
                mapRenderer.applyPostProcessingToScreen();
            }
        }
    }

    public boolean shouldSendPositionUpdate() {
        if (positionUpdateTimer >= POSITION_UPDATE_INTERVAL) {
            positionUpdateTimer = 0;
            return true;
        }
        return false;
    }

    // Getters
    public GameMap getMapManager() { return mapManager; }
    public GameMapRenderer getMapRenderer() { return mapRenderer; }
    public Environment getEnvironment() { return environment; }
    public Random getRandom() { return random; }
    public GameObjectManager getGameObjectManager() { return gameObjectManager; }

    /**
     * Handle window resize for all rendering components.
     */
    public void resize(int width, int height) {
        if (mapRenderer != null) {
            mapRenderer.resize(width, height);
        }
        if (gameObjectManager.localPlayerController != null) {
            gameObjectManager.localPlayerController.resize(width, height);
        }
    }


    // Physics debug methods
    public void togglePhysicsDebug() {
        if (mapManager != null) {
            boolean newState = !mapManager.isDebugRenderingEnabled();
            mapManager.setDebugRenderingEnabled(newState);

            // Initialize debug drawer if needed (must be on OpenGL thread)
            if (newState) {
                mapManager.initializeDebugDrawer();
            }
        }
    }

    public void togglePhysicsStrategy() {
        if (mapManager != null) {
            // Switch between strategies
            GameMap.PhysicsStrategy currentStrategy = mapManager.getPhysicsStrategy();
            GameMap.PhysicsStrategy newStrategy = (currentStrategy == GameMap.PhysicsStrategy.ALL_TILES)
                ? GameMap.PhysicsStrategy.BFS_BOUNDARY
                : GameMap.PhysicsStrategy.ALL_TILES;

            Log.info("GameWorld", "Switching physics strategy from " + currentStrategy + " to " + newStrategy);
            mapManager.setPhysicsStrategy(newStrategy);
            mapManager.regeneratePhysics();
        }
    }

    public boolean isPhysicsDebugEnabled() {
        return mapManager != null && mapManager.isDebugRenderingEnabled();
    }

    public String getPhysicsStrategyInfo() {
        if (mapManager != null) {
            return mapManager.getPhysicsStrategy().name();
        }
        return "N/A";
    }

    public long getPhysicsTriangleCount() {
        return mapManager != null ? mapManager.totalTriangleCount : 0;
    }

    public void toggleRenderingStrategy() {
        if (mapRenderer != null && mapManager != null) {
            // Switch between strategies
            GameMapRenderer.RenderingStrategy currentStrategy = mapRenderer.getRenderingStrategy();
            GameMapRenderer.RenderingStrategy newStrategy = (currentStrategy == GameMapRenderer.RenderingStrategy.ALL_TILES)
                ? GameMapRenderer.RenderingStrategy.BFS_VISIBLE
                : GameMapRenderer.RenderingStrategy.ALL_TILES;

            Log.info("GameWorld", "Switching rendering strategy from " + currentStrategy + " to " + newStrategy);
            mapRenderer.setRenderingStrategy(newStrategy);
            mapRenderer.updateMap(mapManager);
        }
    }

    public String getRenderingStrategyInfo() {
        if (mapRenderer != null) {
            return mapRenderer.getRenderingStrategy().name();
        }
        return "N/A";
    }

    public long getRenderingFacesBuilt() {
        return mapRenderer != null ? mapRenderer.getLastFacesBuilt() : 0;
    }

    public long getRenderingTilesProcessed() {
        return mapRenderer != null ? mapRenderer.getLastTilesProcessed() : 0;
    }

    public void dispose() {
        if (disposed) {
            Log.info("GameWorld", "Already disposed, skipping");
            return;
        }

        Log.info("GameWorld", "Disposing game world...");

        // Dispose map renderer first
        if (mapRenderer != null) {
            try {
                mapRenderer.disposeAll();
                Log.info("GameWorld", "Map renderer disposed");
            } catch (Exception e) {
                Log.error("GameWorld", "Error disposing map renderer: " + e.getMessage());
            }
            mapRenderer = null;
        }

        // Clear dungeon lights
        if (dungeonLights != null) {
            dungeonLights.clear();
            Log.info("GameWorld", "Dungeon lights cleared");
        }

        // Dispose map manager
        if (mapManager != null) {
            try {
                mapManager.dispose();
                Log.info("GameWorld", "Map manager disposed");
            } catch (Exception e) {
                Log.error("GameWorld", "Error disposing map manager: " + e.getMessage());
            }
            mapManager = null;
        }

        // Clear references
        players.clear();
        gameObjectManager.activePlayers.clear();
        gameObjectManager.localPlayerController = null;

        disposed = true;
        Log.info("GameWorld", "Game world disposed");
    }
}
