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

    public static Random random = new Random();
    private ModelBatch modelBatch;

    // UI Components
    private LobbyUI lobbyUI;
    private DebugUI debugUI;
    private boolean showLobby = true;

    // Game Components
    private ThreadedHostedGameMode hostedGameMode;
    private ClientGameMode clientGameMode;

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
        // The server runs in its own thread, but client runs on main thread
        clientGameMode = hostedGameMode.getClientGameMode();

        lobbyUI.setStatus("Server started on port " + Network.TCP_PORT);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    private void startClientMode(String host) {
        // Dispose previous game modes
        disposePreviousGameModes();

        clientGameMode = new ClientGameMode(host, random);
        clientGameMode.initialize();

        lobbyUI.setStatus("Connected to " + host);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    @Override
    public void create() {
        try {
            Log.info("Main", "Starting initialization...");
            Log.info("Main", "Working directory: " + System.getProperty("user.dir"));

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

        // Update client game mode (runs on main thread for input/physics/rendering sync)
        if (clientGameMode != null) {
            try {
                clientGameMode.update(deltaTime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Render the client game mode (this handles rendering for both hosted and direct client)
        if (clientGameMode != null && clientGameMode.isActive()) {
            GameWorld clientGameWorld = clientGameMode.getGameWorld();
            clientGameMode.render(modelBatch, clientGameWorld.getEnvironment());

            // Update debug info - get local player from client game world
            if (clientGameWorld.getGameObjectManager() != null && clientGameWorld.getGameObjectManager().localPlayer != null) {
                Vector3 pos = clientGameWorld.getGameObjectManager().localPlayer.getPosition();
                if (pos != null) {
                    debugUI.setPlayerPosition(pos.x, pos.y, pos.z);
                }
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
            long hostedStart = System.currentTimeMillis();
            Log.info("Main", "Disposing previous hosted game mode");
            hostedGameMode.dispose();
            hostedGameMode = null;
            long hostedEnd = System.currentTimeMillis();
            Log.info("Main", "Hosted game mode disposed in " + (hostedEnd - hostedStart) + "ms");
        }

        if (clientGameMode != null) {
            long clientStart = System.currentTimeMillis();
            Log.info("Main", "Disposing previous client game mode");
            clientGameMode.dispose();
            clientGameMode = null;
            long clientEnd = System.currentTimeMillis();
            Log.info("Main", "Client game mode disposed in " + (clientEnd - clientStart) + "ms");
        }
    }

    @Override
    public void dispose() {
        long startTime = System.currentTimeMillis();
        Log.info("Main", "Disposing resources...");

        // Dispose game modes (they handle their own game worlds)
        long gameModeStart = System.currentTimeMillis();
        disposePreviousGameModes();
        long gameModeEnd = System.currentTimeMillis();
        Log.info("Main", "Game modes disposed in " + (gameModeEnd - gameModeStart) + "ms");

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

        long totalTime = System.currentTimeMillis() - startTime;
        Log.info("Main", "All resources disposed in " + totalTime + "ms");
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
