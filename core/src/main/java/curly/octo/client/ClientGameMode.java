package curly.octo.client;

import curly.octo.common.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.clientStates.StateManager;
import curly.octo.common.character.NPCBrain;
import curly.octo.common.character.PlayerBrain;
import curly.octo.common.character.WalkingCharacter;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.map.hints.SpawnPointHint;
import curly.octo.common.network.messages.*;
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

    // Static flag to prevent duplicate handler registration (NetworkManager uses static handlers)
    private static boolean networkListenersSetup = false;

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

    // NPC sync broadcasting (for elected client)
    private float npcSyncTimer = 0f;
    private static final float NPC_SYNC_INTERVAL = 5.0f; // 0.2 FPS (once every 5 seconds) to reduce network load
    private final java.util.Map<String, Vector3> lastSyncedNPCPositions = new java.util.HashMap<>(); // Track positions to skip stationary NPCs

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
        if (networkListenersSetup) {
            return;
        }

        Log.info("ClientGameMode", "Setting up network listeners");

        NetworkManager.onReceive(PlayerAssignmentUpdate.class, msg -> Gdx.app.postRunnable(() -> {
            Log.info("ClientGameMode", "[SPAWN_DEBUG] Received PlayerAssignmentUpdate for player ID: " + msg.playerId);
            setLocalPlayer(msg.playerId);
            playerAssigned = true;
        }));

        NetworkManager.onReceive(PlayerObjectRosterUpdate.class, roster -> {
            Gdx.app.postRunnable(() -> {
                HashSet<String> currentPlayers = new HashSet<>();
                for (WalkingCharacter player : gameWorld.getGameObjectManager().activePlayers) {
                    currentPlayers.add(player.entityId);
                }

                for (WalkingCharacter player : roster.players) {
                    if (!currentPlayers.contains(player.entityId)) {
                        gameWorld.getGameObjectManager().activePlayers.add(player);
                        gameWorld.getGameObjectManager().add(player);
                    }
                }
            });
        });

        NetworkManager.onReceive(PlayerDisconnectUpdate.class, disconnectUpdate -> {
            Gdx.app.postRunnable(() -> {
                WalkingCharacter playerToRemove = null;
                for (WalkingCharacter player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.entityId.equals(disconnectUpdate.playerId)) {
                        playerToRemove = player;
                        break;
                    }
                }

                if (playerToRemove != null) {
                    gameWorld.getGameObjectManager().activePlayers.remove(playerToRemove);
                    gameWorld.getGameObjectManager().remove(playerToRemove);
                }
            });
        });

        NetworkManager.onReceive(PlayerUpdate.class, playerUpdate -> {
            Gdx.app.postRunnable(() -> {
                String localId = getLocalPlayerId();
                if (localId != null && playerUpdate.playerId.equals(localId)) {
                    return;
                }

                WalkingCharacter targetPlayer = null;
                for (WalkingCharacter player : gameWorld.getGameObjectManager().activePlayers) {
                    if (player.entityId.equals(playerUpdate.playerId)) {
                        targetPlayer = player;
                        break;
                    }
                }

                if (targetPlayer == null) {
                    targetPlayer = new WalkingCharacter(playerUpdate.playerId, Constants.PLAYER_HEIGHT, 1.0f);
                    gameWorld.getGameObjectManager().activePlayers.add(targetPlayer);
                    gameWorld.getGameObjectManager().add(targetPlayer);

                    if (gameWorld.getMapManager() != null && gameWorld.getMapManager().isPhysicsInitialized()) {
                        targetPlayer.initializeRemotePhysics(gameWorld.getMapManager(), 1.0f, 5.0f);
                    }
                }

                targetPlayer.setPosition(new Vector3(playerUpdate.x, playerUpdate.y, playerUpdate.z));
                targetPlayer.setYaw(playerUpdate.yaw);
                targetPlayer.setPitch(playerUpdate.pitch);
                targetPlayer.updateRemotePhysicsPosition();
            });
        });

        NetworkManager.onReceive(PlayerImpulseMessage.class, impulseMessage -> {
            Gdx.app.postRunnable(() -> {
                WalkingCharacter targetPlayer = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(impulseMessage.playerId);
                if (targetPlayer != null) {
                    targetPlayer.applyImpulse(new Vector3(impulseMessage.impulseX, impulseMessage.impulseY, impulseMessage.impulseZ));
                }
            });
        });

        NetworkManager.onReceive(NPCElectionMessage.class, electionMessage -> {
            Gdx.app.postRunnable(() -> {
                String myClientId = curly.octo.Main.clientUniqueId != null ? curly.octo.Main.clientUniqueId.uniqueId : "NULL";
                boolean iAmAuthority = myClientId.equals(electionMessage.electedClientId);

                if (iAmAuthority) {
                    curly.octo.common.NPCAuthorityManager.addAuthority(electionMessage.npcId);
                } else {
                    curly.octo.common.NPCAuthorityManager.removeAuthority(electionMessage.npcId);
                }

                WalkingCharacter npc = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(electionMessage.npcId);
                if (npc != null) {
                    configureNPCAuthority(npc);
                }
            });
        });

        NetworkManager.onReceive(NPCInstructionMessage.class, instructionMessage -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "[DEBUG_NPC] Received NPCInstructionMessage for NPC: " + instructionMessage.npcId);
                WalkingCharacter npc = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(instructionMessage.npcId);
                if (npc != null) {
                    configureNPCAuthority(npc);
                    npc.executeInstruction(instructionMessage);
                }
            });
        });

        NetworkManager.onReceive(NPCSyncMessage.class, this::applySyncCorrections);

        networkListenersSetup = true;
    }

    public void setupPlayerPhysics(WalkingCharacter player) {
        if (player == null || gameWorld.getMapManager() == null) {
            return;
        }
        if (!player.isPhysicsInitialized()) {
            player.initializePhysics(gameWorld.getMapManager().dynamicsWorld, player.getCharacterHeight(), player.getCharacterWidth());
        }
        player.setGameMap(gameWorld.getMapManager());
    }

    public WalkingCharacter getLocalPlayer() {
        return gameWorld.getGameObjectManager().localPlayer;
    }

    private void setLocalPlayer(String localPlayerId) {
        Log.info("ClientGameMode", "[SPAWN_DEBUG] Setting local player ID: " + localPlayerId);
        WalkingCharacter existingPlayer = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(localPlayerId);

        if (existingPlayer != null) {
            Log.info("ClientGameMode", "[SPAWN_DEBUG] Reusing existing player from map transfer.");
            gameWorld.getGameObjectManager().localPlayer = existingPlayer;
            if (gameWorld.getMapManager() != null) {
                setupPlayerPhysics(existingPlayer);
            }
        } else {
            Log.warn("ClientGameMode", "[SPAWN_DEBUG] No existing player found. Creating new one.");
            gameWorld.setupLocalPlayer();
            gameWorld.getGameObjectManager().localPlayer.entityId = localPlayerId;
        }
        inputController.setPossessionTarget(gameWorld.getGameObjectManager().localPlayer);
    }


    @Override
    public void update(float deltaTime) throws IOException {
        if (!active) {
            return;
        }

        if (inputController != null && gameWorld.getGameObjectManager().localPlayer != null && camera != null) {
            inputController.handleInput(deltaTime, gameWorld.getGameObjectManager().localPlayer, camera);
        }

        npcSyncTimer += deltaTime;
        if (npcSyncTimer >= NPC_SYNC_INTERVAL) {
            broadcastNPCSync();
            npcSyncTimer = 0f;
        }

        gameWorld.update(deltaTime);
    }

    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        if (!active) {
            return;
        }

        WalkingCharacter localPlayer = gameWorld.getGameObjectManager().localPlayer;
        if (localPlayer != null) {
            if (camera == null) {
                camera = new PerspectiveCamera(Constants.CAMERA_FOV, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                camera.near = Constants.CAMERA_NEAR_PLANE;
                camera.far = Constants.CAMERA_FAR_PLANE;
            }

            inputController.updateCamera(camera, Gdx.graphics.getDeltaTime());
            gameWorld.render(modelBatch, camera);
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
        networkRunning = false;
        if (networkThread != null) {
            try {
                networkThread.interrupt();
                networkThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        if (gameWorld != null) {
            gameWorld.dispose();
        }
        NetworkManager.clearHandlers();
        networkListenersSetup = false;
    }

    public com.badlogic.gdx.InputProcessor getInputProcessor() {
        if (inputDisabled) {
            return null;
        }
        return inputController instanceof com.badlogic.gdx.InputProcessor ?
               (com.badlogic.gdx.InputProcessor) inputController : null;
    }

    private void sendPositionUpdate() {
        if (networkUpdatesPaused || gameClient == null) {
            return;
        }

        WalkingCharacter localPlayer = getLocalPlayer();
        if (localPlayer != null) {
            PlayerUpdate update = new PlayerUpdate(localPlayer.entityId, localPlayer.getPosition(), localPlayer.getYaw(), localPlayer.getPitch());
            gameClient.sendUDP(update);
        }
    }

    private void checkNetworkBufferStatus() {
        if (gameClient != null && gameClient.getClient() != null && gameClient.getClient().isConnected()) {
            int returnTripTime = gameClient.getClient().getReturnTripTime();
            if (returnTripTime > 100) {
                Log.warn("ClientGameMode", "High network latency detected: " + returnTripTime + "ms RTT");
            }
        }
    }

    private void broadcastNPCSync() {
        if (!curly.octo.common.NPCAuthorityManager.hasAnyManagedNPCs()) {
            return;
        }

        for (String npcId : curly.octo.common.NPCAuthorityManager.getManagedNPCs()) {
            WalkingCharacter npc = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(npcId);
            if (npc == null) continue;

            Vector3 pos = npc.getPosition();
            Vector3 lastPos = lastSyncedNPCPositions.get(npc.entityId);
            if (lastPos != null && pos.dst(lastPos) < 0.05f) {
                continue;
            }

            NPCSyncMessage sync = new NPCSyncMessage(npcId, pos.x, pos.y, pos.z, npc.getYaw());
            NetworkManager.sendToServer(sync);
            lastSyncedNPCPositions.put(npc.entityId, new Vector3(pos));
        }
    }

    private void configureNPCAuthority(WalkingCharacter npc) {
        boolean iAmAuthority = curly.octo.common.NPCAuthorityManager.isMyNPC(npc.entityId);
        if (iAmAuthority) {
            curly.octo.common.NPCAuthorityManager.setPathCompleteCallback(npc.entityId, () -> sendNPCPathCompletion(npc));
        } else {
            curly.octo.common.NPCAuthorityManager.setPathCompleteCallback(npc.entityId, null);
        }
    }

    private void sendNPCPathCompletion(WalkingCharacter npc) {
        NPCInstructionMessage currentInstruction = npc.getCurrentInstruction();
        if (currentInstruction == null) return;

        NPCPathCompleteMessage completionMsg = new NPCPathCompleteMessage(npc.entityId, npc.getPosition().x, npc.getPosition().y, npc.getPosition().z, npc.getYaw(), currentInstruction.instructionId);
        NetworkManager.sendToServer(completionMsg);
        lastSyncedNPCPositions.put(npc.entityId, new Vector3(npc.getPosition()));
    }

    private void applySyncCorrections(NPCSyncMessage sync) {
        if (curly.octo.common.NPCAuthorityManager.isMyNPC(sync.npcId)) {
            return;
        }

        WalkingCharacter npc = (WalkingCharacter) gameWorld.getGameObjectManager().getObjectById(sync.npcId);
        if (npc == null) return;

        Vector3 authorityPos = new Vector3(sync.position[0], sync.position[1], sync.position[2]);
        npc.applySyncCorrection(authorityPos, sync.yaw, -1L);
    }
}
