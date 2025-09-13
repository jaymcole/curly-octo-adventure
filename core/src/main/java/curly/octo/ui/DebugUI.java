package curly.octo.ui;

import curly.octo.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import com.badlogic.gdx.scenes.scene2d.Actor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;

/**
 * Handles the debug UI overlay showing FPS, player position, etc.
 */
public class DebugUI {

    private Stage stage;
    private Label fpsLabel;
    private Label playerPositionLabel;
    private Label debugClientIPAddressLabel;
    private TextButton mapSeedButton;
    private TextButton mapRegenerateButton;
    private long currentMapSeed;
    private Label lightsLabel;
    private Label shadowLightsLabel;
    private Label physicsDebugLabel;
    private Label physicsStrategyLabel;

    // Callback for debug toggles
    public interface DebugListener {
        void onTogglePhysicsDebug();
        void onTogglePhysicsStrategy();
        void onRegenerateMap();
    }
    private DebugListener debugListener;

    public DebugUI() {
        createStage();
    }

    private void createStage() {
        stage = new Stage(new ScreenViewport());

        // Create skin
        Skin skin;
        try {
            skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        } catch (Exception e) {
            Log.error("DebugUI", "Failed to load UI skin: " + e.getMessage());
            e.printStackTrace();
            // Create a basic skin as fallback
            skin = new Skin();
        }

        // Create debug table
        Table debugTable = new Table();
        debugTable.setFillParent(true);
        debugTable.top().right().pad(10);

        // Debug title
        Label debugTitleLabel = new Label("Debug Info", skin, "subtitle");
        debugTitleLabel.setAlignment(Align.center);
        debugTable.add(debugTitleLabel).pad(10).row();

        // Client IP Address
        debugClientIPAddressLabel = new Label("Client IP: N/A", skin);
        debugTable.add(debugClientIPAddressLabel).pad(10).row();

        // Map Generation Seed Button
        currentMapSeed = Constants.MAP_GENERATION_SEED;
        mapSeedButton = new TextButton("Map Seed: " + currentMapSeed, skin);
        mapSeedButton.addListener(new ClickListener() {

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // Copy seed to clipboard
                try {
                    String seedString = String.valueOf(currentMapSeed);
                    StringSelection stringSelection = new StringSelection(seedString);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                } catch (Exception e) {
                    Log.error("DebugUI", "Failed to copy seed to clipboard: " + e.getMessage());
                }

                // Show feedback to user
                final String originalText = mapSeedButton.getText().toString();
                mapSeedButton.setText("Copied: " + currentMapSeed);

                // Revert text after delay
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Gdx.app.postRunnable(() -> mapSeedButton.setText(originalText));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                mapSeedButton.setColor(1f, 0.5f, 0.5f, 1f); // Back to reddish
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                mapSeedButton.setColor(1f, 0.7f, 0.7f, 1f); // Brighter reddish
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                mapSeedButton.setColor(1f, 0.5f, 0.5f, 1f); // Back to original reddish
            }
        });

        // Debug: Log button addition to table
        debugTable.add(mapSeedButton).size(200, 50).pad(10).row();

        // Map Regeneration Button
        mapRegenerateButton = new TextButton("Regenerate Map", skin);
        mapRegenerateButton.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // Trigger regeneration
                if (debugListener != null) {
                    debugListener.onRegenerateMap();
                }

                // Show feedback to user
                final String originalText = mapRegenerateButton.getText().toString();
                mapRegenerateButton.setText("Regenerating...");

                // Revert text after delay
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Gdx.app.postRunnable(() -> mapRegenerateButton.setText(originalText));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                return super.touchDown(event, x, y, pointer, button);
            }
        });

        debugTable.add(mapRegenerateButton).size(200, 50).pad(10).row();

        // Lighting System Limits
        Label lightingLimitsLabel = new Label("Lighting: " + Constants.LIGHTING_ENHANCED_SHADER_LIGHTS + "/" +
                                            Constants.LIGHTING_MAX_FALLBACK_LIGHTS + " (Enhanced/Fallback)", skin);
        debugTable.add(lightingLimitsLabel).pad(5).row();

        // Physics Settings
        Label physicsSettingsLabel = new Label("Physics: " + Math.round(Constants.PHYSICS_GRAVITY) +
                                             " gravity, " + Constants.PHYSICS_MAX_SUBSTEPS + " substeps", skin);
        debugTable.add(physicsSettingsLabel).pad(5).row();

        // Network & Performance Settings
        Label networkSettingsLabel = new Label("Network: " + (1000_000_000L / Constants.NETWORK_POSITION_UPDATE_INTERVAL_NS) +
                                             " FPS updates, Target: " + Constants.GAME_TARGET_FPS + " FPS", skin);
        debugTable.add(networkSettingsLabel).pad(5).row();

        // FPS
        fpsLabel = new Label("FPS: ...", skin);
        debugTable.add(fpsLabel).pad(10).row();

        // Player position
        playerPositionLabel = new Label("Position: ...", skin);
        debugTable.add(playerPositionLabel).pad(10).row();

        // Light count information
        lightsLabel = new Label("Lights: ...", skin);
        debugTable.add(lightsLabel).pad(10).row();

        shadowLightsLabel = new Label("Shadow Lights: ...", skin);
        debugTable.add(shadowLightsLabel).pad(10).row();

        // Physics debug info
        physicsDebugLabel = new Label("Physics Debug: OFF", skin);
        debugTable.add(physicsDebugLabel).pad(10).row();

        physicsStrategyLabel = new Label("Physics Strategy: ...", skin);
        debugTable.add(physicsStrategyLabel).pad(10).row();

        // Instructions
        Label instructionsLabel = new Label("F1: Physics Debug | F2: Physics Strategy | F3: Render Strategy", skin);
        instructionsLabel.setAlignment(Align.center);
        debugTable.add(instructionsLabel).pad(10).row();

        stage.addActor(debugTable);

        // Debug: Add input event listener to stage to see if events are reaching it
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return false; // Don't consume the event
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            }

            public boolean mouseMoved(InputEvent event, float x, float y) {
                // Log mouse movement occasionally to see if events are reaching the stage
                return false; // Don't consume the event
            }
        });

        // Debug: Log stage and table setup
        Log.info("DebugUI", "Created debug UI");
        Log.info("DebugUI", "Stage viewport: " + stage.getViewport().getScreenWidth() + "x" + stage.getViewport().getScreenHeight());
        Log.info("DebugUI", "Table bounds: " + debugTable.getX() + "," + debugTable.getY() + " " +
                debugTable.getWidth() + "x" + debugTable.getHeight());
        if (mapSeedButton != null) {
            Log.info("DebugUI", "Button bounds: " + mapSeedButton.getX() + "," + mapSeedButton.getY() + " " +
                    mapSeedButton.getWidth() + "x" + mapSeedButton.getHeight());
        }
    }

    public void update(float deltaTime) {
        stage.act(deltaTime);

        // Update FPS
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        // Debug: Log button state occasionally
        if (mapSeedButton != null && System.currentTimeMillis() % 5000 < 50) { // Log every ~5 seconds
            Log.info("DebugUI", "Button state - Text: " + mapSeedButton.getText() +
                    ", Visible: " + mapSeedButton.isVisible() +
                    ", Touchable: " + mapSeedButton.isTouchable() +
                    ", Stage: " + (mapSeedButton.getStage() != null ? "attached" : "not attached"));
        }

        // Handle keyboard input for debug toggles
        if (debugListener != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
                debugListener.onTogglePhysicsDebug();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
                debugListener.onTogglePhysicsStrategy();
            }
        }
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

    public void setPlayerPosition(float x, float y, float z) {
        playerPositionLabel.setText("Position: " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z));
    }

    public void setClientIP(String ip) {
        debugClientIPAddressLabel.setText("Client IP: " + ip);
    }

    public void setLightCounts(int totalLights, int shadowLights) {
        lightsLabel.setText("Lights: " + totalLights);
        shadowLightsLabel.setText("Shadow Lights: " + shadowLights);
    }

    public void setDebugListener(DebugListener listener) {
        this.debugListener = listener;
    }

    public void setPhysicsDebugEnabled(boolean enabled) {
        physicsDebugLabel.setText("Physics Debug: " + (enabled ? "ON" : "OFF"));
    }

    public void setPhysicsStrategy(String strategy, long triangleCount) {
        physicsStrategyLabel.setText("Physics: " + strategy + " (" + triangleCount + " triangles)");
    }

    public void setMapSeed(long seed) {
        currentMapSeed = seed;
        mapSeedButton.setText("Map Seed: " + seed);
    }

    /**
     * Get the stage for input processing.
     * Used by the input multiplexer to handle UI input.
     */
    public Stage getStage() {
        return stage;
    }
}
