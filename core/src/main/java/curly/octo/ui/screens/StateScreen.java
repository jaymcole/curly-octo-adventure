package curly.octo.ui.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import curly.octo.game.state.StateContext;

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
     * Update the screen with the current state context.
     * This is called whenever the state data changes.
     * 
     * @param context Current state context with progress and data
     */
    void updateContext(StateContext context);
    
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