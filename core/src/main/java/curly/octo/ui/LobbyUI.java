package curly.octo.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the lobby UI for starting servers and connecting to clients.
 */
public class LobbyUI {

    private Stage stage;
    private TextButton startServerButton;
    private TextButton connectButton;
    private TextField ipAddressField;
    private Label statusLabel;

    private LobbyListener listener;

    public interface LobbyListener {
        void onStartServer();
        void onConnectToServer(String host);
    }

    public LobbyUI(LobbyListener listener) {
        this.listener = listener;
        createStage();
    }

    private void createStage() {
        stage = new Stage(new ScreenViewport());

        // Create skin
        Skin skin;
        try {
            skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
            Log.info("LobbyUI", "Successfully loaded UI skin");
        } catch (Exception e) {
            Log.error("LobbyUI", "Failed to load UI skin: " + e.getMessage());
            e.printStackTrace();
            // Create a basic skin as fallback
            skin = new Skin();
        }

        // Create main table
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.pad(20);

        // Title
        Label titleLabel = new Label("Multiplayer Game", skin, "subtitle");
        titleLabel.setAlignment(Align.center);
        mainTable.add(titleLabel).colspan(2).padBottom(30).row();

        // Server section
        Label serverLabel = new Label("Host a Game", skin, "subtitle");
        serverLabel.setAlignment(Align.center);
        mainTable.add(serverLabel).colspan(2).padBottom(10).row();

        startServerButton = new TextButton("Start Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    listener.onStartServer();
                }
            }
        });
        mainTable.add(startServerButton).colspan(2).padBottom(30).row();

        // Client section
        Label clientLabel = new Label("Join a Game", skin, "subtitle");
        clientLabel.setAlignment(Align.center);
        mainTable.add(clientLabel).colspan(2).padBottom(10).row();

        // IP Address input
        Label ipLabel = new Label("Server IP:", skin);
        mainTable.add(ipLabel).padRight(10);

        ipAddressField = new TextField("localhost", skin);
        ipAddressField.setMaxLength(15);
        mainTable.add(ipAddressField).padBottom(10).row();

        connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    String host = ipAddressField.getText().trim();
                    if (!host.isEmpty()) {
                        listener.onConnectToServer(host);
                    }
                }
            }
        });
        mainTable.add(connectButton).colspan(2).padBottom(30).row();

        // Status label
        statusLabel = new Label("Ready to play!", skin);
        statusLabel.setAlignment(Align.center);
        mainTable.add(statusLabel).colspan(2);

        stage.addActor(mainTable);

        Log.info("LobbyUI", "Created lobby UI");
    }

    public void update(float deltaTime) {
        stage.act(deltaTime);
    }

    public void render() {
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void dispose() {
        stage.dispose();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setInputProcessor() {
        Gdx.input.setInputProcessor(stage);
    }

    /**
     * Get the stage for input processing.
     * Used by the input multiplexer to handle UI input.
     */
    public Stage getStage() {
        return stage;
    }

    public void disableInputs() {
        if (startServerButton != null) startServerButton.setDisabled(true);
        if (connectButton != null) connectButton.setDisabled(true);
        if (ipAddressField != null) ipAddressField.setDisabled(true);
    }
}
