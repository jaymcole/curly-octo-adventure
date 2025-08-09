package curly.octo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.MapTile;
import curly.octo.network.GameClient;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.player.PlayerController;
import curly.octo.player.PlayerUtilities;

import java.io.IOException;
import java.util.HashSet;

/**
 * Client game mode that handles connecting to server and receiving updates.
 */
public class ClientGameMode implements GameMode {

    private final GameWorld gameWorld;
    private final String host;
    private GameClient gameClient;
    private boolean active = false;
    private boolean mapReceived = false;
    private boolean playerAssigned = false;
    
    // Network threading
    private Thread networkThread;
    private volatile boolean networkRunning = false;

    public ClientGameMode(String host, java.util.Random random) {
        this.host = host;
        this.gameWorld = new GameWorld(random);
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
                Log.info("ClientGameMode", "Received map with size: " +
                    receivedMap.getWidth() + "x" +
                    receivedMap.getHeight() + "y" +
                    receivedMap.getDepth() + "z (" +
                    (receivedMap.getWidth() * receivedMap.getHeight() * receivedMap.getDepth()) + " total tiles)");

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
                for (PlayerController player : gameWorld.getGameObjectManager().activePlayers) {
                    currentPlayers.add(player.getPlayerId());
                }

                for (PlayerController player : roster.players) {
                    if (!currentPlayers.contains(player.getPlayerId())) {
                        // Ensure other players don't have physics bodies
                        player.setGameMap(null);
                        gameWorld.getGameObjectManager().activePlayers.add(player);
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
                PlayerController playerToRemove = null;
                for (PlayerController player : gameWorld.getGameObjectManager().activePlayers) {
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
                if (gameWorld.getGameObjectManager().localPlayerController != null && 
                    playerUpdate.playerId.equals(gameWorld.getGameObjectManager().localPlayerController.getPlayerId())) {
                    return;
                }

                // Find the player in our list
                PlayerController targetPlayer = null;
                for (PlayerController player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.getPlayerId().equals(playerUpdate.playerId)) {
                        targetPlayer = player;
                        break;
                    }
                }

                // If player not found, create a new one
                if (targetPlayer == null) {
                    Log.info("ClientGameMode", "Creating new player controller for player " + playerUpdate.playerId);
                    targetPlayer = PlayerUtilities.createPlayerController();
                    targetPlayer.setPlayerId(playerUpdate.playerId);
                    gameWorld.getGameObjectManager().activePlayers.add(targetPlayer);
                }

                targetPlayer.setPlayerPosition(playerUpdate.x, playerUpdate.y, playerUpdate.z, 0);
            });
        });
    }

    private void setLocalPlayer(String localPlayerId) {
        Log.info("ClientGameMode", "Setting local player ID: " + localPlayerId);
        Log.info("ClientGameMode", "Current activePlayers count: " + gameWorld.getGameObjectManager().activePlayers.size());

        // Debug: Print all active players
        for (int i = 0; i < gameWorld.getGameObjectManager().activePlayers.size(); i++) {
            PlayerController p = gameWorld.getGameObjectManager().activePlayers.get(i);
            Log.info("ClientGameMode", "activePlayers[" + i + "] = " + (p != null ? p.getPlayerId() : "NULL"));
        }

        // First, check if a player with this ID already exists in activePlayers (from roster)
        PlayerController existingPlayer = null;
        for (PlayerController player : gameWorld.getGameObjectManager().activePlayers) {
            if (player != null && player.getPlayerId() != null && player.getPlayerId().equals(localPlayerId)) {
                existingPlayer = player;
                break;
            }
        }

        Log.info("ClientGameMode", "Found existing player: " + (existingPlayer != null ? "YES" : "NO"));

        if (existingPlayer != null) {
            // Use the existing player from the roster as our local player
            Log.info("ClientGameMode", "Using existing player from roster as local player: " + localPlayerId);
            gameWorld.getGameObjectManager().localPlayerController = existingPlayer;

            // CRITICAL: Initialize camera for existing player since roster players don't have cameras
            Log.info("ClientGameMode", "Force-initializing camera for existing player");
            try {
                // Manually initialize camera since the initialize() method is private
                PerspectiveCamera newCamera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                Vector3 playerPos = existingPlayer.getPosition();
                newCamera.position.set(playerPos);
                newCamera.position.y += 5.0f; // player height offset
                newCamera.lookAt(playerPos.x, playerPos.y + 5.0f, playerPos.z - 1.0f); // look forward
                newCamera.up.set(Vector3.Y);
                newCamera.near = 0.1f;
                newCamera.far = 300f;
                newCamera.update();

                // We need to use reflection to set the private camera field
                java.lang.reflect.Field cameraField = PlayerController.class.getDeclaredField("camera");
                cameraField.setAccessible(true);
                cameraField.set(existingPlayer, newCamera);

                Log.info("ClientGameMode", "Successfully created camera for existing player via reflection");
            } catch (Exception e) {
                Log.error("ClientGameMode", "Failed to initialize camera for existing player: " + e.getMessage());
                e.printStackTrace();
            }

            // Set up physics and input for the existing player
            if (gameWorld.getMapManager() != null) {
                existingPlayer.setGameMap(gameWorld.getMapManager());

                // Add player to physics world
                float playerRadius = 1.0f;
                float playerHeight = 5.0f;
                float playerMass = 10.0f;
                Vector3 playerStart = new Vector3(15, 25, 15);
                if (!gameWorld.getMapManager().spawnTiles.isEmpty()) {
                    MapTile spawnTile = gameWorld.getMapManager().spawnTiles.get(0);
                    playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
                }

                gameWorld.getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
                existingPlayer.setPlayerPosition(playerStart.x, playerStart.y, playerStart.z, 0);
                Log.info("ClientGameMode", "Setup existing player at position: " + playerStart);

                // Verify camera is now available
                if (existingPlayer.getCamera() != null) {
                    Log.info("ClientGameMode", "Camera successfully initialized for existing player");
                } else {
                    Log.error("ClientGameMode", "Camera is still NULL after initialization attempt");
                }
            }
            Gdx.input.setInputProcessor(existingPlayer);
        } else {
            // Create the local player if it doesn't exist
            if (gameWorld.getGameObjectManager().localPlayerController == null) {
                Log.info("ClientGameMode", "Creating local player controller");
                gameWorld.setupLocalPlayer();
            }

            // Set the player ID
            PlayerController localPlayer = gameWorld.getGameObjectManager().localPlayerController;
            if (localPlayer != null) {
                localPlayer.setPlayerId(localPlayerId);
                // Also set the localPlayerId in GameWorld
                if (gameWorld.getMapManager() != null) {
                    localPlayer.setGameMap(gameWorld.getMapManager());
                }
                Gdx.input.setInputProcessor(localPlayer);
            } else {
                // Try to create it again
                gameWorld.setupLocalPlayer();
                localPlayer = gameWorld.getGameObjectManager().localPlayerController;
                if (localPlayer != null) {
                    localPlayer.setPlayerId(localPlayerId);
                    if (gameWorld.getMapManager() != null) {
                        localPlayer.setGameMap(gameWorld.getMapManager());
                    }
                    Gdx.input.setInputProcessor(localPlayer);
                    Log.info("ClientGameMode", "Successfully created and set local player ID to: " + localPlayerId);
                } else {
                    Log.error("ClientGameMode", "Still failed to create local player controller after retry");
                }
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

                    if (gameWorld.getGameObjectManager().localPlayerController != null) {
                        Gdx.input.setInputProcessor(gameWorld.getGameObjectManager().localPlayerController);
                        Log.info("ClientGameMode", "Client mode activated successfully");
                    } else {
                        Log.error("ClientGameMode", "Local player controller is null, cannot activate");
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
        
        if (!active) return;

        // Update game world (input processing, physics, player movement)
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

        PlayerController localPlayer = gameWorld.getGameObjectManager().localPlayerController;
        if (localPlayer != null) {
            // Log.info("ClientGameMode", "Rendering with local player: " + localPlayer.getPlayerId());
            PerspectiveCamera camera = localPlayer.getCamera();
            if (camera != null) {
                // Vector3 pos = camera.position;
                // Vector3 dir = camera.direction;
                // Log.info("ClientGameMode", "Camera position: (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
                // Log.info("ClientGameMode", "Camera direction: (" + dir.x + ", " + dir.y + ", " + dir.z + ")");
                gameWorld.render(modelBatch, camera);
            } else {
                Log.error("ClientGameMode", "Local player camera is NULL!");
            }
        } else {
            Log.warn("ClientGameMode", "Active but no local player controller for rendering");
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
        Log.info("ClientGameMode", "Disposing client game mode...");

        // Stop network thread
        networkRunning = false;
        if (networkThread != null && networkThread.isAlive()) {
            try {
                networkThread.interrupt();
                networkThread.join(3000); // Wait up to 3 seconds
                if (networkThread.isAlive()) {
                    Log.warn("ClientGameMode", "Network thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("ClientGameMode", "Interrupted while waiting for network thread to stop");
            }
        }

        // Disconnect from server
        if (gameClient != null) {
            try {
                gameClient.disconnect();
                Log.info("ClientGameMode", "Disconnected from server");
            } catch (Exception e) {
                Log.error("ClientGameMode", "Error disconnecting from server: " + e.getMessage());
            }
        }

        // Dispose our own gameWorld
        if (gameWorld != null) {
            try {
                gameWorld.dispose();
                Log.info("ClientGameMode", "Game world disposed");
            } catch (Exception e) {
                Log.error("ClientGameMode", "Error disposing game world: " + e.getMessage());
            }
        }

        active = false;
        Log.info("ClientGameMode", "Client game mode disposed");
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
        if (gameClient != null && gameWorld.getGameObjectManager().localPlayerController != null) {
            PlayerUpdate update = new PlayerUpdate(
                gameWorld.getGameObjectManager().localPlayerController.getPlayerId(),
                gameWorld.getGameObjectManager().localPlayerController.getPosition()
            );
            gameClient.sendUDP(update);
            // Log.debug("ClientGameMode", "Sent position update: " + update.x + ", " + update.y + ", " + update.z);
        }
    }

    public GameClient getGameClient() {
        return gameClient;
    }
}
