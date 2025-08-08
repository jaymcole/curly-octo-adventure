package curly.octo.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;

/**
 * Handles the debug UI overlay showing FPS, player position, etc.
 */
public class DebugUI {

    private Stage stage;
    private Label fpsLabel;
    private Label playerPositionLabel;
    private Label debugClientIPAddressLabel;
    private Label lightsLabel;
    private Label shadowLightsLabel;
    private Label shadowMapDebugLabel;
    private Label physicsDebugLabel;
    private Label physicsStrategyLabel;
    private Label renderingStrategyLabel;

    // Callback for debug toggles
    public interface DebugListener {
        void onTogglePhysicsDebug();
        void onTogglePhysicsStrategy();
        void onToggleRenderingStrategy();
        void onToggleShadowMapDebug();
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
            Log.info("DebugUI", "Successfully loaded UI skin");
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

        // Shadow map debug info
        shadowMapDebugLabel = new Label("Shadow Map Debug: OFF", skin);
        debugTable.add(shadowMapDebugLabel).pad(10).row();

        // Physics debug info
        physicsDebugLabel = new Label("Physics Debug: OFF", skin);
        debugTable.add(physicsDebugLabel).pad(10).row();

        physicsStrategyLabel = new Label("Physics Strategy: ...", skin);
        debugTable.add(physicsStrategyLabel).pad(10).row();

        // Rendering strategy info
        renderingStrategyLabel = new Label("Rendering Strategy: ...", skin);
        debugTable.add(renderingStrategyLabel).pad(10).row();

        // Instructions
        Label instructionsLabel = new Label("F1: Physics Debug | F2: Physics Strategy \nF3: Render Strategy | F4: Shadow Maps", skin);
        instructionsLabel.setAlignment(Align.center);
        debugTable.add(instructionsLabel).pad(10).row();

        stage.addActor(debugTable);

        Log.info("DebugUI", "Created debug UI");
    }

    public void update(float deltaTime) {
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
            if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
                debugListener.onToggleRenderingStrategy();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
                debugListener.onToggleShadowMapDebug();
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

    public void setRenderingStrategy(String strategy, long facesBuilt, long tilesProcessed) {
        renderingStrategyLabel.setText("Render: " + strategy + " (" + facesBuilt + " faces, " + tilesProcessed + " tiles)");
    }

    public void setShadowMapDebugEnabled(boolean enabled) {
        shadowMapDebugLabel.setText("Shadow Map Debug: " + (enabled ? "ON" : "OFF"));
    }
}
