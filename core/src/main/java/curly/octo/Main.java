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
    private GameWorld gameWorld;
    private GameMode currentGameMode;

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
        gameWorld = new GameWorld(random);
        currentGameMode = new ServerGameMode(gameWorld);
        currentGameMode.initialize();

        lobbyUI.setStatus("Server started on port " + Network.TCP_PORT);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    private void startClientMode(String host) {
        gameWorld = new GameWorld(random);
        currentGameMode = new ClientGameMode(gameWorld, host);
        currentGameMode.initialize();

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

        // Update and render game mode if active
        if (currentGameMode != null && currentGameMode.isActive()) {
            try {
                currentGameMode.update(deltaTime);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            currentGameMode.render(modelBatch, gameWorld.getEnvironment());

            // Update debug info
            if (GameObjectManager.playerController != null) {
                Vector3 pos = GameObjectManager.playerController.getPosition();
                debugUI.setPlayerPosition(pos.x, pos.y, pos.z);
            }

            // Update light count info
            if (gameWorld.getMapRenderer() != null) {
                debugUI.setLightCounts(
                    gameWorld.getMapRenderer().getLastTotalLights(),
                    gameWorld.getMapRenderer().getLastShadowLights()
                );
            }

            // Update physics debug info
            debugUI.setPhysicsDebugEnabled(gameWorld.isPhysicsDebugEnabled());
            debugUI.setPhysicsStrategy(gameWorld.getPhysicsStrategyInfo(), gameWorld.getPhysicsTriangleCount());

            // Update rendering strategy info
            debugUI.setRenderingStrategy(gameWorld.getRenderingStrategyInfo(),
                gameWorld.getRenderingFacesBuilt(), gameWorld.getRenderingTilesProcessed());
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

        if (currentGameMode != null) {
            currentGameMode.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        Log.info("Main", "Disposing resources...");

        // Dispose game mode first (it handles its own resources)
        if (currentGameMode != null) {
            try {
                currentGameMode.dispose();
                Log.info("Main", "Game mode disposed");
            } catch (Exception e) {
                Log.error("Main", "Error disposing game mode: " + e.getMessage());
            }
        }

        // Dispose game world (it handles map and renderer)
        if (gameWorld != null) {
            try {
                gameWorld.dispose();
                Log.info("Main", "Game world disposed");
            } catch (Exception e) {
                Log.error("Main", "Error disposing game world: " + e.getMessage());
            }
        }

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
        if (gameWorld != null) {
            gameWorld.togglePhysicsDebug();
            Log.info("Main", "Physics debug toggled: " + gameWorld.isPhysicsDebugEnabled());
        }
    }

    @Override
    public void onTogglePhysicsStrategy() {
        if (gameWorld != null) {
            gameWorld.togglePhysicsStrategy();
            Log.info("Main", "Physics strategy switched to: " + gameWorld.getPhysicsStrategyInfo());
        }
    }

    @Override
    public void onToggleRenderingStrategy() {
        if (gameWorld != null) {
            gameWorld.toggleRenderingStrategy();
            Log.info("Main", "Rendering strategy switched to: " + gameWorld.getRenderingStrategyInfo());
        }
    }
}
