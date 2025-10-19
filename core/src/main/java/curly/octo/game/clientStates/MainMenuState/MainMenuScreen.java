package curly.octo.game.clientStates.MainMenuState;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.ui.UIAssetCache;

import static curly.octo.Constants.DEFAULT_SCREEN_HEIGHT;
import static curly.octo.Constants.DEFAULT_SCREEN_WIDTH;

public class MainMenuScreen extends BaseScreen {

    private TextButton startServerButton;
    private TextButton connectButton;
    private TextField ipAddressField;
    private Label statusLabel;

    private final MainMenuScreen.MainMenuListener listener;

    public interface MainMenuListener {
        void onStartServer();
        void onConnectToServer(String host);
    }

    public MainMenuScreen(MainMenuScreen.MainMenuListener listener) {
        this.listener = listener;
        createStage();
    }

    @Override
    protected void createStage() {
        stage = new Stage(new ExtendViewport(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT));

        // Create skin
        Skin skin = UIAssetCache.getDefaultSkin();

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

    public void disableInputs() {
        if (startServerButton != null) startServerButton.setDisabled(true);
        if (connectButton != null) connectButton.setDisabled(true);
        if (ipAddressField != null) ipAddressField.setDisabled(true);
    }
}
