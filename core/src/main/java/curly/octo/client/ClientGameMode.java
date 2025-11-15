package curly.octo.client;

import curly.octo.common.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameMode;
import curly.octo.client.clientStates.StateManager;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.map.hints.SpawnPointHint;
import curly.octo.common.network.messages.PlayerUpdate;
import curly.octo.common.network.messages.PlayerAssignmentUpdate;
import curly.octo.common.network.messages.PlayerObjectRosterUpdate;
import curly.octo.common.network.messages.PlayerDisconnectUpdate;
import curly.octo.common.network.messages.PlayerImpulseMessage;
import curly.octo.common.PlayerObject;
import curly.octo.common.InputController;
import curly.octo.common.MinimalPlayerController;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.hints.MapHint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Client game mode that handles connecting to server and receiving updates.
 */
public class ClientGameMode implements GameMode {

    // Callback interface for communicating with parent (Main)
    public interface MapRegenerationListener {
        void onMapSeedChanged(long newSeed);
    }

    private final ClientGameWorld gameWorld;
    private final String host;
    private GameClient gameClient;
    private boolean active = false;
    private boolean mapReceived = false;
    private boolean playerAssigned = false;

    // State management system
    private boolean networkUpdatesPaused = false;
    private boolean inputDisabled = false;

    // Map regeneration listener
    private MapRegenerationListener mapRegenerationListener;

    // New input system (optional migration)
    private InputController inputController;
    private boolean useNewInputSystem = false; // Flag to enable new system
    private PerspectiveCamera camera;

    // Network threading
    private Thread networkThread;
    private volatile boolean networkRunning = false;
    private long lastNetworkLoopTime = System.nanoTime();

    // Debug: Position update frequency tracking
    private long lastPositionUpdateCount = 0;
    private long lastPositionUpdateTime = System.currentTimeMillis();

    // Debug: Network loop frequency tracking
    private long networkLoopCount = 0;
    private long lastNetworkLoopLogTime = System.currentTimeMillis();

    // Smart rate limiting for sustained performance
    private long lastPositionSendTime = 0;
    private final long TARGET_POSITION_INTERVAL_NS = Constants.NETWORK_POSITION_UPDATE_INTERVAL_NS; // 50 FPS (20ms between updates) - high but sustainable

    // Buffer monitoring
    private long lastBufferCheckTime = System.currentTimeMillis();

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
    public String getLocalPlayerId() {
        GameObjectManager gom = gameWorld.getGameObjectManager();
        if (gom.localPlayer != null) {
            return gom.localPlayer.entityId;
        }
        return null;
    }

    /**
     * Set the map received flag (used by regeneration handlers to integrate with normal flow)
     */
    public void setMapReceivedFlag(boolean received) {
        this.mapReceived = received;
        Log.info("ClientGameMode", "Map received flag set to: " + received);
    }

    /**
     * Get the map received flag
     */
    public boolean isMapReceived() {
        return mapReceived;
    }

    /**
     * Get the player assigned flag
     */
    public boolean isPlayerAssigned() {
        return playerAssigned;
    }

    /**
     * Get the active flag
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Set the active flag (used by state handlers)
     */
    public void setActiveFlag(boolean active) {
        this.active = active;
        Log.info("ClientGameMode", "Active flag set to: " + active);
    }

    /**
     * Get the input controller
     */
    public MinimalPlayerController getInputController() {
        return (MinimalPlayerController) inputController;
    }

    /**
     * Get the game world
     */
    public ClientGameWorld getGameWorld() {
        return gameWorld;
    }

    /**
     * Pause network position updates (used during map regeneration)
     */
    public void pauseNetworkUpdates() {
        this.networkUpdatesPaused = true;
        Log.info("ClientGameMode", "Network position updates paused");
    }

    /**
     * Resume network position updates (used after map regeneration)
     */
    public void resumeNetworkUpdates() {
        this.networkUpdatesPaused = false;
        Log.info("ClientGameMode", "Network position updates resumed");
    }

    /**
     * Disable player input (movement, etc.) during map regeneration
     */
    public void disableInput() {
        inputDisabled = true;
        Log.info("ClientGameMode", "Player input disabled (map regeneration)");
    }

    /**
     * Re-enable player input after map regeneration completes
     */
    public void enableInput() {
        inputDisabled = false;
        Log.info("ClientGameMode", "Player input enabled (map regeneration complete)");
    }

    /**
     * Immediately dispose of current map resources when regeneration starts
     */
    private void performImmediateMapCleanup() {
        try {
            Log.info("ClientGameMode", "Performing immediate map resource cleanup");

            ClientGameWorld clientWorld = (ClientGameWorld) gameWorld;
            if (clientWorld != null) {
                // Call the existing cleanup method that properly disposes resources
                clientWorld.cleanupForMapRegeneration();
                Log.info("ClientGameMode", "Map resources disposed immediately");
            } else {
                Log.warn("ClientGameMode", "No ClientGameWorld available for cleanup");
            }

        } catch (Exception e) {
            Log.error("ClientGameMode", "Error during immediate map cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the GameClient instance for network operations
     */
    public GameClient getGameClient() {
        return gameClient;
    }

    @Override
    public void initialize() {
        try {
            Log.info("ClientGameMode", "Initializing client mode");

            gameClient = new GameClient(host);
            setupNetworkListeners();

            StateManager.setGameClient(gameClient);
            StateManager.setClientGameWorld(gameWorld);

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
                    long loopStartTime = System.currentTimeMillis();

                    // Debug: Track network loop frequency
                    networkLoopCount++;
                    if (loopStartTime - lastNetworkLoopLogTime >= 1000) {
                        Log.info("ClientGameMode", "Network loops per second: " + networkLoopCount);
                        networkLoopCount = 0;
                        lastNetworkLoopLogTime = loopStartTime;
                    }

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
                            // Only call client update every 300th loop to minimize periodic spikes
                            if (networkLoopCount % 300 == 0) {
                                long updateStart = System.nanoTime();
                                gameClient.update();
                                long updateTime = (System.nanoTime() - updateStart) / 1_000_000; // Convert to ms

                                if (updateTime > 50) { // Log if update takes more than 50ms
                                    Log.warn("ClientGameMode", "gameClient.update() took " + updateTime + "ms");
                                }
                            }
                        }

                        // Send position updates - smart rate limiting for sustained performance
                        if (active) {
                            long currentTime = System.nanoTime();
                            if (currentTime - lastPositionSendTime >= TARGET_POSITION_INTERVAL_NS) {
                                sendPositionUpdate();
                                lastPositionSendTime = currentTime;
                            }
                        }

                        // Monitor network buffer status every few seconds
                        long currentTimeMs = System.currentTimeMillis();
                        if (gameClient != null && currentTimeMs - lastBufferCheckTime >= 2000) {
                            checkNetworkBufferStatus();
                            lastBufferCheckTime = currentTimeMs;
                        }
                    }

                    // Small sleep to prevent excessive CPU usage while maintaining responsiveness
                    Thread.sleep(1);
                } catch (IOException e) {
                    Log.error("ClientGameMode", "Network thread error: " + e.getMessage());
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.error("ClientGameMode", "Network thread exception: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            Log.info("ClientGameMode", "Network thread exiting");
        }, "ClientNetworkThread");

        networkThread.setDaemon(false);
        networkThread.start();
    }

    private void setupNetworkListeners() {
        // Migrate to NetworkManager pattern - handle messages directly
        NetworkManager.onReceive(PlayerAssignmentUpdate.class, receivedPlayerId -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "=== RECEIVED PlayerAssignmentUpdate ===");
                Log.info("ClientGameMode", "Assigned player ID: " + receivedPlayerId.playerId);
                Log.info("ClientGameMode", "Calling setLocalPlayer()...");
                setLocalPlayer(receivedPlayerId.playerId);
                playerAssigned = true;
                Log.info("ClientGameMode", "=== PlayerAssignmentUpdate COMPLETE ===");
            });
        });

        // DEPRECATED: PlayerObjectRosterUpdate is no longer sent by server
        // Players are now transferred via MapTransferPayload during map transfer
        // This handler remains for backward compatibility but will not receive messages
        NetworkManager.onReceive(PlayerObjectRosterUpdate.class, roster -> {
            Gdx.app.postRunnable(() -> {
                Log.warn("ClientGameMode", "Received deprecated PlayerObjectRosterUpdate - " +
                        "players should come from map transfer");
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

        NetworkManager.onReceive(PlayerDisconnectUpdate.class, disconnectUpdate -> {
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

        NetworkManager.onReceive(PlayerUpdate.class, playerUpdate -> {
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
                    Log.info("ClientGameMode", "Creating new remote player for player " + playerUpdate.playerId);
                    targetPlayer = new PlayerObject(playerUpdate.playerId); // client mode - need graphics
                    gameWorld.getGameObjectManager().activePlayers.add(targetPlayer);
                    gameWorld.getGameObjectManager().add(targetPlayer);

                    // Initialize physics collision body for remote player
                    // This allows the local player to collide with remote players
                    targetPlayer.initializeRemotePhysics(gameWorld.getMapManager(), 1.0f, 5.0f);
                }

                targetPlayer.setPosition(new Vector3(playerUpdate.x, playerUpdate.y, playerUpdate.z));
                targetPlayer.setYaw(playerUpdate.yaw);
                targetPlayer.setPitch(playerUpdate.pitch);

                // Update physics body position to match network position
                targetPlayer.updateRemotePhysicsPosition();
            });
        });

        NetworkManager.onReceive(PlayerImpulseMessage.class, impulseMessage -> {
            Gdx.app.postRunnable(() -> {
                // Find the target player (could be local or remote)
                PlayerObject targetPlayer = null;
                String playerType = "UNKNOWN";

                // First check if this is the local player
                PlayerObject localPlayer = gameWorld.getGameObjectManager().localPlayer;
                if (localPlayer != null && localPlayer.entityId.equals(impulseMessage.playerId)) {
                    targetPlayer = localPlayer;
                    playerType = "LOCAL";
                } else {
                    // Search remote players in activePlayers list
                    for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                        if (player.entityId.equals(impulseMessage.playerId)) {
                            targetPlayer = player;
                            playerType = "REMOTE";
                            break;
                        }
                    }
                }

                if (targetPlayer != null) {
                    Vector3 impulse = new Vector3(impulseMessage.impulseX, impulseMessage.impulseY, impulseMessage.impulseZ);
                    Log.info("ClientGameMode", "Received impulse for " + playerType + " player " +
                             impulseMessage.playerId + ": " + impulse);
                    targetPlayer.applyImpulse(impulse);
                } else {
                    Log.warn("ClientGameMode", "Received impulse for unknown player: " + impulseMessage.playerId);
                }
            });
        });
    }

    /**
     * Set up physics for a player if not already initialized.
     * This can be called either when the player is first assigned, or after the map is loaded.
     * @param player The player to set up physics for
     */
    public void setupPlayerPhysics(PlayerObject player) {
        if (player == null) {
            Log.error("ClientGameMode", "Cannot setup physics for null player");
            return;
        }

        if (gameWorld.getMapManager() == null) {
            Log.error("ClientGameMode", "Cannot setup physics - MapManager is NULL!");
            return;
        }

        Log.info("ClientGameMode", "Setting up physics for player: " + player.entityId);
        Log.info("ClientGameMode", "Current PlayerController state: " + (gameWorld.getMapManager().getPlayerController() != null ? "EXISTS" : "NULL"));

        // Check if player physics is already set up
        if (gameWorld.getMapManager().getPlayerController() == null) {
            Log.info("ClientGameMode", "PlayerController is NULL - creating new physics...");
            // Add player to physics world only if not already added
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 25, 15);
            ArrayList<MapHint> spawnHints = gameWorld.getMapManager().getAllHintsOfType(SpawnPointHint.class);
            if (!spawnHints.isEmpty()) {
                MapTile spawnTile = gameWorld.getMapManager().getTile(spawnHints.get(0).tileLookupKey);
                if (spawnTile != null) {
                    // Spawn directly on the tile - physics will handle proper ground positioning
                    playerStart = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);
                    Log.info("ClientGameMode", "Found spawn tile at: " + playerStart);
                }
            }
            Log.info("ClientGameMode", "Calling addPlayer() at position: " + playerStart);
            gameWorld.getMapManager().addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);
            Log.info("ClientGameMode", "addPlayer() complete, PlayerController now: " + (gameWorld.getMapManager().getPlayerController() != null ? "EXISTS" : "STILL NULL!"));
        } else {
            Log.info("ClientGameMode", "PlayerController already exists, skipping physics creation");
        }

        // Link the PlayerObject to the physics character controller
        Log.info("ClientGameMode", "Linking PlayerObject to physics...");
        player.setGameMap(gameWorld.getMapManager());
        player.setCharacterController(gameWorld.getMapManager().getPlayerController());
        Log.info("ClientGameMode", "PlayerObject.characterController set to: " + (player.getCharacterController() != null ? "EXISTS" : "NULL"));

        // Sync PlayerObject position to physics (in case physics was created at different position)
        if (gameWorld.getMapManager().getPlayerController() != null) {
            Vector3 physicsPos = gameWorld.getMapManager().getPlayerController()
                .getGhostObject()
                .getWorldTransform()
                .getTranslation(new Vector3());
            player.setPosition(physicsPos);
            Log.info("ClientGameMode", "Synced player position to physics: " + physicsPos);
        }

        Log.info("ClientGameMode", "Player physics setup complete | ID: " + player.entityId + ", position: " + player.getPosition() + ", controller: " + (player.getCharacterController() != null) + ", flyMode: " + player.isFlyModeEnabled());
    }

    /**
     * Get the local player object.
     * @return The local player, or null if not assigned yet
     */
    public PlayerObject getLocalPlayer() {
        return gameWorld.getGameObjectManager().localPlayer;
    }

    private void setLocalPlayer(String localPlayerId) {
        Log.info("ClientGameMode", "Setting local player ID: " + localPlayerId);
        Log.info("ClientGameMode", "Current activePlayers count: " + gameWorld.getGameObjectManager().activePlayers.size());

        // Debug: Print all active players
        for (int i = 0; i < gameWorld.getGameObjectManager().activePlayers.size(); i++) {
            PlayerObject p = gameWorld.getGameObjectManager().activePlayers.get(i);
            Log.info("ClientGameMode", "activePlayers[" + i + "] = " + (p != null ? p.entityId : "NULL"));
        }

        // NORMAL FLOW: Player should already exist from map transfer
        // Check if a player with this ID already exists in activePlayers (from map transfer payload)
        PlayerObject existingPlayer = null;
        for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
            if (player != null && player.entityId != null && player.entityId.equals(localPlayerId)) {
                existingPlayer = player;
                break;
            }
        }

        Log.info("ClientGameMode", "Found existing player: " + (existingPlayer != null ? "YES" : "NO"));

        if (existingPlayer != null) {
            // Found existing player from map transfer, reuse it as the local player
            Log.info("ClientGameMode", "Found existing player from map transfer, reusing as local player");
            Log.info("ClientGameMode", "Existing player graphics initialized: " + existingPlayer.isGraphicsInitialized());
            Log.info("ClientGameMode", "Existing player current position: " + existingPlayer.getPosition());

            // Set this existing player as the local player instead of creating a new one
            gameWorld.getGameObjectManager().localPlayer = existingPlayer;

            // Set up the existing player for local control with full physics setup
            if (gameWorld.getMapManager() != null) {
                Log.info("ClientGameMode", "MapManager exists, setting up physics...");
                setupPlayerPhysics(existingPlayer);
            } else {
                Log.error("ClientGameMode", "MapManager is NULL! Cannot set up physics! Will retry after map is loaded.");
            }

            inputController.setPossessionTarget(existingPlayer);
            if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                // Don't override input processor - let main class handle multiplexer
                Log.info("ClientGameMode", "Would set input processor to inputController (reused player)");
            }

            Log.info("ClientGameMode", "Successfully reused existing player as local player ID: " + localPlayerId);

            return; // Exit early since we've set up the local player
        }

        // FALLBACK: Create a fresh local player only if none exists (shouldn't happen with map transfer)
        if (gameWorld.getGameObjectManager().localPlayer == null) {
            Log.warn("ClientGameMode", "No existing player found in map transfer - using fallback creation");
            Log.info("ClientGameMode", "Creating new local player object");
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
                    // Don't override input processor - let main class handle multiplexer
                    Log.info("ClientGameMode", "Would set input processor to inputController (fresh player)");
                }
                Log.info("ClientGameMode", "Successfully created and set local player ID to: " + localPlayerId);
            } else {
                Log.error("ClientGameMode", "Failed to create local player controller");
            }
        }
    }


    @Override
    public void update(float deltaTime) throws IOException {
        // Update state management system first


        // Network updates are now handled in separate thread
        // This method only handles game world updates on main thread

        if (!active) {
            return;
        }

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
                camera = new PerspectiveCamera(Constants.CAMERA_FOV, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                camera.near = Constants.CAMERA_NEAR_PLANE;
                camera.far = Constants.CAMERA_FAR_PLANE;
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

    /**
     * Get the input processor for this game mode.
     * Used by the input multiplexer to handle both UI and game input.
     */
    public com.badlogic.gdx.InputProcessor getInputProcessor() {
        // Disable input during map regeneration to prevent player movement
        if (inputDisabled) {
            return null;
        }
        return inputController instanceof com.badlogic.gdx.InputProcessor ?
               (com.badlogic.gdx.InputProcessor) inputController : null;
    }

    private void sendPositionUpdate() {
        // Check if network updates are paused (during map regeneration)
        if (networkUpdatesPaused) {
            return; // Skip sending position updates to prevent Kryo serialization errors
        }

        if (gameClient != null) {
            String playerId = getLocalPlayerId();
            Vector3 position = getLocalPlayerPosition();
            GameObjectManager gom = gameWorld.getGameObjectManager();

            if (playerId != null && position != null && gom.localPlayer != null) {
                float yaw = gom.localPlayer.getYaw();
                float pitch = gom.localPlayer.getPitch();
                PlayerUpdate update = new PlayerUpdate(playerId, position, yaw, pitch);
                gameClient.sendUDP(update);

                // Debug: Track actual position update frequency (only incremented when actually sent)
                lastPositionUpdateCount++;
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPositionUpdateTime >= 1000) {
                    Log.info("ClientGameMode", "Actual position updates per second: " + lastPositionUpdateCount);
                    lastPositionUpdateCount = 0;
                    lastPositionUpdateTime = currentTime;
                }
            }
        }
    }

    private void checkNetworkBufferStatus() {
        if (gameClient != null && gameClient.getClient() != null) {
            try {
                // Get the underlying KryoNet client
                com.esotericsoftware.kryonet.Client client = gameClient.getClient();

                // Check if client is connected first
                if (client.isConnected()) {
                    // KryoNet may not expose direct buffer size methods, but we can check connection state
                    // and use reflection or indirect methods to monitor network health

                    // Check return trip time which can indicate network congestion
                    int returnTripTime = client.getReturnTripTime();

                    // Log network health indicators
                    if (returnTripTime > 100) {
                        Log.warn("ClientGameMode", "High network latency detected: " + returnTripTime + "ms RTT - possible congestion");
                    } else if (returnTripTime > 0) {
                        Log.info("ClientGameMode", "Network RTT: " + returnTripTime + "ms (healthy)");
                    }

                    // Additional connection info
                    String remoteAddress = "unknown";
                    try {
                        if (client.getRemoteAddressTCP() != null) {
                            remoteAddress = client.getRemoteAddressTCP().toString();
                        }
                    } catch (Exception e) {
                        // Ignore address lookup errors
                    }

                    Log.info("ClientGameMode", "Connection status - Address: " + remoteAddress +
                            ", Connected: " + client.isConnected());
                }
            } catch (Exception e) {
                Log.warn("ClientGameMode", "Could not check network status: " + e.getMessage());
            }
        }
    }


    /**
     * Called by GameClient when a chunk is received - update state context with progress
     */
    // Cache for chunk counts to avoid expensive state operations
    private volatile int cachedChunksReceived = 0;
    private volatile long cachedBytesReceived = 0L;
    private volatile String cachedMapId = null;
    private volatile Integer cachedTotalChunks = null;

//    public void onChunkReceived(String mapId, int chunkIndex, byte[] chunkData) {
//
//        // Show progress during any map downloading state OR during initial connection (LOBBY)
//        if (currentState == GameState.MAP_REGENERATION_DOWNLOADING ||
//            currentState == GameState.MAP_TRANSFER_DOWNLOADING ||
//            currentState == GameState.LOBBY) {
//
//            // FAST PATH: Use cached values and minimal state operations to prevent buffer backup
//
//            // Reset cache if mapId changed (new transfer)
//            if (!mapId.equals(cachedMapId)) {
//                cachedMapId = mapId;
//                cachedChunksReceived = 0;
//                cachedBytesReceived = 0L;
//                cachedTotalChunks = null;
//
//                // Load totalChunks from state context once
//                StateContext context = stateManager.getStateContext();
//                cachedTotalChunks = context.getStateData("total_chunks_" + mapId, Integer.class);
//                if (cachedTotalChunks == null || cachedTotalChunks <= 0) {
//                    cachedTotalChunks = gameClient.getCurrentTransferTotalChunks(mapId);
//                }
//            }
//
//            // Fast increment (no state operations on network thread)
//            cachedChunksReceived++;
//            cachedBytesReceived += chunkData.length;
//
//            // Minimal logging
//            if (cachedChunksReceived % 100 == 0) {
//                float progressPercent = cachedTotalChunks != null && cachedTotalChunks > 0 ?
//                    (cachedChunksReceived * 100.0f / cachedTotalChunks) : 0;
//                Log.info("ClientGameMode", "FAST CHUNK: " + mapId + " chunks=" + cachedChunksReceived + "/" + cachedTotalChunks + " (" + String.format("%.1f", progressPercent) + "%)");
//            }
//
//            // UI update with aggressive throttling to prevent buffer backup
//            if (cachedTotalChunks != null && cachedTotalChunks > 0) {
//                boolean shouldUpdateUI = (cachedChunksReceived % curly.octo.common.Constants.MAP_TRANSFER_CHUNK_DELAY == 0);
//
//                if (shouldUpdateUI) {
//                    float progress = 0.1f + (cachedChunksReceived / (float) cachedTotalChunks) * 0.8f;
//
//                    // Minimal UI posting - cache values to avoid capture issues
//                    final float finalProgress = progress;
//                    final int finalChunksReceived = cachedChunksReceived;
//                    final int finalTotalChunks = cachedTotalChunks;
//
//                    // Use ONLY the static method for immediate, consistent UI updates
//                    // Avoid competing updates that cause UI shaking/oscillation
//                    float chunkProgress = (float) finalChunksReceived / finalTotalChunks;
//                    curly.octo.ui.screens.MapTransferScreen.updateProgressDirect(
//                        currentState, chunkProgress,
//                        String.format("Received %d/%d chunks...", finalChunksReceived, finalTotalChunks));
//
//                    // Periodic state sync (every 100 chunks) to maintain consistency
//                    if (cachedChunksReceived % 100 == 0) {
//                        StateContext context = stateManager.getStateContext();
//                        context.setStateData("chunks_received_" + mapId, cachedChunksReceived);
//                        context.setStateData("chunks_received", cachedChunksReceived);
//                    }
//                }
//            }
//        }
//    }

    /**
     * Called by GameClient when map transfer completes - update state context
     */
//    public void onMapTransferComplete(String mapId) {
//        GameState currentState = stateManager.getCurrentState();
//        if (currentState == GameState.MAP_REGENERATION_DOWNLOADING ||
//            currentState == GameState.MAP_TRANSFER_DOWNLOADING ||
//            currentState == GameState.LOBBY) {
//            StateContext context = stateManager.getStateContext();
//
//            // Force immediate UI synchronization with final chunk count
//            if (mapId.equals(cachedMapId) && cachedTotalChunks != null) {
//                final int finalChunksReceived = cachedChunksReceived;
//                final int finalTotalChunks = cachedTotalChunks;
//
//                // Calculate final progress (should be ~95% of download phase)
//                float finalProgress = 0.1f + (finalChunksReceived / (float) finalTotalChunks) * 0.8f;
//
//                Log.info("ClientGameMode", "TRANSFER COMPLETE: Forcing final UI sync - " + finalChunksReceived + "/" + finalTotalChunks + " chunks (" + String.format("%.1f%%", finalProgress * 100) + ")");
//
//                // Use static method for immediate UI update with current state
//                MapTransferScreen.updateProgressDirect(currentState, finalProgress * 0.9f / 0.6f,
//                    "Map transfer complete, processing...");
//
//                // Also update state manager (immediate, no postRunnable to avoid queue delay)
//                stateManager.updateProgress(finalProgress, "Map transfer complete, processing...");
//            }
//
//            context.setStateData("received_map_id", mapId);
//            // Don't set download_complete = true here - let the mapReceivedListener do it
//            // after the map is actually deserialized and stored
//            Log.info("ClientGameMode", "Map transfer complete, waiting for deserialization");
//        }
//    }

    /**
     * Set the map regeneration listener to notify about seed changes.
     */
    public void setMapRegenerationListener(MapRegenerationListener listener) {
        this.mapRegenerationListener = listener;
    }
}
