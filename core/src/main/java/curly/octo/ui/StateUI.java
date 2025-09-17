package curly.octo.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import curly.octo.ui.screens.StateScreen;
import curly.octo.ui.screens.MapTransferScreen;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages modal UI screens for different game states.
 * Integrates with the existing UI system by providing overlay screens
 * that appear over the game world during state transitions.
 */
public class StateUI {
    
    private Stage stage;
    private Skin skin;
    private StateScreen currentScreen;
    private final Map<GameState, StateScreen> screens;
    private boolean visible = false;
    private Table backgroundTable;
    
    public StateUI() {
        this.screens = new ConcurrentHashMap<>();
        createStage();
        createScreens();
    }
    
    private void createStage() {
        stage = new Stage(new ScreenViewport());
        
        // Load the UI skin (same as other UI components)
        try {
            skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
            Log.info("StateUI", "Successfully loaded UI skin");
        } catch (Exception e) {
            Log.error("StateUI", "Failed to load UI skin: " + e.getMessage());
            // Create a basic fallback skin
            skin = createFallbackSkin();
        }
        
        // Create semi-transparent background overlay
        createBackgroundOverlay();
    }
    
    private void createBackgroundOverlay() {
        // Create a semi-transparent background to dim the game world
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(0, 0, 0, 0.7f); // Dark semi-transparent
        pixmap.fill();
        Texture backgroundTexture = new Texture(pixmap);
        pixmap.dispose();
        
        TextureRegionDrawable background = new TextureRegionDrawable(new TextureRegion(backgroundTexture));
        
        backgroundTable = new Table();
        backgroundTable.setFillParent(true);
        backgroundTable.setBackground(background);
        
        // Initially invisible
        backgroundTable.setVisible(false);
        stage.addActor(backgroundTable);
    }
    
    private Skin createFallbackSkin() {
        Log.warn("StateUI", "Creating fallback UI skin");
        Skin fallbackSkin = new Skin();
        
        try {
            // Create basic UI elements for fallback
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            fallbackSkin.add("white", new Texture(pixmap));
            pixmap.dispose();
            
            // Try to get or create a basic font
            BitmapFont font = null;
            try {
                // Try to get the default font first
                font = fallbackSkin.getFont("default-font");
            } catch (Exception e) {
                // Create a default font if none exists
                font = new BitmapFont(); // Uses LibGDX's built-in font
            }
            
            // Basic label style
            Label.LabelStyle labelStyle = new Label.LabelStyle();
            labelStyle.font = font;
            labelStyle.fontColor = Color.WHITE;
            fallbackSkin.add("default", labelStyle);
            
            // Basic progress bar style
            ProgressBar.ProgressBarStyle progressStyle = new ProgressBar.ProgressBarStyle();
            Texture whiteTexture = fallbackSkin.get("white", Texture.class);
            progressStyle.background = new TextureRegionDrawable(new TextureRegion(whiteTexture));
            progressStyle.knob = new TextureRegionDrawable(new TextureRegion(whiteTexture));
            fallbackSkin.add("default-horizontal", progressStyle);
            
        } catch (Exception e) {
            Log.error("StateUI", "Failed to create fallback skin: " + e.getMessage());
            // Return a minimal skin that at least won't crash
            fallbackSkin = new Skin();
        }
        
        return fallbackSkin;
    }
    
    private void createScreens() {
        // Create screens for states that need UI
        MapTransferScreen mapTransferScreen = new MapTransferScreen(skin);

        // Register screen for all map processing states (both regeneration and transfer)
        screens.put(GameState.MAP_REGENERATION_CLEANUP, mapTransferScreen);
        screens.put(GameState.MAP_REGENERATION_DOWNLOADING, mapTransferScreen);
        screens.put(GameState.MAP_REGENERATION_REBUILDING, mapTransferScreen);
        screens.put(GameState.MAP_REGENERATION_COMPLETE, mapTransferScreen);
        screens.put(GameState.MAP_TRANSFER_DOWNLOADING, mapTransferScreen);
        screens.put(GameState.MAP_TRANSFER_REBUILDING, mapTransferScreen);
        screens.put(GameState.MAP_TRANSFER_COMPLETE, mapTransferScreen);
        
        Log.info("StateUI", "Created state screens for " + screens.size() + " states");
    }
    
    /**
     * Show the appropriate screen for the given state
     */
    public void showStateScreen(GameState state, StateContext context) {
        StateScreen screen = screens.get(state);
        
        if (screen != null) {
            // Hide current screen if different
            if (currentScreen != screen) {
                hideCurrentScreen();
                currentScreen = screen;
            }
            
            // Update the screen with current context
            currentScreen.updateContext(context);
            
            // Show the screen
            showScreen(currentScreen);
            
            Log.info("StateUI", "Showing screen for state: " + state.getDisplayName());
        } else {
            // No screen for this state, hide any current screen
            hideCurrentScreen();
            Log.debug("StateUI", "No screen defined for state: " + state.getDisplayName());
        }
    }
    
    /**
     * Update progress for the current screen
     */
    public void updateProgress(StateContext context) {
        Log.info("StateUI", "updateProgress called - visible: " + visible + ", currentScreen: " + (currentScreen != null));
        if (currentScreen != null && visible) {
            Log.info("StateUI", "Calling updateContext on current screen");
            currentScreen.updateContext(context);
        }
    }
    
    /**
     * Hide all state screens
     */
    public void hideAllScreens() {
        hideCurrentScreen();
    }
    
    private void showScreen(StateScreen screen) {
        if (screen != null && !visible) {
            // Clear any existing content
            backgroundTable.clear();
            
            // Add the screen content to our background table
            Table screenTable = screen.createUI();
            backgroundTable.add(screenTable).expand().center();
            
            // Show the background overlay and content
            backgroundTable.setVisible(true);
            visible = true;
            
            Log.debug("StateUI", "Screen made visible");
        }
    }
    
    private void hideCurrentScreen() {
        if (visible) {
            backgroundTable.setVisible(false);
            backgroundTable.clear();
            visible = false;
            currentScreen = null;
            
            Log.debug("StateUI", "Screen hidden");
        }
    }
    
    /**
     * Update the UI (should be called each frame)
     */
    public void update(float deltaTime) {
        if (visible && stage != null) {
            stage.act(deltaTime);
        }
    }
    
    /**
     * Render the UI (should be called each frame)
     */
    public void render() {
        if (visible && stage != null) {
            // Enable blending for transparency
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            
            stage.draw();
            
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }
    
    /**
     * Handle window resize
     */
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }
    
    /**
     * Get the input processor for this UI (for input handling)
     */
    public Stage getStage() {
        return stage;
    }
    
    /**
     * Check if a state screen is currently visible
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * Get the current state screen being displayed
     */
    public StateScreen getCurrentScreen() {
        return currentScreen;
    }
    
    /**
     * Dispose of resources
     */
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        
        // Dispose all screens
        for (StateScreen screen : screens.values()) {
            screen.dispose();
        }
    }
}