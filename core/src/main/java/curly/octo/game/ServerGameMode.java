package curly.octo.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameServer;
import curly.octo.player.PlayerController;

import java.io.IOException;

/**
 * Server game mode that handles hosting and broadcasting to clients.
 */
public class ServerGameMode implements GameMode {

    private final GameWorld gameWorld;
    private GameServer gameServer;
    private boolean active = false;

    public ServerGameMode(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public void initialize() {
        try {
            Log.info("ServerGameMode", "Initializing server mode");

            // Initialize map and local player
            gameWorld.initializeMap();
            gameWorld.setupLocalPlayer();

            // Create and start server
            gameServer = new GameServer(gameWorld.getRandom(), gameWorld.getMapManager(), gameWorld.getPlayers(), gameWorld);
            gameServer.start();

            // Switch to 3D view
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000);
                    active = true;
                    Gdx.input.setInputProcessor(gameWorld.getLocalPlayerController());
                    Log.info("ServerGameMode", "Server mode activated");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

        } catch (IOException e) {
            Log.error("ServerGameMode", "Failed to start server: " + e.getMessage());
            e.printStackTrace();
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
        Log.info("ServerGameMode", "Disposing server game mode...");

        // Stop the server
        if (gameServer != null) {
            try {
                gameServer.stop();
                Log.info("ServerGameMode", "Server stopped");
            } catch (Exception e) {
                Log.error("ServerGameMode", "Error stopping server: " + e.getMessage());
            }
        }

        // Don't dispose gameWorld here - Main will handle it
        active = false;
        Log.info("ServerGameMode", "Server game mode disposed");
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
        if (gameServer != null && gameWorld.getLocalPlayerController() != null) {
            PlayerController localPlayer = gameWorld.getLocalPlayerController();
            long localPlayerId = gameWorld.getLocalPlayerId();
            Vector3 position = localPlayer.getPosition();

            Log.info("ServerGameMode", "Sending position update for host player " + localPlayerId + " at (" +
                position.x + "," + position.y + "," + position.z + ")");

            gameServer.broadcastPlayerPosition(localPlayerId, position);
        }
    }

    public GameServer getGameServer() {
        return gameServer;
    }
}
