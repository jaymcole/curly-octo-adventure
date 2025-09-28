package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.InputMultiplexer;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.*;
import curly.octo.game.stateV2.BaseScreen;
import curly.octo.game.stateV2.MainMenuState.MainMenuScreen;
import curly.octo.game.stateV2.StateManager;
import curly.octo.ui.DebugUI;
import curly.octo.game.state.GameStateManager;
import curly.octo.game.state.GameState;

import java.io.IOException;
import java.util.Random;

/**
 * Main game class with network setup UI.
 * Delegates game logic to specific game modes (Server/Client).
 */
public class Main extends ApplicationAdapter implements MainMenuScreen.MainMenuListener, DebugUI.DebugListener, ClientGameMode.MapRegenerationListener {

    public static Random random = new Random();
    private ModelBatch modelBatch;

    // UI Components
    private DebugUI debugUI;
    private BaseScreen activeScreen;
    private boolean gameIsPlaying = false;

    // Game Components
    private ThreadedHostedGameMode hostedGameMode;
    private ClientGameMode clientGameMode;

    public Main() {
    }

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

        // Set the regeneration listener on the client mode
        if (clientGameMode != null) {
            clientGameMode.setMapRegenerationListener(this);
            // Connect the state UI to the state manager
            connectStateUIToClient(clientGameMode);
        }
    }

    private void startClientMode(String host) {
        // Dispose previous game modes
        disposePreviousGameModes();

        clientGameMode = new ClientGameMode(host, random);
        clientGameMode.setMapRegenerationListener(this); // Set ourselves as the listener

        // Connect the state UI to the state manager
        connectStateUIToClient(clientGameMode);

        clientGameMode.initialize();
    }

    /**
     * Connect the StateUI to the ClientGameMode's state manager for automatic UI updates
     */
    private void connectStateUIToClient(ClientGameMode clientGameMode) {
        if (clientGameMode == null) {
            return;
        }

        GameStateManager stateManager = clientGameMode.getStateManager();
        if (stateManager != null) {
            // Add a state change listener that updates the UI
            stateManager.addStateChangeListener(new GameStateManager.StateChangeListener() {
                @Override
                public void onStateChanged(curly.octo.game.state.GameState oldState,
                                          curly.octo.game.state.GameState newState,
                                          curly.octo.game.state.StateContext context) {

//                    Log.info("Main", String.format("STATE TRANSITION: %s -> %s",
//                        oldState.getDisplayName(), newState.getDisplayName()));
//
//                    // Update UI on the main thread
//                    Gdx.app.postRunnable(() -> {
//                        if (newState.isMapRegenerationState()) {
//                            // Show the regeneration screen
//                            stateUI.showStateScreen(newState, context);
//                        } else {
//                            // Hide state screens for normal states
//                            stateUI.hideAllScreens();
//                        }
//                    });
                }

                @Override
                public void onStateProgressUpdated(curly.octo.game.state.StateContext context) {
//                    Log.info("Main", "STATE PROGRESS UPDATED - Progress: " + context.getProgress() + ", State: " + context.getCurrentState());
//                    // Update progress on the main thread
//                    Gdx.app.postRunnable(() -> {
//                        Log.info("Main", "Executing UI update on main thread");
//                        stateUI.updateProgress(context);
//                    });
                }
            });
        }
    }

    @Override
    public void create() {
        try {
            StateManager.initialize(this);
            this.modelBatch = new ModelBatch();
            this.debugUI = new DebugUI();
            debugUI.setDebugListener(this);
            updateInputMultiplexer();
            Log.info("Main", "Initialized successfully");
        } catch (Exception e) {
            Log.error("Main", "Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            throw e;
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

        if (gameIsPlaying) {
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
            }
        }

        StateManager.update(deltaTime);

        if (activeScreen != null) {
            activeScreen.render(deltaTime);
        }

        // Show debug UI only if enabled in constants and not during map regeneration
        if (Constants.DEBUG_SHOW_FPS) {
            boolean showDebugUI = true;

            // Hide debug UI during map regeneration states
            if (clientGameMode != null && clientGameMode.getStateManager() != null) {
                GameState currentState = clientGameMode.getStateManager().getCurrentState();
                if (currentState.isMapRegenerationState()) {
                    showDebugUI = false;
                }
            }

            if (showDebugUI && gameIsPlaying) {
                debugUI.update(deltaTime);
                debugUI.render();
            }
        }
    }

    public void setScreen(BaseScreen screen, boolean gameIsPlaying) {
        activeScreen = screen;

        updateInputMultiplexer();

        this.gameIsPlaying = gameIsPlaying;
    }

    private void updateInputMultiplexer() {
        InputMultiplexer multiplexer = new InputMultiplexer();

        if (activeScreen != null) {
            multiplexer.addProcessor(activeScreen.getStage());
        }

        if (debugUI != null) {
            multiplexer.addProcessor(debugUI.getStage());
        }
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void resize(int width, int height) {
        debugUI.resize(width, height);
        if (hostedGameMode != null) {
            hostedGameMode.resize(width, height);
        }

        if (clientGameMode != null) {
            clientGameMode.resize(width, height);

            // Update input processor to include both game and UI input
            if (clientGameMode.isActive()) {
                updateInputMultiplexer();
            }
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

        StateManager.dispose();

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
    public void onRegenerateMap() {
        Log.info("Main", "Map regeneration triggered from debug UI");

        // Only allow regeneration if we're hosting a server
        if (hostedGameMode != null) {
            try {
                Log.info("Main", "Triggering map regeneration via hosted game mode");
                hostedGameMode.debugRegenerateMap();
                Log.info("Main", "Map regeneration command sent successfully");
            } catch (Exception e) {
                Log.error("Main", "Failed to regenerate map: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.warn("Main", "Map regeneration not available - not hosting a server");
            Log.warn("Main", "Only the server host can regenerate the map");
        }
    }

    // ClientGameMode.MapRegenerationListener implementation
    @Override
    public void onMapSeedChanged(long newSeed) {
        Log.info("Main", "Map seed changed to: " + newSeed + ", updating debug UI");
        if (debugUI != null) {
            debugUI.setMapSeed(newSeed);
            Log.info("Main", "Debug UI updated with new map seed: " + newSeed);
        } else {
            Log.warn("Main", "Cannot update debug UI - debugUI is null");
        }
    }
}
