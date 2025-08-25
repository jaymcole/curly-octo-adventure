package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.GameMapRenderer;
import curly.octo.gameobjects.PlayerObject;

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
    protected List<PlayerObject> players;
    protected Random random;
    protected GameObjectManager gameObjectManager;

    // Physics update timer
    protected float positionUpdateTimer = 0;
    protected static final float POSITION_UPDATE_INTERVAL = 1/60f; // 120 updates per second for smoother movement
    protected boolean disposed = false;

    /**
     * Server-only constructor that creates minimal GameWorld for network coordination only.
     * Skips graphics initialization entirely.
     */
    public GameWorld(Random random, boolean serverOnly) {
        this.random = random;
        if (serverOnly) {
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
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.0f, .0f, .0f, 1f));
    }

    public void setMap(GameMap map) {
        this.mapManager = map;

        // Initialize physics for client-received maps (server-only maps don't have physics)
        if (!map.isPhysicsInitialized()) {
            Log.info("GameWorld", "Initializing physics for received map");
            map.initializePhysics();
            // Also generate triangle mesh physics that was skipped in server-only generation
            map.regeneratePhysics();
        }

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

    protected List<PlayerObject> getPlayers() {
        return players;
    }

    public GameMap getMapManager() { return mapManager; }
    public GameMapRenderer getMapRenderer() { return mapRenderer; }
    public Environment getEnvironment() { return environment; }
    public Random getRandom() { return random; }
    public GameObjectManager getGameObjectManager() { return gameObjectManager; }

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

    public void dispose() {
        if (disposed) {
            Log.info("GameWorld", "Already disposed, skipping");
            return;
        }

        Log.info("GameWorld", "Disposing game world...");

        if (mapRenderer != null) {
            try {
                mapRenderer.disposeAll();
                Log.info("GameWorld", "Map renderer disposed");
            } catch (Exception e) {
                Log.error("GameWorld", "Error disposing map renderer: " + e.getMessage());
            }
            mapRenderer = null;
        }

        if (mapManager != null) {
            try {
                mapManager.dispose();
                Log.info("GameWorld", "Map manager disposed");
            } catch (Exception e) {
                Log.error("GameWorld", "Error disposing map manager: " + e.getMessage());
            }
            mapManager = null;
        }

        players.clear();
        gameObjectManager.activePlayers.clear();
        gameObjectManager.localPlayer = null;

        disposed = true;
        Log.info("GameWorld", "Game world disposed");
    }
}
