package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
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
        // Initialize the stage and UI components
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        // Create UI components
        startServerButton = new TextButton("Start Server", skin);
        connectButton = new TextButton("Connect", skin);
        ipAddressField = new TextField("localhost", skin);
        statusLabel = new Label("Select an option to begin", skin);

        // Set up button listeners
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startAsServer();
            }
        });

        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                connectAsClient(ipAddressField.getText());
            }
        });

        // Set up layout
        table = new Table();
        table.setFillParent(true);

//        table.add(new Label("Network Setup", skin, "title")).colspan(2).padBottom(20).row();
        table.add(startServerButton).colspan(2).width(200).padBottom(20).row();

        Table clientTable = new Table();
        clientTable.add(new Label("Connect to:", skin)).padRight(10);
        clientTable.add(ipAddressField).width(150);
        clientTable.add(connectButton).padLeft(10);

        table.add(clientTable).colspan(2).padBottom(20).row();
        table.add(statusLabel).colspan(2);

        stage.addActor(table);
    }

    private void startAsServer() {
        try {
            gameServer = new GameServer();
            gameServer.start();
            updateStatus("Server started on port " + Network.TCP_PORT);
            disableUI();
        } catch (IOException e) {
            updateStatus("Failed to start server: " + e.getMessage());
            Gdx.app.error("Server", "Error starting server", e);
        }
    }

    private void connectAsClient(String host) {
        try {
            gameClient = new GameClient(host);
            gameClient.connect(5000); // 5 second timeout
            updateStatus("Connected to " + host + ":" + Network.TCP_PORT);
            disableUI();
        } catch (IOException e) {
            updateStatus("Connection failed: " + e.getMessage());
            Gdx.app.error("Client", "Error connecting to server", e);
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
        Gdx.app.log("Status", message);
    }

    private void disableUI() {
        startServerButton.setDisabled(true);
        connectButton.setDisabled(true);
        ipAddressField.setDisabled(true);
    }

    @Override
    public void render() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (gameServer != null) {
            gameServer.stop();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        stage.dispose();
    }
}
