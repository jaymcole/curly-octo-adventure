package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.InputMultiplexer;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.*;
import curly.octo.ui.LobbyUI;
import curly.octo.ui.DebugUI;
import curly.octo.ui.StateUI;
import curly.octo.network.Network;
import curly.octo.game.state.GameStateManager;
import curly.octo.game.state.GameState;

import java.io.IOException;
import java.util.Random;

/**
 * Main game class with network setup UI.
 * Delegates game logic to specific game modes (Server/Client).
 */
public class Main extends ApplicationAdapter implements LobbyUI.LobbyListener, DebugUI.DebugListener, ClientGameMode.MapRegenerationListener {

    public static Random random = new Random();
    private ModelBatch modelBatch;

    // UI Components
    private LobbyUI lobbyUI;
    private DebugUI debugUI;
    private StateUI stateUI;
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
        
        // Set the regeneration listener on the client mode
        if (clientGameMode != null) {
            clientGameMode.setMapRegenerationListener(this);
            // Connect the state UI to the state manager
            connectStateUIToClient(clientGameMode);
        }

        lobbyUI.setStatus("Server started on port " + Network.TCP_PORT);
        lobbyUI.disableInputs();
        showLobby = false;
    }

    private void startClientMode(String host) {
        // Dispose previous game modes
        disposePreviousGameModes();

        clientGameMode = new ClientGameMode(host, random);
        clientGameMode.setMapRegenerationListener(this); // Set ourselves as the listener
        
        // Connect the state UI to the state manager
        connectStateUIToClient(clientGameMode);
        
        clientGameMode.initialize();

        lobbyUI.setStatus("Connected to " + host);
        lobbyUI.disableInputs();
        showLobby = false;
    }
    
    /**
     * Connect the StateUI to the ClientGameMode's state manager for automatic UI updates
     */
    private void connectStateUIToClient(ClientGameMode clientGameMode) {
        if (clientGameMode == null || stateUI == null) {
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
                    
                    Log.info("Main", String.format("STATE TRANSITION: %s -> %s",
                        oldState.getDisplayName(), newState.getDisplayName()));
                    
                    // Update UI on the main thread
                    Gdx.app.postRunnable(() -> {
                        if (newState.isMapRegenerationState()) {
                            // Show the regeneration screen
                            stateUI.showStateScreen(newState, context);
                        } else {
                            // Hide state screens for normal states
                            stateUI.hideAllScreens();
                        }
                    });
                }
                
                @Override
                public void onStateProgressUpdated(curly.octo.game.state.StateContext context) {
                    Log.info("Main", "STATE PROGRESS UPDATED - Progress: " + context.getProgress() + ", State: " + context.getCurrentState());
                    // Update progress on the main thread
                    Gdx.app.postRunnable(() -> {
                        Log.info("Main", "Executing UI update on main thread");
                        stateUI.updateProgress(context);
                    });
                }
            });
            
            Log.info("Main", "Connected StateUI to ClientGameMode state manager");
        }
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
            this.stateUI = new StateUI();

            // Set debug listener
            debugUI.setDebugListener(this);

            // Set up input multiplexer to handle both game and UI input
            // Initially just UI since no game is running yet
            InputMultiplexer multiplexer = new InputMultiplexer();
            multiplexer.addProcessor(stateUI.getStage());   // Add state UI first (modal screens have priority)
            multiplexer.addProcessor(lobbyUI.getStage());   // Add lobby UI stage (needs input for buttons)
            multiplexer.addProcessor(debugUI.getStage());
            Gdx.input.setInputProcessor(multiplexer);
            
            // Debug: Log input processor setup
            Log.info("Main", "Set up input multiplexer with debug UI stage and lobby UI stage");
            Log.info("Main", "Debug UI stage: " + (debugUI.getStage() != null ? "created" : "null"));
            Log.info("Main", "Lobby UI stage: " + (lobbyUI.getStage() != null ? "created" : "null"));
            Log.info("Main", "Input processor set: " + (Gdx.input.getInputProcessor() != null ? "yes" : "no"));
            
            // Don't let lobby UI override our input processor setup
            // lobbyUI.setInputProcessor(); // REMOVED - this was overriding our multiplexer

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
        }

        // Update and render UI
        if (showLobby) {
            lobbyUI.update(deltaTime);
            lobbyUI.render();
        }
        
        // Always update and render state UI (handles its own visibility)
        stateUI.update(deltaTime);
        stateUI.render();

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
            
            if (showDebugUI) {
                debugUI.update(deltaTime);
                debugUI.render();
            }
        }

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
        stateUI.resize(width, height);

        if (hostedGameMode != null) {
            hostedGameMode.resize(width, height);
        }

        if (clientGameMode != null) {
            clientGameMode.resize(width, height);
            
            // Update input processor to include both game and UI input
            if (clientGameMode.isActive()) {
                InputMultiplexer multiplexer = new InputMultiplexer();
                // Add client game mode input processor FIRST so it gets keyboard events (WASD, F, etc.)
                if (clientGameMode.getInputProcessor() != null) {
                    multiplexer.addProcessor(clientGameMode.getInputProcessor());
                }
                // Add debug UI second so it gets mouse events for UI but doesn't consume keyboard
                multiplexer.addProcessor(debugUI.getStage());
                Gdx.input.setInputProcessor(multiplexer);
                Log.info("Main", "Updated input multiplexer for active client game mode (game input first)");
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
        
        try {
            if (stateUI != null) {
                stateUI.dispose();
                Log.info("Main", "State UI disposed");
            }
        } catch (Exception e) {
            Log.error("Main", "Error disposing state UI: " + e.getMessage());
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
