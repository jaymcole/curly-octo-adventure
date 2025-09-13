package curly.octo.game;

import curly.octo.Constants;
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
import curly.octo.game.regeneration.MapRegenerationCoordinator;
import curly.octo.game.regeneration.MapResourceManager;
import curly.octo.game.regeneration.NetworkEventRouter;
import curly.octo.game.regeneration.RegenerationProgressListener;
import curly.octo.game.regeneration.RegenerationPhase;

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

    // New simplified regeneration system
    private MapRegenerationCoordinator regenerationCoordinator;
    private MapResourceManager resourceManager;
    private NetworkEventRouter networkEventRouter;
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

        // Initialize new simplified regeneration system
        this.resourceManager = new MapResourceManager(this.gameWorld);
        this.regenerationCoordinator = new MapRegenerationCoordinator(resourceManager, null); // gameClient set later
        this.networkEventRouter = new NetworkEventRouter(regenerationCoordinator, null); // clientId set later
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
     * Get the regeneration coordinator for this client game mode
     */
    public MapRegenerationCoordinator getRegenerationCoordinator() {
        return regenerationCoordinator;
    }


    /**
     * Disable player input (movement, etc.) during map regeneration
     */
    public void disableInput() {
        inputDisabled = true;

        // Also disable input in the input controller to unlock cursor
        if (inputController instanceof MinimalPlayerController) {
            ((MinimalPlayerController) inputController).setInputEnabled(false);
        }

        Log.info("ClientGameMode", "Player input disabled (map regeneration)");
    }

    /**
     * Re-enable player input after map regeneration completes
     */
    public void enableInput() {
        inputDisabled = false;

        // Also re-enable input in the input controller
        if (inputController instanceof MinimalPlayerController) {
            ((MinimalPlayerController) inputController).setInputEnabled(true);
        }

        Log.info("ClientGameMode", "Player input enabled (map regeneration complete)");
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

            // Update coordinator with game client reference
            regenerationCoordinator = new MapRegenerationCoordinator(resourceManager, gameClient);

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
        // Setup regeneration listeners through the router
        networkEventRouter = new NetworkEventRouter(regenerationCoordinator, getLocalPlayerId());
        networkEventRouter.setupMapRegenerationListeners(gameClient);

        // Setup other gameplay listeners directly (non-regeneration)
        networkEventRouter.setupGameplayListeners(gameClient, new NetworkEventRouter.GameplayEventHandler() {
            @Override
            public void onPlayerAssignment(curly.octo.network.messages.PlayerAssignmentUpdate message) {
                handlePlayerAssignment(message);
            }

            @Override
            public void onPlayerUpdate(curly.octo.network.messages.PlayerUpdate message) {
                handlePlayerUpdate(message);
            }

            @Override
            public void onPlayerRoster(curly.octo.network.messages.PlayerObjectRosterUpdate message) {
                handlePlayerRoster(message);
            }

            @Override
            public void onPlayerDisconnect(curly.octo.network.messages.PlayerDisconnectUpdate message) {
                handlePlayerDisconnect(message);
            }

            @Override
            public void onPlayerReset(curly.octo.network.messages.PlayerResetMessage message) {
                handlePlayerReset(message);
            }
        });

        // Map received listener for both normal gameplay and regeneration
        gameClient.setMapReceivedListener(receivedMap -> {
            Gdx.app.postRunnable(() -> {
                if (regenerationCoordinator.isActive()) {
                    // During regeneration - pass to coordinator
                    Log.info("ClientGameMode", "Map received during regeneration - passing to coordinator");
                    regenerationCoordinator.onMapReady(receivedMap);
                } else {
                    // Normal map loading (not during regeneration)
                    Log.info("ClientGameMode", "Map received during normal play - processing immediately");
                    long startTime = System.currentTimeMillis();

                    gameWorld.setMap(receivedMap);

                    if (gameWorld instanceof ClientGameWorld) {
                        Log.info("ClientGameMode", "Reinitializing players for new map");
                        ((ClientGameWorld) gameWorld).reinitializePlayersAfterMapRegeneration();
                    }

                    long endTime = System.currentTimeMillis();
                    Log.info("ClientGameMode", "Map setup completed in " + (endTime - startTime) + "ms");
                    mapReceived = true;
                    checkReady();
                }
            });
        });
    }

    // Extracted handler methods for clean separation
    private void handlePlayerAssignment(curly.octo.network.messages.PlayerAssignmentUpdate receivedPlayerId) {
        Log.info("ClientGameMode", "Assigned player ID: " + receivedPlayerId.playerId);
        setLocalPlayer(receivedPlayerId.playerId);
        playerAssigned = true;
        checkReady();
    }



    private void handlePlayerRoster(curly.octo.network.messages.PlayerObjectRosterUpdate roster) {
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
    }


    private void handlePlayerDisconnect(curly.octo.network.messages.PlayerDisconnectUpdate disconnectUpdate) {
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
    }


    private void handlePlayerUpdate(curly.octo.network.messages.PlayerUpdate playerUpdate) {
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
    }



    private void handlePlayerReset(curly.octo.network.messages.PlayerResetMessage playerReset) {
        Log.info("ClientGameMode", "Received player reset for: " + playerReset.playerId);

        // Check if this is for our local player
        String localId = getLocalPlayerId();
        if (localId != null && localId.equals(playerReset.playerId)) {
            Log.info("ClientGameMode", "Resetting local player to new spawn position");

            if (gameWorld instanceof ClientGameWorld) {
                ClientGameWorld clientWorld = (ClientGameWorld) gameWorld;
                clientWorld.resetLocalPlayerToSpawn(
                    playerReset.getSpawnPosition(),
                    playerReset.spawnYaw
                );
            }
        } else {
            Log.info("ClientGameMode", "Player reset for remote player: " + playerReset.playerId);

            // Find and reset remote player
            for (PlayerObject player : gameWorld.getGameObjectManager().activePlayers) {
                if (player.entityId.equals(playerReset.playerId)) {
                    player.setPosition(playerReset.getSpawnPosition());
                    player.setYaw(playerReset.spawnYaw);
                    player.resetPhysicsState();
                    Log.info("ClientGameMode", "Reset remote player " + playerReset.playerId);
                    break;
                }
            }
        }
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
                            playerStart = new Vector3(spawnTile.x, spawnTile.y + 5f, spawnTile.z);
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
                        playerStart = new Vector3(spawnTile.x, spawnTile.y + 5f, spawnTile.z);
                    }
                }
                existingPlayer.setPosition(new Vector3(playerStart.x, playerStart.y, playerStart.z));
            }

            inputController.setPossessionTarget(existingPlayer);
            if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                // Don't override input processor - let main class handle multiplexer
                Log.info("ClientGameMode", "Would set input processor to inputController (reused player)");
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
                    // Don't override input processor - let main class handle multiplexer
                    Log.info("ClientGameMode", "Would set input processor to inputController (fresh player)");
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
                active = true;

                if (gameWorld.getGameObjectManager().localPlayer != null) {
                    inputController.setPossessionTarget(gameWorld.getGameObjectManager().localPlayer);
                    if (inputController instanceof com.badlogic.gdx.InputProcessor) {
                        // Don't override input processor - let main class handle multiplexer
                        Log.info("ClientGameMode", "Would set input processor to inputController (checkReady)");
                    }
                    Log.info("ClientGameMode", "Client mode activated successfully");
                } else {
                    Log.error("ClientGameMode", "Local player object is null, cannot activate");
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

        // Skip input handling and game world updates during map regeneration
        if (regenerationCoordinator.isActive()) {
            // Skip all game updates during regeneration to prevent interference
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

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public GameWorld getGameWorld() {
        return gameWorld;
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
        // Check if regeneration is active (skip updates to prevent issues)
        if (regenerationCoordinator.isActive()) {
            return; // Skip sending position updates during regeneration
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
                }
            } catch (Exception e) {
                Log.warn("ClientGameMode", "Could not check network status: " + e.getMessage());
            }
        }
    }

    // Old transfer methods removed - now handled by NetworkEventRouter and MapRegenerationCoordinator

    /**
     * Set the map regeneration listener to notify about seed changes.
     * Also connect it to the regeneration coordinator for progress updates.
     */
    public void setMapRegenerationListener(MapRegenerationListener listener) {
        this.mapRegenerationListener = listener;

        // Connect the listener to the coordinator for progress updates
        if (regenerationCoordinator != null) {
            regenerationCoordinator.addProgressListener(new RegenerationProgressListener() {
                @Override
                public void onPhaseChanged(RegenerationPhase phase, String message) {
                    // Phase changes are handled by UI directly
                }

                @Override
                public void onProgressChanged(float progress) {
                    // Progress changes are handled by UI directly
                }

                @Override
                public void onCompleted() {
                    // Re-enable input when regeneration completes
                    enableInput();
                }

                @Override
                public void onError(String errorMessage) {
                    Log.error("ClientGameMode", "Regeneration error: " + errorMessage);
                    // Re-enable input on error to allow user to retry/exit
                    enableInput();
                }
            });

            // Also connect for seed change notifications
            regenerationCoordinator.addProgressListener(new RegenerationProgressListener() {
                @Override
                public void onPhaseChanged(RegenerationPhase phase, String message) {
                    if (phase == RegenerationPhase.CLEANUP && mapRegenerationListener != null) {
                        // Disable input at start of regeneration
                        disableInput();
                        // Notify about seed change
                        long newSeed = regenerationCoordinator.getNewMapSeed();
                        if (newSeed != 0) {
                            mapRegenerationListener.onMapSeedChanged(newSeed);
                        }
                    }
                }

                @Override
                public void onProgressChanged(float progress) {}

                @Override
                public void onCompleted() {}

                @Override
                public void onError(String errorMessage) {}
            });
        }
    }
}
