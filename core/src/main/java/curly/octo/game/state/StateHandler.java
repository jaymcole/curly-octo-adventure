package curly.octo.game.state;

/**
 * Interface for handling state transitions and updates.
 * Implementations define the behavior for specific game states using the Strategy pattern.
 */
public interface StateHandler {
    
    /**
     * Called when entering this state.
     * This is where initialization, resource allocation, and setup should occur.
     * 
     * @param context The current state context with all state data
     */
    void onEnterState(StateContext context);
    
    /**
     * Called when exiting this state.
     * This is where cleanup, resource disposal, and finalization should occur.
     * 
     * @param context The current state context
     */
    void onExitState(StateContext context);
    
    /**
     * Called every frame while in this state.
     * This is where ongoing updates, progress tracking, and state logic should occur.
     * 
     * @param context The current state context
     * @param deltaTime Time in seconds since last update
     */
    void onUpdateState(StateContext context, float deltaTime);
    
    /**
     * Returns the states that this handler can transition to.
     * This is used for validation and safety checks.
     * 
     * @return Array of allowed transition states
     */
    GameState[] getAllowedTransitions();
    
    /**
     * Returns the state this handler is responsible for.
     * 
     * @return The GameState this handler manages
     */
    GameState getHandledState();
}