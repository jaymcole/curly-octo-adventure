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
import curly.octo.game.regeneration.MapRegenerationCoordinator;
import curly.octo.game.regeneration.RegenerationProgressListener;
import curly.octo.game.regeneration.RegenerationPhase;
import curly.octo.ui.screens.StateScreen;
import curly.octo.ui.screens.MapRegenerationScreen;

/**
 * Simplified UI manager for regeneration overlay screen.
 * Shows the map regeneration progress over the game world.
 */
public class StateUI implements RegenerationProgressListener {
    
    private Stage stage;
    private Skin skin;
    private MapRegenerationScreen regenerationScreen;
    private boolean visible = false;
    private Table backgroundTable;
    private MapRegenerationCoordinator coordinator;
    
    public StateUI() {
        createStage();
    }
    
    /**
     * Set the regeneration coordinator to listen for progress updates
     */
    public void setRegenerationCoordinator(MapRegenerationCoordinator coordinator) {
        Log.info("StateUI", "setRegenerationCoordinator called with coordinator: " + (coordinator != null ? "valid" : "null"));
        
        // Remove from old coordinator if any
        if (this.coordinator != null) {
            this.coordinator.removeProgressListener(this);
            Log.info("StateUI", "Removed listener from old coordinator");
        }
        
        this.coordinator = coordinator;
        
        // Add to new coordinator
        if (coordinator != null) {
            coordinator.addProgressListener(this);
            createScreens(coordinator);
            Log.info("StateUI", "Successfully added progress listener to coordinator");
        } else {
            Log.warn("StateUI", "Cannot add progress listener - coordinator is null");
        }
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
    
    private void createScreens(MapRegenerationCoordinator coordinator) {
        // Create single regeneration screen that listens to coordinator
        regenerationScreen = new MapRegenerationScreen(skin, coordinator);
        
        Log.info("StateUI", "Created regeneration screen");
    }
    
    /**
     * Show the regeneration screen (called automatically when regeneration starts)
     */
    private void showRegenerationScreen() {
        if (regenerationScreen != null && !visible) {
            showScreen(regenerationScreen);
            Log.info("StateUI", "Showing regeneration screen");
        }
    }
    
    // RegenerationProgressListener implementation
    @Override
    public void onPhaseChanged(RegenerationPhase phase, String message) {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            if (phase == RegenerationPhase.CLEANUP) {
                // Show screen when regeneration starts
                showRegenerationScreen();
            }
            Log.debug("StateUI", "Phase changed to: " + phase.getDisplayName());
        });
    }
    
    @Override
    public void onProgressChanged(float progress) {
        // No UI modifications needed for progress updates
        Log.debug("StateUI", "Progress updated to: " + (progress * 100) + "%");
    }
    
    @Override
    public void onCompleted() {
        Log.info("StateUI", "*** onCompleted() callback received! ***");
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            Log.info("StateUI", "Regeneration completed, hiding screen (on main thread)");
            hideRegenerationScreen();
            Log.info("StateUI", "Screen hide request completed");
        });
    }
    
    @Override
    public void onError(String errorMessage) {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            Log.error("StateUI", "Regeneration error: " + errorMessage);
            // Keep screen visible to show error
        });
    }
    
    /**
     * Hide the regeneration screen
     */
    public void hideRegenerationScreen() {
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
        Log.info("StateUI", "hideCurrentScreen called - visible: " + visible);
        if (visible) {
            backgroundTable.setVisible(false);
            backgroundTable.clear();
            visible = false;
            
            Log.info("StateUI", "*** SCREEN SUCCESSFULLY HIDDEN ***");
        } else {
            Log.warn("StateUI", "hideCurrentScreen called but screen was not visible");
        }
    }
    
    /**
     * Update the UI (should be called each frame)
     */
    public void update(float deltaTime) {
        if (visible && stage != null) {
            try {
                stage.act(deltaTime);
            } catch (Exception e) {
                Log.error("StateUI", "Error during stage update, hiding screen to prevent further crashes", e);
                // Safely hide the screen to prevent further crashes
                hideCurrentScreen();
            }
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
     * Get the regeneration screen
     */
    public MapRegenerationScreen getRegenerationScreen() {
        return regenerationScreen;
    }
    
    /**
     * Dispose of resources
     */
    public void dispose() {
        // Unregister from coordinator
        if (coordinator != null) {
            coordinator.removeProgressListener(this);
        }
        
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        
        // Dispose regeneration screen
        if (regenerationScreen != null) {
            regenerationScreen.dispose();
        }
    }
}