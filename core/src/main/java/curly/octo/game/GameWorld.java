package curly.octo.game;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.GameMapRenderer;
import curly.octo.player.PlayerController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shared game world that contains the map, physics, and environment.
 * This is used by both server and client game modes.
 */
public class GameWorld {
    
    private GameMap mapManager;
    private GameMapRenderer mapRenderer;
    private Environment environment;
    private DirectionalLight sun;
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
        setupEnvironment();
    }
    
    private void setupEnvironment() {
        environment.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        sun = new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f);
        environment.add(sun);
    }
    
    public void initializeMap() {
        if (mapManager == null) {
            mapManager = new GameMap(64, 60, 64, System.currentTimeMillis());
            mapRenderer = new GameMapRenderer();
            mapRenderer.updateMap(mapManager);
            Log.info("GameWorld", "Initialized new map");
        }
    }
    
    public void setMap(GameMap map) {
        this.mapManager = map;
        if (mapRenderer == null) {
            mapRenderer = new GameMapRenderer();
        }
        mapRenderer.updateMap(mapManager);
        Log.info("GameWorld", "Set map from network");
    }
    
    public void setupLocalPlayer() {
        if (localPlayerController == null) {
            Log.info("GameWorld", "Creating local player controller");
            localPlayerController = curly.octo.player.PlayerUtilities.createPlayerController(random);
            // Don't set localPlayerId here - it will be set by the server assignment
            players.add(localPlayerController);
            Log.info("GameWorld", "Local player controller created and added to players list");
        }
        
        if (mapManager != null) {
            // Add player to physics world
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 25, 15);
            mapManager.addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
            
            localPlayerController.setGameMap(mapManager);
            localPlayerController.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z);
            
            Log.info("GameWorld", "Setup local player at position: " + playerStart);
        }
    }
    
    public void update(float deltaTime) {
        // Update sun position
        updateSun(deltaTime);
        
        // Update physics
        if (mapManager != null && localPlayerController != null) {
            mapManager.stepPhysics(deltaTime);
            Vector3 bulletPlayerPos = mapManager.getPlayerPosition();
            localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z);
        }
        
        // Update local player
        if (localPlayerController != null) {
            localPlayerController.update(deltaTime);
        }
        
        // Update position update timer
        positionUpdateTimer += deltaTime;
    }
    
    private void updateSun(float deltaTime) {
        // Update sun position in a circular motion
        float sunAngle = deltaTime * 1f; // Adjust rotation speed as needed
        float radius = 10f;
        float sunX = (float) Math.cos(sunAngle) * radius;
        float sunZ = (float) Math.sin(sunAngle) * radius;
        sun.setDirection(new Vector3(-sunX, -0.8f, sunZ).nor());
    }
    
    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (mapRenderer != null && camera != null) {
            mapRenderer.render(camera, environment);
        }
        
        // Render all other players
        if (players != null && localPlayerController != null) {
            for (PlayerController player : players) {
                if (player.getPlayerId() != localPlayerController.getPlayerId()) {
                    player.render(modelBatch, environment, localPlayerController.getCamera());
                }
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
    public List<PlayerController> getPlayers() { return players; }
    public PlayerController getLocalPlayerController() { return localPlayerController; }
    public long getLocalPlayerId() { return localPlayerId; }
    public Random getRandom() { return random; }
    
    // Setters
    public void setLocalPlayerId(long localPlayerId) { 
        Log.info("GameWorld", "Setting local player ID to: " + localPlayerId);
        this.localPlayerId = localPlayerId; 
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
        localPlayerController = null;
        
        disposed = true;
        Log.info("GameWorld", "Game world disposed");
    }
} 