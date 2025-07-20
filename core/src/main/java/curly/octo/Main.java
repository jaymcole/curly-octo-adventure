package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
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

        // If we were launched with command line args, skip the UI
//        if (host != null) {
//            if (isServer) {
//                startServer();
//            } else {
//                connectToServer(host);
//            }
//            return;
//        }

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

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
            gameServer.start();
            updateUIForServer();
            statusLabel.setText("Server started on port " + Network.TCP_PORT);
            // Switch to 3D view after a short delay
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000); // Wait for the message to be visible
                    show3DView = true;
                    Gdx.input.setInputProcessor(camController);
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
            gameClient.connect(5000);
            updateUIForServer();
            statusLabel.setText("Connected to " + host);
            // Switch to 3D view after a short delay
            Gdx.app.postRunnable(() -> {
                try {
                    Thread.sleep(1000); // Wait for the message to be visible
                    show3DView = true;
                    Gdx.input.setInputProcessor(camController);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (IOException e) {
            statusLabel.setText("Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateUIForServer() {
        startServerButton.setDisabled(true);
        connectButton.setDisabled(true);
        ipAddressField.setDisabled(true);
    }

    private void disableUI() {
        startServerButton.setDisabled(true);
        connectButton.setDisabled(true);
        ipAddressField.setDisabled(true);
    }

    @Override
    public void render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (show3DView) {
            // Update camera controller
            camController.update();

            // Rotate the cube
            instance.transform.rotate(Vector3.Y, Gdx.graphics.getDeltaTime() * 20);
            instance.transform.rotate(Vector3.X, Gdx.graphics.getDeltaTime() * 30);

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
    public void dispose() {
        if (gameServer != null) {
            gameServer.stop();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (modelBatch != null) {
            modelBatch.dispose();
        }
        if (model != null) {
            model.dispose();
        }
    }
}
