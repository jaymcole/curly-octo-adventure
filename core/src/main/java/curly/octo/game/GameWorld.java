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
    private PlayerController localPlayerController;
    private long localPlayerId;
    private Random random;

    // Physics update timer
    private float positionUpdateTimer = 0;
    private static final float POSITION_UPDATE_INTERVAL = 1/60f; // 60 updates per second
    private boolean disposed = false;

    public GameWorld(Random random) {
        this.random = random;
        this.players = new ArrayList<>();
        this.environment = new Environment();
        this.dungeonLights = new Array<>();
        setupEnvironment();
    }

    private void setupEnvironment() {
        // Extremely low ambient light for very hard shadows
//        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.02f, 0.02f, 0.03f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.0f, .0f, .0f, 1f));

        // Create atmospheric point lights around the dungeon
        createDungeonLights();
    }

    private void createDungeonLights() {
        // Player lights are now managed by individual PlayerController instances
        // No need for a global playerLantern
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
        if (localPlayerController == null) {
            Log.info("GameWorld", "Creating local player controller");
            localPlayerController = createPlayerController(random);
            // Don't set localPlayerId here - it will be set by the server assignment
            players.add(localPlayerController);
            Log.info("GameWorld", "Local player controller created with ID: " + localPlayerController.getPlayerId() + " and added to players list");

            // Set the localPlayerId to match the created player (important for server mode)
            setLocalPlayerId(localPlayerController.getPlayerId());
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

            localPlayerController.setGameMap(mapManager);
            localPlayerController.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z, 0);

            // Add local player light to environment
            Log.info("GameWorld", "About to add local player light to environment for player " + localPlayerController.getPlayerId());
            addPlayerToEnvironment(localPlayerController);

            Log.info("GameWorld", "Setup local player at position: " + playerStart);
        }
    }

    public void update(float deltaTime) {
        // Update torch flicker effect
        updateDungeonLights(deltaTime);

        // Update physics
        if (mapManager != null && localPlayerController != null) {
            mapManager.stepPhysics(deltaTime);
            Vector3 bulletPlayerPos = mapManager.getPlayerPosition();
            localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z, deltaTime);
        }

        // Update local player
        if (localPlayerController != null) {
            localPlayerController.update(deltaTime);
        }

        // Update position update timer
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


    public void addPlayerToEnvironment(PlayerController player) {
        PointLight light = player.getPlayerLight();
        if (light != null) {
            environment.add(light);
            Log.info("GameWorld", "Added light for player " + player.getPlayerId() + " to environment at (" +
                light.position.x + "," + light.position.y + "," + light.position.z + ") intensity=" + light.intensity);
        } else {
            Log.warn("GameWorld", "Player " + player.getPlayerId() + " has no light to add to environment");
        }
    }

    public void removePlayerFromEnvironment(PlayerController player) {
        if (player.getPlayerLight() != null) {
            environment.remove(player.getPlayerLight());
            Log.info("GameWorld", "Removed light for player " + player.getPlayerId() + " from environment");
        }
    }

    private void removeAllPlayerLights() {
        for (PlayerController player : players) {
            if (player.getPlayerLight() != null) {
                environment.remove(player.getPlayerLight());
            }
        }
        Log.info("GameWorld", "Removed all player lights from environment");
    }

    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (mapRenderer != null && camera != null) {
            // DEBUG: Test basic framebuffer capture and passthrough display
            mapRenderer.beginBloomRender();
            
            // Render the map with bloom framebuffer restoration
            mapRenderer.render(camera, environment, mapRenderer.getBloomFrameBuffer());
            
            // Render all other players
            if (players != null && localPlayerController != null) {
                for (PlayerController player : players) {
                    if (player.getPlayerId() != localPlayerController.getPlayerId()) {
                        player.render(modelBatch, environment, localPlayerController.getCamera());
                    }
                }
            }

            // Render physics debug information if enabled
            if (mapManager != null) {
                mapManager.renderPhysicsDebug(camera);
            }
            
            // DEBUG: Test passthrough render (should show scene if working)
            mapRenderer.endBloomRender();
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
    public List<PlayerController> getPlayers() { return players; }
    public PlayerController getLocalPlayerController() { return localPlayerController; }
    public long getLocalPlayerId() { return localPlayerId; }
    public Random getRandom() { return random; }
    
    /**
     * Handle window resize for all rendering components.
     */
    public void resize(int width, int height) {
        if (mapRenderer != null) {
            mapRenderer.resize(width, height);
        }
        if (localPlayerController != null) {
            localPlayerController.resize(width, height);
        }
    }

    // Setters
    public void setLocalPlayerId(long localPlayerId) {
        Log.info("GameWorld", "Setting local player ID to: " + localPlayerId);
        this.localPlayerId = localPlayerId;
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

        // Remove all player lights from environment
        removeAllPlayerLights();

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
        localPlayerController = null;

        disposed = true;
        Log.info("GameWorld", "Game world disposed");
    }
}
