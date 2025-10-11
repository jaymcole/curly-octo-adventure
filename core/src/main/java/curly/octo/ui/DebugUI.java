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
import curly.octo.Main;

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
    private Table debugTable;
    private Main mainInstance;

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
        skin = UIAssetCache.getDefaultSkin();

        // Create debug table
        debugTable = new Table();
        debugTable.setFillParent(true);
        debugTable.top().right().pad(10);

        // Debug title
        Label debugTitleLabel = new Label("Debug Info", skin, "subtitle");
        debugTitleLabel.setAlignment(Align.center);
        debugTable.add(debugTitleLabel).pad(10).row();

        // Client IP Address
        debugClientIPAddressLabel = new Label("Client IP: N/A", skin);
        debugTable.add(debugClientIPAddressLabel).pad(10).row();

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
                // Restore original color
                mapSeedButton.setColor(1f, 0.5f, 0.5f, 1f); // Back to reddish
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                // Make button brighter on hover
                mapSeedButton.setColor(1f, 0.7f, 0.7f, 1f); // Brighter reddish
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                // Restore original color
                mapSeedButton.setColor(1f, 0.5f, 0.5f, 1f); // Back to original reddish
            }
        });

        // Debug: Log button addition to table
        if (Main.isHostClient) {
            debugTable.add(mapSeedButton).size(200, 50).pad(10).row();
        }

        // Map Regeneration Button
        mapRegenerateButton = new TextButton("Regenerate Map", skin);
        mapRegenerateButton.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Log.info("DebugUI", "Map regeneration button clicked!");

                // Trigger regeneration
                if (debugListener != null) {
                    debugListener.onRegenerateMap();
                } else {
                    Log.warn("DebugUI", "No debug listener set for map regeneration");
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

        if (Main.isHostClient) {
            debugTable.add(mapRegenerateButton).size(200, 50).pad(10).row();
        }

        // FPS
        fpsLabel = new Label("FPS: ...", skin);
        debugTable.add(fpsLabel).pad(10).row();

        stage.addActor(debugTable);

        // Debug: Add input event listener to stage to see if events are reaching it
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Log.info("DebugUI", "Stage touchDown: x=" + x + ", y=" + y + ", pointer=" + pointer + ", button=" + button);
                return false; // Don't consume the event
            }

            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                Log.info("DebugUI", "Stage touchUp: x=" + x + ", y=" + y + ", pointer=" + pointer + ", button=" + button);
            }

            public boolean mouseMoved(InputEvent event, float x, float y) {
                // Log mouse movement occasionally to see if events are reaching the stage
                if (System.currentTimeMillis() % 10000 < 50) { // Log every ~10 seconds
                    Log.info("DebugUI", "Stage mouseMoved: x=" + x + ", y=" + y);
                }
                return false; // Don't consume the event
            }
        });
    }

    private boolean isHostClientLastStatus = Boolean.valueOf(Main.isHostClient);
    public void update(float deltaTime) {
        if (isHostClientLastStatus != Main.isHostClient) {
            isHostClientLastStatus = Boolean.valueOf(Main.isHostClient);
            createStage();
            // Re-register the new stage with the input multiplexer
            if (mainInstance != null) {
                mainInstance.updateInputMultiplexer();
            }
        }

        stage.act(deltaTime);
        // Update FPS
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

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
//        playerPositionLabel.setText("Position: " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z));
    }

    public void setClientIP(String ip) {
        debugClientIPAddressLabel.setText("Client IP: " + ip);
    }

    public void setLightCounts(int totalLights, int shadowLights) {
//        lightsLabel.setText("Lights: " + totalLights);
//        shadowLightsLabel.setText("Shadow Lights: " + shadowLights);
    }

    public void setDebugListener(DebugListener listener) {
        this.debugListener = listener;
    }

    public void setMainInstance(Main mainInstance) {
        this.mainInstance = mainInstance;
    }

    public void setPhysicsDebugEnabled(boolean enabled) {
//        physicsDebugLabel.setText("Physics Debug: " + (enabled ? "ON" : "OFF"));
    }

    public void setPhysicsStrategy(String strategy, long triangleCount) {
//        physicsStrategyLabel.setText("Physics: " + strategy + " (" + triangleCount + " triangles)");
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
