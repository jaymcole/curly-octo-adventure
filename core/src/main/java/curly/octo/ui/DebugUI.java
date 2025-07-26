package curly.octo.ui;

import com.badlogic.gdx.Gdx;
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
        
        stage.addActor(debugTable);
        
        Log.info("DebugUI", "Created debug UI");
    }
    
    public void update(float deltaTime) {
        stage.act(deltaTime);
        
        // Update FPS
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
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
} 