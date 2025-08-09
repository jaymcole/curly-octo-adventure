package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.*;
import curly.octo.ui.LobbyUI;
import curly.octo.ui.DebugUI;
import curly.octo.network.Network;

import java.io.IOException;
import java.util.Random;

/**
 * Main game class with network setup UI.
 * Delegates game logic to specific game modes (Server/Client).
 */
public class Main extends ApplicationAdapter implements LobbyUI.LobbyListener, DebugUI.DebugListener {

    private Random random;
    private ModelBatch modelBatch;

    // UI Components
    private LobbyUI lobbyUI;
    private DebugUI debugUI;
    private boolean showLobby = true;

    // Game Components
    private GameMode currentGameMode;
    private ThreadedHostedGameMode hostedGameMode;
    private ThreadedClientGameMode clientGameMode;

    public Main() {
    }

    // LobbyUI.LobbyListener implementation
    @Override
    public void onStartServer() {
        Log.info("Main", "Starting server mode");
        startServerMode();
    }

    @Override
    public void onConnectToServer(String host) {
        Log.info("Main", "Connecting to server: " + host);
        startClientMode(host);
    }

    private void startServerMode() {
        // Dispose previous game modes
        disposePreviousGameModes();

        hostedGameMode = new ThreadedHostedGameMode(random);
        hostedGameMode.initialize();
        
        // In hosted mode, we get the client mode directly for rendering
        // The HostedGameMode is already threaded, so we wrap for consistent interface
        ClientGameMode rawClientMode = hostedGameMode.getClientGameMode();
        if (rawClientMode != null) {
            // Wrap in ThreadedClientGameMode for consistent interface (no separate thread needed)
            clientGameMode = new ThreadedClientGameMode(rawClientMode);
            currentGameMode = clientGameMode;
        }

        lobbyUI.setStatus("Server started on port " + Network.TCP_PORT);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    private void startClientMode(String host) {
        // Dispose previous game modes
        disposePreviousGameModes();

        clientGameMode = new ThreadedClientGameMode(host, random);
        clientGameMode.initialize();
        currentGameMode = clientGameMode;

        lobbyUI.setStatus("Connected to " + host);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    @Override
    public void create() {
        try {
            Log.info("Main", "Starting initialization...");
            Log.info("Main", "Working directory: " + System.getProperty("user.dir"));

            this.random = new Random();
            this.modelBatch = new ModelBatch();

            // Initialize UI
            this.lobbyUI = new LobbyUI(this);
            this.debugUI = new DebugUI();

            // Set debug listener
            debugUI.setDebugListener(this);

            // Set input processor to lobby
            lobbyUI.setInputProcessor();

            Log.info("Main", "Initialized successfully");
        } catch (Exception e) {
            Log.error("Main", "Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to ensure the error is visible
        }
    }

    @Override
    public void render() {
        float deltaTime = Gdx.graphics.getDeltaTime();

        // Clear screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Threaded game modes handle their own updates in separate threads
        // No need to update them here - this prevents blocking the render thread

        // Render the client game mode (this handles rendering for both hosted and direct client)
        if (clientGameMode != null && clientGameMode.isActive()) {
            GameWorld clientGameWorld = clientGameMode.getGameWorld();
            clientGameMode.render(modelBatch, clientGameWorld.getEnvironment());

            // Update debug info - get local player from client game world
            if (clientGameWorld.getGameObjectManager() != null && clientGameWorld.getGameObjectManager().localPlayerController != null) {
                Vector3 pos = clientGameWorld.getGameObjectManager().localPlayerController.getPosition();
                debugUI.setPlayerPosition(pos.x, pos.y, pos.z);
            }

            // Update light count info
            if (clientGameWorld.getMapRenderer() != null) {
                debugUI.setLightCounts(
                    clientGameWorld.getMapRenderer().getLastTotalLights(),
                    clientGameWorld.getMapRenderer().getLastShadowLights()
                );
            }

            // Update physics debug info
            debugUI.setPhysicsDebugEnabled(clientGameWorld.isPhysicsDebugEnabled());
            debugUI.setPhysicsStrategy(clientGameWorld.getPhysicsStrategyInfo(), clientGameWorld.getPhysicsTriangleCount());

            // Update rendering strategy info
            debugUI.setRenderingStrategy(clientGameWorld.getRenderingStrategyInfo(),
                clientGameWorld.getRenderingFacesBuilt(), clientGameWorld.getRenderingTilesProcessed());
        }

        // Update and render UI
        if (showLobby) {
            lobbyUI.update(deltaTime);
            lobbyUI.render();
        }

        debugUI.update(deltaTime);
        debugUI.render();

        // Check for OpenGL errors
        int error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            System.out.println("OpenGL Error: " + error);
        }
    }

    @Override
    public void resize(int width, int height) {
        lobbyUI.resize(width, height);
        debugUI.resize(width, height);

        if (hostedGameMode != null) {
            hostedGameMode.resize(width, height);
        }

        if (clientGameMode != null) {
            clientGameMode.resize(width, height);
        }
    }

    private void disposePreviousGameModes() {
        if (hostedGameMode != null) {
            Log.info("Main", "Disposing previous hosted game mode");
            hostedGameMode.dispose();
            hostedGameMode = null;
        }

        if (clientGameMode != null) {
            Log.info("Main", "Disposing previous client game mode");
            clientGameMode.dispose();
            clientGameMode = null;
        }

        currentGameMode = null;
    }

    @Override
    public void dispose() {
        Log.info("Main", "Disposing resources...");

        // Dispose game modes (they handle their own game worlds)
        disposePreviousGameModes();

        // Dispose UI components
        try {
            if (lobbyUI != null) {
                lobbyUI.dispose();
                Log.info("Main", "Lobby UI disposed");
            }
        } catch (Exception e) {
            Log.error("Main", "Error disposing lobby UI: " + e.getMessage());
        }

        try {
            if (debugUI != null) {
                debugUI.dispose();
                Log.info("Main", "Debug UI disposed");
            }
        } catch (Exception e) {
            Log.error("Main", "Error disposing debug UI: " + e.getMessage());
        }

        // Dispose model batch last
        try {
            if (modelBatch != null) {
                modelBatch.dispose();
                Log.info("Main", "Model batch disposed");
            }
        } catch (Exception e) {
            Log.error("Main", "Error disposing model batch: " + e.getMessage());
        }

        Log.info("Main", "All resources disposed");
    }

    // DebugUI.DebugListener implementation
    @Override
    public void onTogglePhysicsDebug() {
        // Debug controls operate on the client's world (where rendering happens)
        if (clientGameMode != null && clientGameMode.getGameWorld() != null) {
            GameWorld clientGameWorld = clientGameMode.getGameWorld();
            clientGameWorld.togglePhysicsDebug();
            Log.info("Main", "Physics debug toggled: " + clientGameWorld.isPhysicsDebugEnabled());
        }
    }

    @Override
    public void onTogglePhysicsStrategy() {
        // Debug controls operate on the client's world (where rendering happens)
        if (clientGameMode != null && clientGameMode.getGameWorld() != null) {
            GameWorld clientGameWorld = clientGameMode.getGameWorld();
            clientGameWorld.togglePhysicsStrategy();
            Log.info("Main", "Physics strategy switched to: " + clientGameWorld.getPhysicsStrategyInfo());
        }
    }

    @Override
    public void onToggleRenderingStrategy() {
        // Debug controls operate on the client's world (where rendering happens)
        if (clientGameMode != null && clientGameMode.getGameWorld() != null) {
            GameWorld clientGameWorld = clientGameMode.getGameWorld();
            clientGameWorld.toggleRenderingStrategy();
            Log.info("Main", "Rendering strategy switched to: " + clientGameWorld.getRenderingStrategyInfo());
        }
    }
}
