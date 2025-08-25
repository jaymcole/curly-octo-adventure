package curly.octo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameClient;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.gameobjects.PlayerObject;
import curly.octo.input.InputController;
import curly.octo.input.MinimalPlayerController;
import curly.octo.map.MapTile;
import curly.octo.map.hints.MapHint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Client game mode that handles connecting to server and receiving updates.
 */
public class ClientGameMode implements GameMode {

    private final ClientGameWorld gameWorld;
    private final String host;
    private GameClient gameClient;
    private boolean active = false;
    private boolean mapReceived = false;
    private boolean playerAssigned = false;

    // New input system (optional migration)
    private InputController inputController;
    private boolean useNewInputSystem = false; // Flag to enable new system
    private PerspectiveCamera camera;

    // Network threading
    private Thread networkThread;
    private volatile boolean networkRunning = false;
    private long lastNetworkLoopTime = System.nanoTime();

    public ClientGameMode(String host, java.util.Random random) {
        this.host = host;
        this.gameWorld = new ClientGameWorld(random);

        // Initialize new input system for future migration
        this.inputController = new MinimalPlayerController();
    }

    // Helper method to get local player position
    private Vector3 getLocalPlayerPosition() {
        GameObjectManager gom = gameWorld.getGameObjectManager();
        if (gom.localPlayer != null) {
            return gom.localPlayer.getPosition();
        }
        return null;
    }

    // Helper method to get local player ID
    private String getLocalPlayerId() {
        GameObjectManager gom = gameWorld.getGameObjectManager();
        if (gom.localPlayer != null) {
            return gom.localPlayer.entityId;
        }
        return null;
    }

    @Override
    public void initialize() {
        try {
            Log.info("ClientGameMode", "Initializing client mode");

            gameClient = new GameClient(host);
            setupNetworkListeners();

            // Connect to server
            gameClient.connect(5000);

            // Start network thread for processing network updates
            startNetworkThread();

            Log.info("ClientGameMode", "Connected to server at " + host);

        } catch (IOException e) {
            Log.error("ClientGameMode", "Failed to connect to server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startNetworkThread() {
        networkRunning = true;
        networkThread = new Thread(() -> {
            Log.info("ClientGameMode", "Network thread started");

            while (networkRunning) {
                try {
                    // Process network updates
                    if (gameClient != null) {
                        // Handle connection state
                        if (gameClient.isConnecting()) {
                            if (gameClient.updateConnection()) {
                                if (gameClient.isConnected()) {
                                    Log.info("ClientGameMode", "Successfully connected to server");
                                } else {
                                    Log.error("ClientGameMode", "Failed to connect to server");
                                    continue;
                                }
                            }
                        } else {
                            // Regular client updates - this is the blocking operation!
                            gameClient.update();
                        }

                        // Send position updates - increment timer based on actual time elapsed
                        if (active) {
                            long currentTime = System.nanoTime();
                            float deltaTimeSeconds = (currentTime - lastNetworkLoopTime) / 1_000_000_000.0f;
                            lastNetworkLoopTime = currentTime;

                            gameWorld.incrementPositionUpdateTimer(deltaTimeSeconds);
                            if (gameWorld.shouldSendPositionUpdate()) {
                                sendPositionUpdate();
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.error("ClientGameMode", "Network thread error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            Log.info("ClientGameMode", "Network thread exiting");
        }, "ClientNetworkThread");

        networkThread.setDaemon(false);
        networkThread.start();
    }

    private void setupNetworkListeners() {
        // Map received listener
        gameClient.setMapReceivedListener(receivedMap -> {
            Gdx.app.postRunnable(() -> {
                long startTime = System.currentTimeMillis();
                gameWorld.setMap(receivedMap);
                long endTime = System.currentTimeMillis();

                Log.info("ClientGameMode", "Map setup completed in " + (endTime - startTime) + "ms");
                mapReceived = true;
                checkReady();
            });
        });

        // Player assignment listener
        gameClient.setPlayerAssignmentListener(receivedPlayerId -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "Assigned player ID: " + receivedPlayerId.playerId);
                setLocalPlayer(receivedPlayerId.playerId);
                playerAssigned = true;
                checkReady();
            });
        });

        // Player roster listener
        gameClient.setPlayerRosterListener(roster -> {
            Gdx.app.postRunnable(() -> {
                HashSet<String> currentPlayers = new HashSet<>();
                for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                    currentPlayers.add(player.entityId);
                }

                for (PlayerObject player : roster.players) {
                    if (!currentPlayers.contains(player.entityId)) {
                        gameWorld.getGameObjectManager().activePlayers.add(player);
                        gameWorld.getGameObjectManager().add(player);
                    } else {
                        Log.info("ClientGameMode", "Skipping player " + player.entityId + " - already in current players");
                    }
                }
            });
        });

        // Player disconnect listener
        gameClient.setPlayerDisconnectListener(disconnectUpdate -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "Processing disconnect for player " + disconnectUpdate.playerId);

                // Find and remove the disconnected player
                PlayerObject playerToRemove = null;
                for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.entityId.equals(disconnectUpdate.playerId)) {
                        playerToRemove = player;
                        break;
                    }
                }

                if (playerToRemove != null) {
                    gameWorld.getGameObjectManager().activePlayers.remove(playerToRemove);
                    gameWorld.getGameObjectManager().remove(playerToRemove);

                    Log.info("ClientGameMode", "Removed disconnected player " + disconnectUpdate.playerId + " from client");
                } else {
                    Log.warn("ClientGameMode", "Could not find player " + disconnectUpdate.playerId + " to remove");
                }
            });
        });

        // Player update listener
        gameClient.setPlayerUpdateListener(playerUpdate -> {
            Gdx.app.postRunnable(() -> {
                // Skip updates for the local player (if local player is set up)
                String localId = getLocalPlayerId();
                if (localId != null && playerUpdate.playerId.equals(localId)) {
                    return;
                }

                // Find the player in our list
                PlayerObject targetPlayer = null;
                for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.entityId.equals(playerUpdate.playerId)) {
                        targetPlayer = player;
                        break;
                    }
                }

                // If player not found, create a new one
                if (targetPlayer == null) {
                    Log.info("ClientGameMode", "Creating new player controller for player " + playerUpdate.playerId);
                    targetPlayer = new PlayerObject(playerUpdate.playerId); // client mode - need graphics
                    gameWorld.getGameObjectManager().activePlayers.add(targetPlayer);
                    gameWorld.getGameObjectManager().add(targetPlayer);
                }

                targetPlayer.setPosition(new Vector3(playerUpdate.x, playerUpdate.y, playerUpdate.z));
                targetPlayer.setYaw(playerUpdate.yaw);
                targetPlayer.setPitch(playerUpdate.pitch);
            });
        });
    }

    private void setLocalPlayer(String localPlayerId) {
        Log.info("ClientGameMode", "Setting local player ID: " + localPlayerId);
        Log.info("ClientGameMode", "Current activePlayers count: " + gameWorld.getGameObjectManager().activePlayers.size());

        // Debug: Print all active players
        for (int i = 0; i < gameWorld.getGameObjectManager().activePlayers.size(); i++) {
            PlayerObject p = gameWorld.getGameObjectManager().activePlayers.get(i);
            Log.info("ClientGameMode", "activePlayers[" + i + "] = " + (p != null ? p.entityId : "NULL"));
        }

        // First, check if a player with this ID already exists in activePlayers (from roster)
        PlayerObject existingPlayer = null;
        for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
            if (player != null && player.entityId != null && player.entityId.equals(localPlayerId)) {
                existingPlayer = player;
                break;
            }
        }

        Log.info("ClientGameMode", "Found existing player: " + (existingPlayer != null ? "YES" : "NO"));

        if (existingPlayer != null) {
            // Found existing player from roster, reuse it as the local player
            Log.info("ClientGameMode", "Found existing player from roster, reusing as local player");
            Log.info("ClientGameMode", "Existing player graphics initialized: " + existingPlayer.isGraphicsInitialized());
            Log.info("ClientGameMode", "Existing player current position: " + existingPlayer.getPosition());

            // Set this existing player as the local player instead of creating a new one
            gameWorld.getGameObjectManager().localPlayer = existingPlayer;

            // Set up the existing player for local control with full physics setup
            if (gameWorld.getMapManager() != null) {
                // Check if player physics is already set up
                if (gameWorld.getMapManager().getPlayerController() == null) {
                    // Add player to physics world only if not already added
                    float playerRadius = 1.0f;
                    float playerHeight = 5.0f;
                    float playerMass = 10.0f;
                    Vector3 playerStart = new Vector3(15, 25, 15);
                    ArrayList<MapHint> spawnHints = gameWorld.getMapManager().getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
                    if (!spawnHints.isEmpty()) {
                        MapTile spawnTile = gameWorld.getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                        if (spawnTile != null) {
                            // Spawn above the tile, not at the tile position
                            playerStart = new Vector3(spawnTile.x, spawnTile.y + 3, spawnTile.z);
                        }
                    }
                    gameWorld.getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
                }

                // Link the PlayerObject to the physics character controller
                existingPlayer.setGameMap(gameWorld.getMapManager());
                existingPlayer.setCharacterController(gameWorld.getMapManager().getPlayerController());

                // Set spawn position
                Vector3 playerStart = new Vector3(15, 25, 15);
                ArrayList<MapHint> spawnHints = gameWorld.getMapManager().getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
                if (!spawnHints.isEmpty()) {
                    MapTile spawnTile = gameWorld.getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                    if (spawnTile != null) {
                        playerStart = new Vector3(spawnTile.x, spawnTile.y + 3, spawnTile.z);
                    }
                }
                existingPlayer.setPosition(new Vector3(playerStart.x, playerStart.y, playerStart.z));
            }

            inputController.setPossessionTarget(existingPlayer);
            if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
            }

            Log.info("ClientGameMode", "Successfully reused existing player as local player ID: " + localPlayerId);
            return; // Exit early since we've set up the local player
        }

        // Create a fresh local player only if none exists
        if (gameWorld.getGameObjectManager().localPlayer == null) {
            Log.info("ClientGameMode", "No existing local player found, creating new local player object");
            gameWorld.setupLocalPlayer();

            // Update the entity ID to match the server-assigned ID
            gameWorld.getGameObjectManager().localPlayer.entityId = localPlayerId;

            // Set up the newly created local player
            PlayerObject localPlayer = gameWorld.getGameObjectManager().localPlayer;
            if (localPlayer != null) {
                // Set up the player for local control
                if (gameWorld.getMapManager() != null) {
                    localPlayer.setGameMap(gameWorld.getMapManager());
                }
                inputController.setPossessionTarget(localPlayer);
                if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                    Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
                }
                Log.info("ClientGameMode", "Successfully created and set local player ID to: " + localPlayerId);
            } else {
                Log.error("ClientGameMode", "Failed to create local player controller");
            }
        }
    }

    private void checkReady() {
        Log.info("ClientGameMode", "checkReady() - mapReceived: " + mapReceived + ", playerAssigned: " + playerAssigned + ", active: " + active);

        if (mapReceived && playerAssigned && !active) {
            Log.info("ClientGameMode", "All conditions met, activating client mode...");
            // Switch to 3D view
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000);
                    active = true;

                    if (gameWorld.getGameObjectManager().localPlayer != null) {
                        inputController.setPossessionTarget(gameWorld.getGameObjectManager().localPlayer);
                        if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                            Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
                        }
                        Log.info("ClientGameMode", "Client mode activated successfully");
                    } else {
                        Log.error("ClientGameMode", "Local player object is null, cannot activate");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            Log.info("ClientGameMode", "Not ready yet - waiting for map and player assignment");
        }
    }

    @Override
    public void update(float deltaTime) throws IOException {
        // Network updates are now handled in separate thread
        // This method only handles game world updates on main thread

        if (!active) {
            return;
        }

        // Handle input for local player
        if (inputController != null && gameWorld.getGameObjectManager().localPlayer != null && camera != null) {
            inputController.handleInput(deltaTime, gameWorld.getGameObjectManager().localPlayer, camera);
        }

        // Update game world (physics, player movement)
        gameWorld.update(deltaTime);
    }

    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        if (!active) {
            // Log occasionally to debug activation state
            if (System.currentTimeMillis() % 2000 < 50) { // Log every ~2 seconds
                Log.info("ClientGameMode", "Not rendering - client not active yet (mapReceived: " + mapReceived + ", playerAssigned: " + playerAssigned + ")");
            }
            return;
        }

        PlayerObject localPlayer = gameWorld.getGameObjectManager().localPlayer;
        if (localPlayer != null) {
            // Log.info("ClientGameMode", "Rendering with local player: " + localPlayer.getPlayerId());
            // Create camera for rendering if not exists
            if (camera == null) {
                camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                camera.near = 0.1f;
                camera.far = 300f;
            }

            // Update camera from input controller
            inputController.updateCamera(camera, Gdx.graphics.getDeltaTime());

            gameWorld.render(modelBatch, camera);
        } else {
            Log.warn("ClientGameMode", "Active but no local player object for rendering");
        }
    }

    @Override
    public void resize(int width, int height) {
        if (gameWorld != null) {
            gameWorld.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        long startTime = System.currentTimeMillis();
        Log.info("ClientGameMode", "Disposing client game mode...");

        // Stop network thread
        long networkStart = System.currentTimeMillis();
        networkRunning = false;
        if (networkThread != null && networkThread.isAlive()) {
            try {
                networkThread.interrupt();
                networkThread.join(1000); // Wait up to 1 second (reduced from 3)
                if (networkThread.isAlive()) {
                    Log.warn("ClientGameMode", "Network thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("ClientGameMode", "Interrupted while waiting for network thread to stop");
            }
        }
        long networkEnd = System.currentTimeMillis();
        Log.info("ClientGameMode", "Network thread stopped in " + (networkEnd - networkStart) + "ms");

        // Disconnect from server with timeout
        long disconnectStart = System.currentTimeMillis();
        if (gameClient != null) {
            try {
                // Use a timeout for disconnection to prevent hanging
                Thread disconnectThread = new Thread(() -> {
                    try {
                        gameClient.disconnect();
                        Log.info("ClientGameMode", "Disconnected from server");
                    } catch (Exception e) {
                        Log.error("ClientGameMode", "Error disconnecting from server: " + e.getMessage());
                    }
                }, "DisconnectThread");

                disconnectThread.start();
                disconnectThread.join(500); // Wait max 500ms for disconnect

                if (disconnectThread.isAlive()) {
                    Log.warn("ClientGameMode", "Disconnect thread timeout, proceeding anyway");
                    disconnectThread.interrupt();
                }
            } catch (Exception e) {
                Log.error("ClientGameMode", "Error during timed disconnect: " + e.getMessage());
            }
        }
        long disconnectEnd = System.currentTimeMillis();
        Log.info("ClientGameMode", "Server disconnect took " + (disconnectEnd - disconnectStart) + "ms");

        // Dispose our own gameWorld
        long gameWorldStart = System.currentTimeMillis();
        if (gameWorld != null) {
            try {
                gameWorld.dispose();
                Log.info("ClientGameMode", "Game world disposed");
            } catch (Exception e) {
                Log.error("ClientGameMode", "Error disposing game world: " + e.getMessage());
            }
        }
        long gameWorldEnd = System.currentTimeMillis();
        Log.info("ClientGameMode", "Game world disposal took " + (gameWorldEnd - gameWorldStart) + "ms");

        active = false;
        long totalTime = System.currentTimeMillis() - startTime;
        Log.info("ClientGameMode", "Client game mode disposed in " + totalTime + "ms");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public GameWorld getGameWorld() {
        return gameWorld;
    }

    private void sendPositionUpdate() {
        if (gameClient != null) {
            String playerId = getLocalPlayerId();
            Vector3 position = getLocalPlayerPosition();
            GameObjectManager gom = gameWorld.getGameObjectManager();

            if (playerId != null && position != null && gom.localPlayer != null) {
                float yaw = gom.localPlayer.getYaw();
                float pitch = gom.localPlayer.getPitch();
                PlayerUpdate update = new PlayerUpdate(playerId, position, yaw, pitch);
                gameClient.sendUDP(update);
            }
        }
    }
}
