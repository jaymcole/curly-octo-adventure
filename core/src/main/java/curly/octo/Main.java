package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import curly.octo.camera.CameraController;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.VoxelMap;
import curly.octo.map.VoxelMapRenderer;
import curly.octo.network.CubeRotationListener;
import curly.octo.network.GameClient;
import curly.octo.network.GameServer;
import curly.octo.network.Network;

import java.io.IOException;

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

    private GameServer gameServer;
    private GameClient gameClient;

    // Voxel map components
    private VoxelMap voxelMap;
    private VoxelMapRenderer voxelMapRenderer;
    private boolean showUI = true;

    // Interface implementation for receiving rotation updates
    private final CubeRotationListener rotationListener = rotation -> {
        if (gameServer != null) {
            // If we're the server, broadcast to all clients
            gameServer.broadcastCubeRotation(rotation);
        }
    };
    private final boolean isServer;
    private final String host;

    // 3D rendering variables
    private boolean show3DView = false;
    private Environment environment;
    private PerspectiveCamera cam;
    private CameraController cameraController;
    private ModelBatch modelBatch;
    private Model model;
    private ModelInstance instance;
    private Quaternion currentRotation = new Quaternion();
    private float rotationUpdateTimer = 0;
    private static final float ROTATION_UPDATE_INTERVAL = 0.1f; // Update 10 times per second
    private boolean isDragging = false;
    private float lastX, lastY;

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
        // Setup 3D environment
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // Create and set up camera
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 10, 10);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 300f;
        cam.update();

        // Initialize camera controller
        cameraController = new CameraController(cam);
        cameraController.setVelocity(20f); // Adjust movement speed as needed

        // Set up input processor
        Gdx.input.setInputProcessor(cameraController);

        // Initialize voxel map and renderer
        voxelMap = new VoxelMap(64, 16, 64, System.currentTimeMillis());
        voxelMap.generateDungeon();
        voxelMapRenderer = new VoxelMapRenderer();
        voxelMapRenderer.updateMap(voxelMap);

        // Create a simple cube model
        ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createBox(2f, 2f, 2f,
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            Usage.Position | Usage.Normal);
        instance = new ModelInstance(model);



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

        // Initialize voxel map
        voxelMap = new VoxelMap(64, 16, 64, System.currentTimeMillis());
        voxelMap.generateDungeon();

        // Initialize renderer
        voxelMapRenderer = new VoxelMapRenderer();
        voxelMapRenderer.updateMap(voxelMap);

        // Auto-start server if specified
        // if (isServer) {
        //     startServer();
        // } else if (host != null) {
        //     connectToServer(host);
        // }
    }

    private void startServer() {
        try {
            gameServer = new GameServer();
            gameServer.setRotationListener(rotation -> {
                // Update local cube rotation
                if (instance != null) {
                    instance.transform.set(Vector3.Zero, rotation);
                }
            });
            gameServer.start();
            updateUIForServer();
            statusLabel.setText("Server started on port " + Network.TCP_PORT);
            // Switch to 3D view after a short delay
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000); // Wait for the message to be visible
                    show3DView = true;
                    Gdx.input.setInputProcessor(cameraController); // Use Main class as input processor
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

            // Set up the rotation listener before connecting
            gameClient.setRotationListener(rotation -> {
                Gdx.app.postRunnable(() -> {
                    if (instance != null) {
                        // Only update if we're not currently dragging (to avoid jitter)
                        if (!isDragging) {
                            instance.transform.set(Vector3.Zero, rotation);
                            Log.info("Client", "Updated cube rotation from server");
                        }
                    }
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

                        Gdx.input.setInputProcessor(cameraController); // Use Main class as input processor
                        Log.info("Client", "Switched to 3D view and set input processor to Main");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });

        } catch (IOException e) {
            Gdx.app.postRunnable(() -> {
                statusLabel.setText("Failed to connect: " + e.getMessage());
                e.printStackTrace();
            });
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

        // Update camera
        cameraController.update(Gdx.graphics.getDeltaTime());

        // Render the voxel map
        voxelMapRenderer.render(cam);

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
        if (cameraController != null) {
            cameraController.resize(width, height);
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
