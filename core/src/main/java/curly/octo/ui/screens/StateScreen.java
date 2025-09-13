package curly.octo.ui.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
// StateContext import removed - using coordinator system now

/**
 * Interface for state-specific UI screens.
 * Each screen is responsible for displaying UI appropriate for a particular game state.
 */
public interface StateScreen {
    
    /**
     * Create the UI table for this screen.
     * This method should build and return a Table containing all UI elements.
     * 
     * @return Table containing the screen's UI elements
     */
    Table createUI();
    
    /**
     * Legacy update method - kept for compatibility.
     * New screens should implement RegenerationProgressListener instead.
     * 
     * @param context Legacy context (may be null)
     */
    default void updateContext(Object context) {
        // Default implementation does nothing - for backward compatibility
    }
    
    /**
     * Get the title to display for this screen.
     * 
     * @return Screen title
     */
    String getTitle();
    
    /**
     * Clean up any resources used by this screen.
     */
    void dispose();
}