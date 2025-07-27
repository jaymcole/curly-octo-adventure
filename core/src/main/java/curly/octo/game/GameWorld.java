package curly.octo.game;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
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
        environment.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight, 0.02f, 0.02f, 0.03f, 1f));

        // Create atmospheric point lights around the dungeon
        createDungeonLights();
    }

    private void createDungeonLights() {
        // Add some static torch-like lights around the dungeon
//        PointLight torch1 = new PointLight();
//        torch1.set(1f, 0.8f, 0.6f, 10f, 30f, 10f, 25f); // Warm torch light
//        dungeonLights.add(torch1);
//        environment.add(torch1);
//
//        PointLight torch2 = new PointLight();
//        torch2.set(1f, 0.8f, 0.6f, 50f, 30f, 10f, 25f); // Another torch
//        dungeonLights.add(torch2);
//        environment.add(torch2);
//
//        PointLight torch3 = new PointLight();
//        torch3.set(1f, 0.8f, 0.6f, 30f, 30f, 50f, 25f); // Third torch
//        dungeonLights.add(torch3);
//        environment.add(torch3);
//
//        PointLight blueCrystal = new PointLight();
//        blueCrystal.set(0.6f, 0.8f, 1f, 20f, 35f, 30f, 15f); // Magical blue crystal
//        dungeonLights.add(blueCrystal);
//        environment.add(blueCrystal);

        // Create player lantern light that will follow the player
        playerLantern = new PointLight();
        playerLantern.set(1f, 0.9f, 0.7f, 0f, 0f, 0f, 20f); // Warm lantern light
        environment.add(playerLantern);
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
            localPlayerController = createPlayerController(random);
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
            if (!mapManager.spawnTiles.isEmpty()) {
                MapTile spawnTile = mapManager.spawnTiles.get(0);
                playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
            }

            mapManager.addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);

            localPlayerController.setGameMap(mapManager);
            localPlayerController.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z);

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
            localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z);

            // Update player lantern position
            updatePlayerLantern(bulletPlayerPos);
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

    private void updatePlayerLantern(Vector3 playerPos) {
        if (playerLantern != null) {
            // Position lantern slightly above and in front of player
            playerLantern.position.set(playerPos.x + 5, playerPos.y + 3f, playerPos.z);

            // Add subtle sway to lantern light
            float sway = 0.95f + 0.05f * (float) Math.sin(System.currentTimeMillis() * 0.003f);
            playerLantern.intensity = 1f;
        }
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

        // Clear player lantern reference
        playerLantern = null;

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
