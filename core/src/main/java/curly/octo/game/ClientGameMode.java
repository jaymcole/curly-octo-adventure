package curly.octo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
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

    public ClientGameMode(GameWorld gameWorld, String host) {
        this.gameWorld = gameWorld;
        this.host = host;
    }

    @Override
    public void initialize() {
        try {
            Log.info("ClientGameMode", "Initializing client mode");

            gameClient = new GameClient(host);
            setupNetworkListeners();

            // Connect to server
            gameClient.connect(5000);

            Log.info("ClientGameMode", "Connected to server at " + host);

        } catch (IOException e) {
            Log.error("ClientGameMode", "Failed to connect to server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupNetworkListeners() {
        // Map received listener
        gameClient.setMapReceivedListener(receivedMap -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "Received map with size: " +
                    receivedMap.getWidth() + "x " +
                    receivedMap.getHeight() + "y " +
                    receivedMap.getDepth() + "z");

                gameWorld.setMap(receivedMap);
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
                HashSet<Long> currentPlayers = new HashSet<>();
                for (PlayerController player : gameWorld.getPlayers()) {
                    currentPlayers.add(player.getPlayerId());
                }

                for (PlayerController player : roster.players) {
                    if (!currentPlayers.contains(player.getPlayerId())) {
                        // Ensure other players don't have physics bodies
                        player.setGameMap(null);

                        // Double-check that player has a light, create one if it doesn't
                        if (player.getPlayerLight() == null) {
                            Log.warn("ClientGameMode", "Player " + player.getPlayerId() + " has no light, creating one");
                            // The getPlayerLight() method should auto-create it, but let's be explicit
                            player.getPlayerLight(); // This will trigger creation in the getter
                        }

                        gameWorld.getPlayers().add(player);

                        // Add the new player's light to the environment
                        gameWorld.addPlayerToEnvironment(player);
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
                for (PlayerController player : gameWorld.getPlayers()) {
                    if (player.getPlayerId() == disconnectUpdate.playerId) {
                        playerToRemove = player;
                        break;
                    }
                }

                if (playerToRemove != null) {
                    // Remove player light from environment
                    gameWorld.removePlayerFromEnvironment(playerToRemove);

                    // Remove player from list
                    gameWorld.getPlayers().remove(playerToRemove);

                    Log.info("ClientGameMode", "Removed disconnected player " + disconnectUpdate.playerId + " from client");
                } else {
                    Log.warn("ClientGameMode", "Could not find player " + disconnectUpdate.playerId + " to remove");
                }
            });
        });

        // Player update listener
        gameClient.setPlayerUpdateListener(playerUpdate -> {
            Gdx.app.postRunnable(() -> {
                Log.info("ClientGameMode", "Received position update for player " + playerUpdate.playerId + ": " +
                    playerUpdate.x + ", " + playerUpdate.y + ", " + playerUpdate.z);

                // Skip updates for the local player
                if (playerUpdate.playerId == gameWorld.getLocalPlayerId()) {
                    return;
                }

                // Find the player in our list
                PlayerController targetPlayer = null;
                for (PlayerController player : gameWorld.getPlayers()) {
                    if (player.getPlayerId() == playerUpdate.playerId) {
                        targetPlayer = player;
                        break;
                    }
                }

                // If player not found, create a new one
                if (targetPlayer == null) {
                    Log.info("ClientGameMode", "Creating new player controller for player " + playerUpdate.playerId);
                    targetPlayer = PlayerUtilities.createPlayerController(gameWorld.getRandom());
                    targetPlayer.setPlayerId(playerUpdate.playerId);
                    gameWorld.getPlayers().add(targetPlayer);
                }

                targetPlayer.setPlayerPosition(playerUpdate.x, playerUpdate.y, playerUpdate.z);

                // Ensure light position is updated and add to environment if not already there
                if (targetPlayer.getPlayerLight() != null) {
                    Log.info("ClientGameMode", "Updated player " + playerUpdate.playerId + " light to position: (" +
                        targetPlayer.getPlayerLight().position.x + "," + targetPlayer.getPlayerLight().position.y + "," + targetPlayer.getPlayerLight().position.z + ")");
                } else {
                    Log.warn("ClientGameMode", "Player " + playerUpdate.playerId + " has no light during position update");
                }
            });
        });
    }

    private void setLocalPlayer(long localPlayerId) {
        Log.info("ClientGameMode", "Setting local player ID: " + localPlayerId);

        // Create the local player if it doesn't exist
        if (gameWorld.getLocalPlayerController() == null) {
            Log.info("ClientGameMode", "Creating local player controller");
            gameWorld.setupLocalPlayer();
        }

        // Set the player ID
        PlayerController localPlayer = gameWorld.getLocalPlayerController();
        if (localPlayer != null) {
            localPlayer.setPlayerId(localPlayerId);
            // Also set the localPlayerId in GameWorld
            gameWorld.setLocalPlayerId(localPlayerId);
            if (gameWorld.getMapManager() != null) {
                localPlayer.setGameMap(gameWorld.getMapManager());
            }
            Gdx.input.setInputProcessor(localPlayer);
            Log.info("ClientGameMode", "Successfully set local player ID to: " + localPlayerId);
        } else {
            Log.error("ClientGameMode", "Failed to create local player controller - getLocalPlayerController() returned null");
            // Try to create it again
            gameWorld.setupLocalPlayer();
            localPlayer = gameWorld.getLocalPlayerController();
            if (localPlayer != null) {
                localPlayer.setPlayerId(localPlayerId);
                gameWorld.setLocalPlayerId(localPlayerId);
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

    private void checkReady() {
        if (mapReceived && playerAssigned && !active) {
            // Switch to 3D view
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000);
                    active = true;
                    Gdx.input.setInputProcessor(gameWorld.getLocalPlayerController());
                    Log.info("ClientGameMode", "Client mode activated");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    @Override
    public void update(float deltaTime) {
        if (!active) return;

        // Update game world
        gameWorld.update(deltaTime);

        // Send position updates
        if (gameWorld.shouldSendPositionUpdate()) {
            sendPositionUpdate();
        }
    }

    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        if (!active) return;

        PlayerController localPlayer = gameWorld.getLocalPlayerController();
        if (localPlayer != null) {
            gameWorld.render(modelBatch, localPlayer.getCamera());
        }
    }

    @Override
    public void resize(int width, int height) {
        PlayerController localPlayer = gameWorld.getLocalPlayerController();
        if (localPlayer != null) {
            localPlayer.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        Log.info("ClientGameMode", "Disposing client game mode...");

        // Disconnect from server
        if (gameClient != null) {
            try {
                gameClient.disconnect();
                Log.info("ClientGameMode", "Disconnected from server");
            } catch (Exception e) {
                Log.error("ClientGameMode", "Error disconnecting from server: " + e.getMessage());
            }
        }

        // Don't dispose gameWorld here - Main will handle it
        active = false;
        Log.info("ClientGameMode", "Client game mode disposed");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public long getLocalPlayerId() {
        return gameWorld.getLocalPlayerId();
    }

    private void sendPositionUpdate() {
        if (gameClient != null && gameWorld.getLocalPlayerController() != null) {
            PlayerUpdate update = new PlayerUpdate(
                gameWorld.getLocalPlayerId(),
                gameWorld.getLocalPlayerController().getPosition()
            );
            gameClient.sendUDP(update);
            Log.debug("ClientGameMode", "Sent position update: " + update.x + ", " + update.y + ", " + update.z);
        }
    }

    public GameClient getGameClient() {
        return gameClient;
    }
}
