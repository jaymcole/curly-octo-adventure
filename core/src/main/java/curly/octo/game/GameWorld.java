package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.GameMapRenderer;
import curly.octo.player.PlayerController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Base game world that contains shared components.
 * Extended by HostGameWorld and ClientGameWorld for specific implementations.
 */
public abstract class GameWorld {

    protected GameMap mapManager;
    protected GameMapRenderer mapRenderer;
    protected Environment environment;
    protected List<PlayerController> players;
    protected Random random;
    protected GameObjectManager gameObjectManager;

    // Physics update timer
    protected float positionUpdateTimer = 0;
    protected static final float POSITION_UPDATE_INTERVAL = 1/60f; // 60 updates per second
    protected boolean disposed = false;

    public GameWorld(Random random) {
        this.random = random;
        this.players = new ArrayList<>();
        this.environment = new Environment();
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
            this.gameObjectManager = new GameObjectManager();
            setupEnvironment();
        }
    }

    private void setupEnvironment() {
        // Extremely low ambient light for very hard shadows
//        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.02f, 0.02f, 0.03f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.0f, .0f, .0f, 1f));
    }


    public void setMap(GameMap map) {
        this.mapManager = map;
        if (mapRenderer == null) {
            mapRenderer = new GameMapRenderer(gameObjectManager);
        }
        mapRenderer.updateMap(mapManager, environment);
        Log.info("GameWorld", "Set map from network");
    }


    public abstract void update(float deltaTime);



    public boolean shouldSendPositionUpdate() {
        if (positionUpdateTimer >= POSITION_UPDATE_INTERVAL) {
            positionUpdateTimer = 0;
            return true;
        }
        return false;
    }

    protected void incrementPositionUpdateTimer(float deltaTime) {
        positionUpdateTimer += deltaTime;
    }

    protected void setMapManager(GameMap mapManager) {
        this.mapManager = mapManager;
    }

    protected List<PlayerController> getPlayers() {
        return players;
    }

    // Getters
    public GameMap getMapManager() { return mapManager; }
    public GameMapRenderer getMapRenderer() { return mapRenderer; }
    public Environment getEnvironment() { return environment; }
    public Random getRandom() { return random; }
    public GameObjectManager getGameObjectManager() { return gameObjectManager; }



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
            mapRenderer.updateMap(mapManager, environment);
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
