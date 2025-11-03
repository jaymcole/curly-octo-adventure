package curly.octo.client.clientStates.mainMenuState;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.ui.UIAssetCache;

import static curly.octo.common.Constants.DEFAULT_SCREEN_HEIGHT;
import static curly.octo.common.Constants.DEFAULT_SCREEN_WIDTH;

public class MainMenuScreen extends BaseScreen {

    private final MainMenuScreen.MainMenuListener listener;

    private TextField ipAddressField;

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
        Label titleLabel = new Label("Game", skin, "subtitle");
        titleLabel.setAlignment(Align.center);
        mainTable.add(titleLabel).colspan(2).padBottom(30).row();

        // Server section
        addHostGameSubheader(mainTable, skin);
        addStartServerButton(mainTable, skin);

        // Client section
        addJoinGameSubheader(mainTable, skin);
        addIpAddressActors(mainTable, skin);
        addUniqueIdActors(mainTable, skin);
        addPreferredNameActors(mainTable, skin);
        addConnectButton(mainTable, skin);

        stage.addActor(mainTable);

        Log.info("LobbyUI", "Created lobby UI");
    }

    private void addHostGameSubheader(Table mainTable, Skin skin) {
        Label serverLabel = new Label("Host a Game", skin, "subtitle");
        serverLabel.setAlignment(Align.center);
        mainTable.add(serverLabel).colspan(2).padBottom(10).row();

    }

    private void addStartServerButton(Table mainTable, Skin skin) {
        TextButton startServerButton = new TextButton("Start Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    listener.onStartServer();
                }
            }
        });
        mainTable.add(startServerButton).colspan(2).padBottom(30).row();
    }

    private void addJoinGameSubheader(Table mainTable, Skin skin) {
        Label clientLabel = new Label("Join a Game", skin, "subtitle");
        clientLabel.setAlignment(Align.center);
        mainTable.add(clientLabel).colspan(2).padBottom(10).row();
    }

    private void addIpAddressActors(Table mainTable, Skin skin) {
        // IP Address input
        Label ipLabel = new Label("Server IP:", skin);
        mainTable.add(ipLabel).padRight(10);

        ipAddressField = new TextField("localhost", skin);
        ipAddressField.setMaxLength(15);
        mainTable.add(ipAddressField).padBottom(10).row();
    }

    private void addUniqueIdActors(Table mainTable, Skin skin) {
        Label uniqueIdLabel = new Label("Unique ID: ", skin);
        mainTable.add(uniqueIdLabel).padRight(10);
        TextField uniqueIdField = new TextField("s", skin);
        uniqueIdField.setMaxLength(150);
        mainTable.add(uniqueIdField).padBottom(10).row();
    }

    private void addPreferredNameActors(Table mainTable, Skin skin) {
        Label uniqueIdLabel = new Label("Preferred Name: ", skin);
        mainTable.add(uniqueIdLabel).padRight(10);
        TextField preferredNameField = new TextField("nothing", skin);
        preferredNameField.setMaxLength(45);
        mainTable.add(preferredNameField).padBottom(10).row();

    }

    private void addConnectButton(Table mainTable, Skin skin) {
        TextButton connectButton = new TextButton("Connect", skin);
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
    }
}
