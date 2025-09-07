package curly.octo.game.state;

import com.esotericsoftware.minlog.Log;

/**
 * Abstract base class for state handlers that provides common functionality
 * and default implementations. Concrete handlers can extend this class.
 */
public abstract class AbstractStateHandler implements StateHandler {
    
    protected final GameState handledState;
    protected final GameState[] allowedTransitions;
    
    public AbstractStateHandler(GameState handledState, GameState... allowedTransitions) {
        this.handledState = handledState;
        this.allowedTransitions = allowedTransitions != null ? allowedTransitions : new GameState[0];
    }
    
    @Override
    public GameState getHandledState() {
        return handledState;
    }
    
    @Override
    public GameState[] getAllowedTransitions() {
        return allowedTransitions.clone(); // Return copy to prevent modification
    }
    
    @Override
    public void onEnterState(StateContext context) {
        Log.info("StateHandler", "Entering state: " + handledState.getDisplayName());
        // Default implementation - subclasses can override
    }
    
    @Override
    public void onExitState(StateContext context) {
        Log.info("StateHandler", "Exiting state: " + handledState.getDisplayName());
        // Default implementation - subclasses can override
    }
    
    @Override
    public void onUpdateState(StateContext context, float deltaTime) {
        // Default implementation does nothing - subclasses should override for active states
    }
    
    /**
     * Utility method to check if a transition to the given state is allowed
     */
    protected boolean isTransitionAllowed(GameState targetState) {
        for (GameState allowed : allowedTransitions) {
            if (allowed == targetState) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Utility method to log state handler actions
     */
    protected void logAction(String action) {
        Log.info("StateHandler", handledState.getDisplayName() + ": " + action);
    }
    
    /**
     * Utility method to update progress and log the change
     */
    protected void updateProgress(StateContext context, float progress, String message) {
        context.updateProgress(progress, message);
        logAction(String.format("Progress: %.1f%% - %s", progress * 100, message));
    }
}