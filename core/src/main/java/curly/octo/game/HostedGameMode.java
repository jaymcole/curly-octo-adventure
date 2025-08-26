package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Hosted game mode that runs both a server and connects as a client to localhost.
 * This separates the server logic from the host player, making all players equal.
 */
public class HostedGameMode implements GameMode {

    private final HostGameWorld serverGameWorld;
    private final java.util.Random random;
    private GameServer gameServer;
    private ClientGameMode clientGameMode;
    private boolean active = false;
    private boolean serverStarted = false;

    public HostedGameMode(java.util.Random random) {
        this.random = random;
        // Server GameWorld should not run physics or graphics - only handles map and network coordination
        this.serverGameWorld = new HostGameWorld(random);
    }

    @Override
    public void initialize() {
        try {
            Log.info("HostedGameMode", "Initializing hosted mode");

            // Initialize map for server (no rendering components)
            serverGameWorld.initializeHostMap();

            // Create and start server with its own player list (not shared with client)
            gameServer = new GameServer(
                serverGameWorld.getRandom(),
                serverGameWorld.getMapManager(),
                new ArrayList<>(), // Server gets its own independent player list
                serverGameWorld
            );
            gameServer.start();
            serverStarted = true;

            Log.info("HostedGameMode", "Server started, now connecting as client to localhost");

            // Brief wait for server to be ready
            Thread.sleep(50);

            // Create client mode to connect to our own server (with its own GameWorld)
            clientGameMode = new ClientGameMode("localhost", random);
            clientGameMode.initialize();

            Log.info("HostedGameMode", "Hosted mode initialization complete");

        } catch (IOException e) {
            Log.error("HostedGameMode", "Failed to start server: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("HostedGameMode", "Interrupted during initialization: " + e.getMessage());
        }
    }

    @Override
    public void update(float deltaTime) throws IOException {
        if (!serverStarted) return;
        serverGameWorld.update(deltaTime);
        if (!active && clientGameMode != null) {
            active = true;
            Log.info("HostedGameMode", "Hosted mode activated (server running)");
        }
    }

    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        Log.warn("HostedGameMode", "render() called on HostedGameMode - this should not happen");
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void dispose() {
        Log.info("HostedGameMode", "Disposing hosted game mode...");

        // Dispose client first
        if (clientGameMode != null) {
            try {
                clientGameMode.dispose();
                Log.info("HostedGameMode", "Client disposed");
            } catch (Exception e) {
                Log.error("HostedGameMode", "Error disposing client: " + e.getMessage());
            }
        }

        // Stop the server
        if (gameServer != null) {
            try {
                gameServer.stop();
                Log.info("HostedGameMode", "Server stopped");
            } catch (Exception e) {
                Log.error("HostedGameMode", "Error stopping server: " + e.getMessage());
            }
        }

        active = false;
        serverStarted = false;
        Log.info("HostedGameMode", "Hosted game mode disposed");
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public GameWorld getGameWorld() {
        // HostedGameMode returns its server GameWorld
        // For rendering, Main should use the ClientGameMode's GameWorld directly
        return serverGameWorld;
    }

    public GameServer getGameServer() {
        return gameServer;
    }

    public ClientGameMode getClientGameMode() {
        return clientGameMode;
    }
}
