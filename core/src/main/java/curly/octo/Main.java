package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import curly.octo.player.PlayerController;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.VoxelMap;
import curly.octo.map.VoxelMapRenderer;
import curly.octo.network.GameClient;
import curly.octo.network.GameServer;
import curly.octo.network.Network;
import curly.octo.player.PlayerUtilities;

import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Main game class with network setup UI.
 * Allows starting a server or connecting as a client.
 */
public class Main extends ApplicationAdapter {
    private Stage stage;
    private Table table;
    private TextButton startServerButton;
    private TextButton connectButton;
    private TextField ipAddressField;
    private Label statusLabel;
    private Random random;

    private GameServer gameServer;
    private GameClient gameClient;

    // Voxel map components
    private VoxelMap voxelMap;
    private VoxelMapRenderer voxelMapRenderer;
    private boolean showUI = true;

    private final boolean isServer;
    private final String host;

    private ArrayList<PlayerController> players;
    private PlayerController localPlayerController;
    private long localPlayerId;

    // 3D rendering variables
    private ModelBatch modelBatch;
    private boolean show3DView = false;
    private Environment environment;

    /**
     * Creates a new instance of the game.
     * @param isServer Whether to start as a server (true) or client (false)
     * @param host The host to connect to (only used in client mode)
     */
    public Main(boolean isServer, String host) {
        this.isServer = isServer;
        this.host = host != null ? host : "localhost";
    }

    /**
     * Default constructor for backward compatibility.
     * Starts as a client connecting to localhost.
     */
    public Main() {
        this(false, "localhost");
    }

    @Override
    public void create() {
        this.random = new Random();
        modelBatch = new ModelBatch();

        // Setup 3D environment
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // Initialize voxel renderer
        voxelMapRenderer = new VoxelMapRenderer();

        // Create stage for UI
        stage = new Stage(new ScreenViewport());

        // Set initial input processor to stage
        Gdx.input.setInputProcessor(stage);

        // If we were launched with command line args, skip the UI
        table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // Create UI elements
        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        statusLabel = new Label("Not connected", skin);
        ipAddressField = new TextField("localhost", skin);

        startServerButton = new TextButton("Start Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startServer();
            }
        });

        connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                connectToServer(ipAddressField.getText());
            }
        });

        // Layout
        table.add(statusLabel).colspan(2).pad(10);
        table.row();
        table.add(ipAddressField).width(200).pad(5);
        table.add(connectButton).pad(5);
        table.row();
        table.add(startServerButton).colspan(2).pad(5);
    }

    private void startServer() {
        try {
            localPlayerController = PlayerUtilities.createPlayerController(random);
            localPlayerId = localPlayerController.getPlayerId();
            localPlayerController.setVelocity(20f); // Adjust movement speed as needed
            players = new ArrayList<>();
            players.add(localPlayerController);

            // Generate map before starting server
            if (voxelMap == null) {
                voxelMap = new VoxelMap(64, 16, 64, System.currentTimeMillis());
                voxelMap.generateDungeon();
                voxelMapRenderer.updateMap(voxelMap);
            }

            gameServer = new GameServer(random, voxelMap, players);

            gameServer.start();
            updateUIForServer();
            statusLabel.setText("Server started on port " + Network.TCP_PORT);
            // Switch to 3D view after a short delay
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000); // Wait for the message to be visible
                    show3DView = true;
                    Gdx.input.setInputProcessor(localPlayerController); // Use Main class as input processor
                    Log.info("Server", "Switched to 3D view and set input processor to Main");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            showUI = false;
        } catch (IOException e) {
            statusLabel.setText("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToServer(String host) {
        try {
            gameClient = new GameClient(host);

            // Set up the map received listener
            gameClient.setMapReceivedListener(receivedMap -> {
                Gdx.app.postRunnable(() -> {
                    Log.info("Client", "Received map with size: " +
                        receivedMap.getWidth() + "x" +
                        receivedMap.getHeight() + "x" +
                        receivedMap.getDepth());

                    // Update the local map and renderer
                    voxelMap = receivedMap;
                    if (voxelMapRenderer != null) {
                        voxelMapRenderer.updateMap(voxelMap);
                        Log.info("Client", "Updated local map and renderer");
                    } else {
                        Log.error("Client", "VoxelMapRenderer is null");
                    }
                });
            });

            gameClient.setPlayerAssignmentListener(receivedPlayerId -> {
                Gdx.app.postRunnable(() -> {
                    setPlayer(receivedPlayerId);
                });
            });

            gameClient.setPlayerRosterListener(newPlayer -> {
                Gdx.app.postRunnable(() -> {
                    players.add(newPlayer);
                });
            });


            // Connect to the server
            gameClient.connect(5000);

            // Set up periodic updates
            Gdx.app.postRunnable(() -> {
                updateUIForServer();
                statusLabel.setText("Connected to " + host);

                // Switch to 3D view after a short delay
                Gdx.app.postRunnable(() -> {
                    try {
                        Thread.sleep(1000); // Wait for the message to be visible
                        show3DView = true;

                        Gdx.input.setInputProcessor(localPlayerController);
                        Log.info("Client", "Switched to 3D view and set input processor to Main");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });
            showUI = false;

        } catch (IOException e) {
            Gdx.app.postRunnable(() -> {
                statusLabel.setText("Failed to connect: " + e.getMessage());
                e.printStackTrace();
            });
        }
    }

    private void setPlayer(long localPlayerId) {
        this.localPlayerId = localPlayerId;
        if (players != null && !players.isEmpty()) {
            for (PlayerController player : players) {
                if(player.getPlayerId() == localPlayerId) {
                    localPlayerController = player;
                    break;
                }
            }
        }
    }

    private void updateUIForServer() {
        startServerButton.setDisabled(true);
        connectButton.setDisabled(true);
        ipAddressField.setDisabled(true);
    }

    @Override
    public void render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (show3DView) {
            // Update camera
            if (localPlayerController != null) {
                localPlayerController.update(Gdx.graphics.getDeltaTime());
                voxelMapRenderer.render(localPlayerController.getCamera());
            }
            // Render the voxel map
            if (players != null) {
                for(PlayerController player : players) {
                    player.render(modelBatch, environment);
                }
            }
        }

        // Update and draw UI if visible
        if (showUI) {
            stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
            stage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        // Update viewport for 2D UI
        stage.getViewport().update(width, height, true);

        // Update camera for 3D view
        if (localPlayerController != null) {
            localPlayerController.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (gameServer != null) {
            gameServer.stop();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        if (voxelMapRenderer != null) {
            voxelMapRenderer.disposeAll();
        }
        stage.dispose();
    }
}
