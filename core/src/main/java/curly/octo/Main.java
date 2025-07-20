package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.CubeRotationListener;
import curly.octo.network.GameClient;
import curly.octo.network.GameServer;
import curly.octo.network.Network;

import java.io.IOException;

/**
 * Main game class with network setup UI.
 * Allows starting a server or connecting as a client.
 */
public class Main extends ApplicationAdapter implements InputProcessor {
    private Stage stage;
    private Table table;
    private TextButton startServerButton;
    private TextButton connectButton;
    private TextField ipAddressField;
    private Label statusLabel;

    private GameServer gameServer;
    private GameClient gameClient;

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
    private CameraInputController camController;
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

        // Create camera
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(3f, 3f, 3f);
        cam.lookAt(0, 0, 0);
        cam.near = 1f;
        cam.far = 300f;
        cam.update();

        // Create a simple cube model
        ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createBox(2f, 2f, 2f,
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            Usage.Position | Usage.Normal);
        instance = new ModelInstance(model);

        // Camera controller for mouse interaction
        camController = new CameraInputController(cam);
        
        // Create stage for UI
        stage = new Stage(new ScreenViewport());
        
        // Set initial input processor to stage
        Gdx.input.setInputProcessor(stage);
        
        // If we were launched with command line args, skip the UI
        // if (host != null) {
        //     if (isServer) {
        //         startServer();
        //     } else {
        //         connectToServer(host);
        //     }
        //     return;
        // }

        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // Title
        Label titleLabel = new Label("Curly Octo Adventure", skin);
        table.add(titleLabel).colspan(2).padBottom(20);
        table.row();

        // Server button
        startServerButton = new TextButton("Create Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startServer();
            }
        });
        table.add(startServerButton).width(200).pad(5);

        // Connect button
        connectButton = new TextButton("Join Server", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String host = ipAddressField.getText().trim();
                if (!host.isEmpty()) {
                    connectToServer(host);
                }
            }
        });
        table.add(connectButton).width(200).pad(5);
        table.row();

        // IP Address field
        ipAddressField = new TextField("localhost", skin);
        ipAddressField.setMessageText("Enter server IP");
        table.add(ipAddressField).colspan(2).width(410).pad(5);
        table.row();

        // Status label
        statusLabel = new Label("", skin);
        statusLabel.setAlignment(Align.center);
        table.add(statusLabel).colspan(2).width(400).pad(10);
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
                    Gdx.input.setInputProcessor(Main.this); // Use Main class as input processor
                    Log.info("Server", "Switched to 3D view and set input processor to Main");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
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
                        Gdx.input.setInputProcessor(Main.this); // Use Main class as input processor
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
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (show3DView) {
            // Update camera controller only when not dragging
            if (!isDragging) {
                camController.update();
            }

            // Get the current rotation
            instance.transform.getRotation(currentRotation);

            // If we're connected (either as server or client), send rotation updates
            if ((gameServer != null || gameClient != null) && isDragging) {
                // Send rotation updates more frequently while dragging
                rotationUpdateTimer += Gdx.graphics.getDeltaTime();
                if (rotationUpdateTimer >= ROTATION_UPDATE_INTERVAL) {
                    rotationUpdateTimer = 0;
                    if (gameServer != null) {
                        // If we're the server, broadcast to all clients
                        gameServer.broadcastCubeRotation(currentRotation);
                    } else if (gameClient != null) {
                        // If we're a client, send to server
                        gameClient.sendCubeRotation(currentRotation);
                    }
                }
            }

            // Apply any received rotation updates (for clients)
            if (gameClient != null && gameClient.getLastRotation() != null) {
                // Only update if we're not currently dragging (to avoid jitter)
                if (!isDragging) {
                    instance.transform.set(Vector3.Zero, gameClient.getLastRotation());
                }
            }

            // Render 3D scene
            modelBatch.begin(cam);
            modelBatch.render(instance, environment);
            modelBatch.end();
        } else {
            // Render UI
            stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
            stage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        // Update viewport for 2D UI
        stage.getViewport().update(width, height, true);

        // Update camera for 3D view
        if (cam != null) {
            cam.viewportWidth = width;
            cam.viewportHeight = height;
            cam.update();
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Log.info("Main", "touchDown");

        if (button == Input.Buttons.LEFT && show3DView) {
            isDragging = true;
            lastX = screenX;
            lastY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Log.info("Main", "touchUp");

        if (button == Input.Buttons.LEFT && isDragging) {
            isDragging = false;
            // Send final rotation update when dragging stops
            if (show3DView) {
                instance.transform.getRotation(currentRotation);
                if (gameServer != null) {
                    gameServer.broadcastCubeRotation(currentRotation);
                } else if (gameClient != null) {
                    gameClient.sendCubeRotation(currentRotation);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        isDragging = false;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        Log.info("Main", "Touch is dragging");

        if (isDragging && show3DView) {
            Log.info("Main", "Touch is dragging");

            // Calculate delta movement
            float deltaX = (screenX - lastX) * 0.5f;
            float deltaY = (screenY - lastY) * 0.5f;

            // Get current rotation
            Quaternion currentRot = new Quaternion();
            instance.transform.getRotation(currentRot);

            // Create rotation deltas
            Quaternion rotX = new Quaternion(Vector3.Y, -deltaX);
            Quaternion rotY = new Quaternion(Vector3.X, -deltaY);

            // Apply rotations to current rotation
            currentRot.mul(rotX).mul(rotY);

            // Update the instance transform
            instance.transform.set(Vector3.Zero, currentRot);

            // Store the current rotation
            currentRotation.set(currentRot);

            // Update last position
            lastX = screenX;
            lastY = screenY;

            // Update rotation timer and send updates at a fixed rate
            rotationUpdateTimer += Gdx.graphics.getDeltaTime();
            if (rotationUpdateTimer >= ROTATION_UPDATE_INTERVAL) {
                if (gameServer != null) {
                    Log.info("Main", "Sending rotation update from server");
                    gameServer.broadcastCubeRotation(currentRot);
                } else if (gameClient != null) {
                    Log.info("Main", "Sending rotation update from client");
                    gameClient.sendCubeRotation(currentRot);
                }
                rotationUpdateTimer = 0;
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public void dispose() {
        if (gameServer != null) gameServer.stop();
        if (gameClient != null) gameClient.disconnect();
        modelBatch.dispose();
        model.dispose();
        stage.dispose();
    }
}
