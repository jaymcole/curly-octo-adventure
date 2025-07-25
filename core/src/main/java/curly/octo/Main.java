package curly.octo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.player.PlayerController;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.GameMapRenderer;
import curly.octo.network.GameClient;
import curly.octo.network.GameServer;
import curly.octo.network.Network;
import curly.octo.player.PlayerUtilities;
import curly.octo.map.PhysicsManager;
import curly.octo.map.MapTile;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Main game class with network setup UI.
 * Allows starting a server or connecting as a client.
 */
public class Main extends ApplicationAdapter {
    private Stage lobbyStage;
    private TextButton startServerButton;
    private TextButton connectButton;
    private TextField ipAddressField;
    private Label statusLabel;
    private Random random;

    private Stage debugStage;
    private Label fpsLabel;
    private Label playerPositionLabel;

    private GameServer gameServer;
    private GameClient gameClient;

    // Voxel map components
    private GameMap voxelMap;
    private GameMapRenderer voxelMapRenderer;
    private boolean showUI = true;

    private ArrayList<PlayerController> players;
    private PlayerController localPlayerController;
    private long localPlayerId;

    // 3D rendering variables
    private ModelBatch modelBatch;
    private boolean show3DView = false;
    private Environment environment;

    // Networking
    private float positionUpdateTimer = 0;
    private static final float POSITION_UPDATE_INTERVAL = 1/60f; // 20 updates per second

    private DirectionalLight sun;

    private PhysicsManager physicsManager;

    private ShapeRenderer shapeRenderer;

    public Main() {
    }

    @Override
    public void create() {
        this.random = new Random();
        modelBatch = new ModelBatch();

        // Setup 3D environment
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        sun = new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f);

        environment.add(sun);

        // Initialize voxel renderer
        voxelMapRenderer = new GameMapRenderer();

        createLobbyStage();
        createDebugStage();
        Gdx.input.setInputProcessor(lobbyStage);
        shapeRenderer = new ShapeRenderer();
        Log.info("Main.create", "Done");
    }

    private void createLobbyStage() {
        // Create stage for UI
        lobbyStage = new Stage(new ScreenViewport());

        // If we were launched with command line args, skip the UI
        Table lobbyTable = new Table();
        lobbyTable.setFillParent(true);
        lobbyStage.addActor(lobbyTable);

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
        lobbyTable.add(statusLabel).colspan(2).pad(10);
        lobbyTable.row();
        lobbyTable.add(ipAddressField).width(200).pad(5);
        lobbyTable.add(connectButton).pad(5);
        lobbyTable.row();
        lobbyTable.add(startServerButton).colspan(2).pad(5);
    }
    private void createDebugStage() {
        debugStage = new Stage(new ScreenViewport());

        Table debugTable = new Table();
        debugTable.setFillParent(true);
        debugTable.align(Align.topLeft);
        debugStage.addActor(debugTable);

        Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        // Get local IP address
        // String ipAddress = "Local IP: ";
        // try {
        //     Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
        //     while (interfaces.hasMoreElements()) {
        //         java.net.NetworkInterface iface = interfaces.nextElement();
        //         // Skip loopback and inactive interfaces
        //         if (iface.isLoopback() || !iface.isUp()) continue;

        //         Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
        //         while (addresses.hasMoreElements()) {
        //             java.net.InetAddress addr = addresses.nextElement();
        //             // Skip loopback and link-local addresses
        //             if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;

        //             // Use the first non-loopback, non-link-local address
        //             ipAddress += addr.getHostAddress();
        //             break;
        //         }
        //         if (!ipAddress.equals("Local IP: ")) break;
        //     }
        // } catch (Exception e) {
        //     ipAddress = "Could not determine IP";
        // }

        String ipAddress = "Public IP: ";
        try {
            URL url = new URL("https://api.ipify.org");
            Scanner s = new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A");
            ipAddress += s.hasNext() ? s.next() : "Unavailable";
            s.close();
        } catch (Exception e) {
            ipAddress = "Could not determine public IP";
        }

        Label debugClientIPAddressLabel = new Label(ipAddress, skin);
        debugTable.add(debugClientIPAddressLabel).pad(10);

        debugTable.row();
        fpsLabel = new Label("...", skin);
        debugTable.add(fpsLabel).pad(10);

        debugTable.row();
        playerPositionLabel = new Label("...", skin);
        debugTable.add(playerPositionLabel).pad(10);
    }

    private void startServer() {
        try {

            localPlayerController = PlayerUtilities.createPlayerController(random);
            localPlayerId = localPlayerController.getPlayerId();
            localPlayerController.setVelocity(20f); // Adjust movement speed as needed
            // Provide map for collision
            if (voxelMap == null) {
                voxelMap = new GameMap(64, 6, 64, System.currentTimeMillis());
                voxelMap.generateDungeon();
                voxelMapRenderer.updateMap(voxelMap);
            }
            physicsManager = new PhysicsManager();
            // Add static blocks for the map (only ground layer)
            for (int x = 0; x < voxelMap.getWidth(); x++) {
                for (int y = 0; y < voxelMap.getHeight(); y++ ) {
                    for (int z = 0; z < voxelMap.getDepth(); z++) {
                        MapTile tile = voxelMap.getTile(x, y, z);
                        if (tile.geometryType != curly.octo.map.enums.MapTileGeometryType.EMPTY) {
                            physicsManager.addStaticBlock(tile.x, tile.y, tile.z, MapTile.TILE_SIZE);
                        }
                    }
                }
            }
                // Add player to physics world
            float playerRadius = 1.0f;
            float playerHeight = 5.0f;
            float playerMass = 10.0f;
            Vector3 playerStart = new Vector3(15, 15, 15);
            physicsManager.addPlayer(playerStart.x, playerStart.y, playerStart.z, playerRadius, playerHeight, playerMass);

            localPlayerController.setGameMap(voxelMap);
            players = new ArrayList<>();
            players.add(localPlayerController);

            // Generate map before starting server
            if (voxelMap == null) {
                voxelMap = new GameMap(64, 6, 64, System.currentTimeMillis());
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
            this.players = new ArrayList<>();
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
                    setPlayer(receivedPlayerId.playerId);
                });
            });

            gameClient.setPlayerRosterListener(roster -> {
                Gdx.app.postRunnable(() -> {
                    HashSet<Long> currentPlayers = new HashSet<>();
                    for (PlayerController player : players) {
                        currentPlayers.add(player.getPlayerId());
                    }

                    for (PlayerController player : roster.players) {
                        if (!currentPlayers.contains(player.getPlayerId())) {
                            players.add(player);
                        }
                    }
                });
            });

            gameClient.setPlayerUpdateListener(playerUpdate -> {
                Gdx.app.postRunnable(() -> {
                    Log.info("PlayerUpdate", "Received position update for player " + playerUpdate.playerId + ": " +
                            playerUpdate.x + ", " + playerUpdate.y + ", " + playerUpdate.z);
                    for (PlayerController player : players) {
                        if (player.getPlayerId() == playerUpdate.playerId) {
                            // Skip updating the local player to prevent position override
                            if (player != localPlayerController) {
                                player.setPlayerPosition(playerUpdate.x, playerUpdate.y, playerUpdate.z);
                            }
                            break;
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
                    localPlayerController.setPlayerId(localPlayerId); // Ensure the controller's id matches the server-assigned id
                    Gdx.input.setInputProcessor(localPlayerController);
                    break;
                }
            }
        }
    }

    private void updateUIForServer() {
        if (lobbyStage != null) {
            lobbyStage.clear();
            lobbyStage.addActor(statusLabel);
        }
        if (startServerButton != null) startServerButton.setDisabled(true);
        if (connectButton != null) connectButton.setDisabled(true);
        if (ipAddressField != null) ipAddressField.setDisabled(true);
    }

    private void sendPositionUpdate() {
        if (gameClient != null && localPlayerController != null) {
            Vector3 position = localPlayerController.getCamera().position;
            PlayerUpdate update = new PlayerUpdate(
                localPlayerId,
                position
            );
            gameClient.sendUDP(update);
            Log.debug("Client", "Sent position update: " + position.x + ", " + position.y + ", " + position.z);
        }
    }


    private float sunAngle = 0f;

    @Override
    public void render() {
        // Update sun position in a circular motion
        sunAngle += Gdx.graphics.getDeltaTime() * 1f; // Adjust rotation speed as needed
        float radius = 10f; // Radius of the sun's circular path
        float sunX = (float) Math.cos(sunAngle) * radius;
        float sunZ = (float) Math.sin(sunAngle) * radius;
        sun.setDirection(new Vector3(-sunX, -0.8f, sunZ).nor());
//        -1f, -0.8f, -0.2f
        float deltaTime = Gdx.graphics.getDeltaTime();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        if (localPlayerController != null) {
            float x = Math.round(localPlayerController.getPosition().x);
            float y = Math.round(localPlayerController.getPosition().y);
            float z = Math.round(localPlayerController.getPosition().z);
            playerPositionLabel.setText("Position: " + x + ", " + y + ", " + z);
        }

        if (show3DView) {
            // Update camera and player position
            if (localPlayerController != null) {
                localPlayerController.update(deltaTime);
                voxelMapRenderer.render(localPlayerController.getCamera(), environment);

                // Handle periodic position updates
                positionUpdateTimer += deltaTime;
                if (positionUpdateTimer >= POSITION_UPDATE_INTERVAL) {
                    positionUpdateTimer = 0;
                    sendPositionUpdate();
                    // If running as server/host, broadcast host's position to all clients
                    if (gameServer != null) {
                        gameServer.broadcastPlayerPosition(localPlayerId, localPlayerController.getPosition());
                    }
                }
            }

            // Render all players
            if (players != null) {
                for(PlayerController player : players) {
                    // Only render other players if we have a local player controller
                    if (localPlayerController != null && player.getPlayerId() != localPlayerController.getPlayerId()) {
                        player.render(modelBatch, environment, localPlayerController.getCamera());
                    }
                }
            }
        }

        // Update and draw UI if visible
        if (showUI) {
            lobbyStage.act(Math.min(deltaTime, 1 / 30f));
            lobbyStage.draw();
        }

        debugStage.act(Math.min(deltaTime, 1 / 30f));
        debugStage.draw();

        // Step physics world
        if (physicsManager != null) {
            localPlayerController.setPhysicsManager(physicsManager);
            physicsManager.step(deltaTime);
            // Update player position from Bullet
            Vector3 bulletPlayerPos = physicsManager.getPlayerPosition();
            if (localPlayerController != null) {
                localPlayerController.setPlayerPosition(bulletPlayerPos.x, bulletPlayerPos.y, bulletPlayerPos.z);
            }
        }
        int error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            System.out.println("OpenGL Error: " + error);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Update viewport for 2D UI
        lobbyStage.getViewport().update(width, height, true);

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
        lobbyStage.dispose();
        debugStage.dispose();
        if (physicsManager != null) physicsManager.dispose();
        shapeRenderer.dispose();
    }
}
