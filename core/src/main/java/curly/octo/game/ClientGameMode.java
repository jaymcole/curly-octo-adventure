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

import java.io.IOException;
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
            return gom.localPlayer.getPlayerId();
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

                        // Send position updates
                        if (active && gameWorld.shouldSendPositionUpdate()) {
                            sendPositionUpdate();
                        }
                    }

                    // Don't overwhelm the network - 60 FPS updates
                    Thread.sleep(16); // ~60 FPS

                } catch (IOException e) {
                    Log.error("ClientGameMode", "Network thread error: " + e.getMessage());
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    Log.info("ClientGameMode", "Network thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
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
                    currentPlayers.add(player.getPlayerId());
                }

                for (PlayerObject player : roster.players) {
                    if (!currentPlayers.contains(player.getPlayerId())) {
                        gameWorld.getGameObjectManager().activePlayers.add(player);
                        gameWorld.getGameObjectManager().add(player);
                    } else {
                        Log.info("ClientGameMode", "Skipping player " + player.getPlayerId() + " - already in current players");
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
                    if (player.getPlayerId().equals(disconnectUpdate.playerId)) {
                        playerToRemove = player;
                        break;
                    }
                }

                if (playerToRemove != null) {
                    // Remove player light from environment
//                    gameWorld.removePlayerFromEnvironment(playerToRemove);

                    // Remove player from list
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
                // Log.debug("ClientGameMode", "Received position update for player " + playerUpdate.playerId + ": " +
                //     playerUpdate.x + ", " + playerUpdate.y + ", " + playerUpdate.z);

                // Skip updates for the local player (if local player is set up)
                String localId = getLocalPlayerId();
                if (localId != null && playerUpdate.playerId.equals(localId)) {
                    return;
                }

                // Find the player in our list
                PlayerObject targetPlayer = null;
                for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.getPlayerId().equals(playerUpdate.playerId)) {
                        targetPlayer = player;
                        break;
                    }
                }

                // If player not found, create a new one
                if (targetPlayer == null) {
                    Log.info("ClientGameMode", "Creating new player controller for player " + playerUpdate.playerId);
                    targetPlayer = new PlayerObject(playerUpdate.playerId, true); // server-only mode
                    gameWorld.getGameObjectManager().activePlayers.add(targetPlayer);
                    gameWorld.getGameObjectManager().add(targetPlayer);
                }

                targetPlayer.setPosition(new Vector3(playerUpdate.x, playerUpdate.y, playerUpdate.z));
            });
        });
    }

    private void setLocalPlayer(String localPlayerId) {
        Log.info("ClientGameMode", "Setting local player ID: " + localPlayerId);
        Log.info("ClientGameMode", "Current activePlayers count: " + gameWorld.getGameObjectManager().activePlayers.size());

        // Debug: Print all active players
        for (int i = 0; i < gameWorld.getGameObjectManager().activePlayers.size(); i++) {
            PlayerObject p = gameWorld.getGameObjectManager().activePlayers.get(i);
            Log.info("ClientGameMode", "activePlayers[" + i + "] = " + (p != null ? p.getPlayerId() : "NULL"));
        }

        // First, check if a player with this ID already exists in activePlayers (from roster)
        PlayerObject existingPlayer = null;
        for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
            if (player != null && player.getPlayerId() != null && player.getPlayerId().equals(localPlayerId)) {
                existingPlayer = player;
                break;
            }
        }

        Log.info("ClientGameMode", "Found existing player: " + (existingPlayer != null ? "YES" : "NO"));

        if (existingPlayer != null) {
            // Found existing player from roster, but create a fresh local player instead
            // The existing player is for network tracking, not local control
            Log.info("ClientGameMode", "Found existing player from roster, but creating fresh local player for control");
            Log.info("ClientGameMode", "Existing player graphics initialized: " + existingPlayer.isGraphicsInitialized());
            Log.info("ClientGameMode", "Existing player current position: " + existingPlayer.getPosition());

            // Remove the existing network player from active players since we'll replace it
            gameWorld.getGameObjectManager().activePlayers.remove(existingPlayer);
            gameWorld.getGameObjectManager().remove(existingPlayer);
        }

        // Always create a fresh local player for proper initialization
        {
            // Create the local player if it doesn't exist
            if (gameWorld.getGameObjectManager().localPlayer == null) {
                Log.info("ClientGameMode", "Creating local player object");
                gameWorld.setupLocalPlayer();
            }

            // Set the player ID
            PlayerObject localPlayer = gameWorld.getGameObjectManager().localPlayer;
            if (localPlayer != null) {
                localPlayer.setPlayerId(localPlayerId);
                // Also set the localPlayerId in GameWorld
                if (gameWorld.getMapManager() != null) {
                    localPlayer.setGameMap(gameWorld.getMapManager());
                }
                inputController.setPossessionTarget(localPlayer);
                if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
            }
            } else {
                // Try to create it again
                gameWorld.setupLocalPlayer();
                localPlayer = gameWorld.getGameObjectManager().localPlayer;
                if (localPlayer != null) {
                    localPlayer.setPlayerId(localPlayerId);
                    if (gameWorld.getMapManager() != null) {
                        localPlayer.setGameMap(gameWorld.getMapManager());
                    }
                    inputController.setPossessionTarget(localPlayer);
                if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
            }
                    Log.info("ClientGameMode", "Successfully created and set local player ID to: " + localPlayerId);
                } else {
                    Log.error("ClientGameMode", "Still failed to create local player controller after retry");
                }
            }
        }
    }

    private void checkReady() {
        Log.info("ClientGameMode", "checkReady() - mapReceived: " + mapReceived + ", playerAssigned: " + playerAssigned + ", active: " + active);
        System.out.println("checkReady() - mapReceived: " + mapReceived + ", playerAssigned: " + playerAssigned + ", active: " + active);

        if (mapReceived && playerAssigned && !active) {
            Log.info("ClientGameMode", "All conditions met, activating client mode...");
            System.out.println("All conditions met, activating client mode...");
            // Switch to 3D view
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000);
                    active = true;
                    System.out.println("ClientGameMode activated! active=" + active);

                    if (gameWorld.getGameObjectManager().localPlayer != null) {
                        inputController.setPossessionTarget(gameWorld.getGameObjectManager().localPlayer);
                        if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                            Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputController);
                            System.out.println("Set InputProcessor to: " + inputController.getClass().getSimpleName());
                        } else {
                            System.out.println("ERROR: inputController is not an InputProcessor: " + inputController.getClass().getSimpleName());
                        }
                        Log.info("ClientGameMode", "Client mode activated successfully");
                        System.out.println("Client mode activated successfully with local player: " + gameWorld.getGameObjectManager().localPlayer.getPlayerId());
                    } else {
                        Log.error("ClientGameMode", "Local player object is null, cannot activate");
                        System.out.println("ERROR: Local player object is null, cannot activate");
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

            if (playerId != null && position != null) {
                PlayerUpdate update = new PlayerUpdate(playerId, position);
                gameClient.sendUDP(update);
                // Log.debug("ClientGameMode", "Sent position update: " + update.x + ", " + update.y + ", " + update.z);
            }
        }
    }

    public GameClient getGameClient() {
        return gameClient;
    }
}
