package curly.octo.game.state;

import com.esotericsoftware.minlog.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central manager for game state transitions and updates.
 * This class coordinates state changes, manages state handlers, and notifies listeners.
 * Thread-safe for use from both main thread and network threads.
 */
public class GameStateManager {
    
    private final StateContext context;
    private final Map<GameState, StateHandler> handlers;
    private final List<StateChangeListener> listeners;
    private volatile boolean isTransitioning;
    
    public interface StateChangeListener {
        void onStateChanged(GameState oldState, GameState newState, StateContext context);
        void onStateProgressUpdated(StateContext context);
    }
    
    public GameStateManager() {
        this(GameState.LOBBY);
    }
    
    public GameStateManager(GameState initialState) {
        this.context = new StateContext(initialState);
        this.handlers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.isTransitioning = false;
        
        Log.info("GameStateManager", "Initialized with state: " + initialState.getDisplayName());
    }
    
    // Handler management
    public void registerHandler(StateHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        GameState state = handler.getHandledState();
        StateHandler existing = handlers.put(state, handler);
        
        if (existing != null) {
            Log.warn("GameStateManager", "Replaced existing handler for state: " + state.getDisplayName());
        } else {
            Log.info("GameStateManager", "Registered handler for state: " + state.getDisplayName());
        }
    }
    
    public void unregisterHandler(GameState state) {
        StateHandler removed = handlers.remove(state);
        if (removed != null) {
            Log.info("GameStateManager", "Unregistered handler for state: " + state.getDisplayName());
        }
    }
    
    // Listener management
    public void addStateChangeListener(StateChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.debug("GameStateManager", "Added state change listener");
        }
    }
    
    public void removeStateChangeListener(StateChangeListener listener) {
        if (listeners.remove(listener)) {
            Log.debug("GameStateManager", "Removed state change listener");
        }
    }
    
    // State management
    public GameState getCurrentState() {
        return context.getCurrentState();
    }
    
    public StateContext getStateContext() {
        return context;
    }
    
    public boolean isTransitioning() {
        return isTransitioning;
    }
    
    /**
     * Request a state transition. This method is thread-safe and will validate
     * the transition before executing it.
     */
    public synchronized boolean requestStateChange(GameState newState) {
        return requestStateChange(newState, null);
    }
    
    /**
     * Request a state transition with additional data.
     */
    public synchronized boolean requestStateChange(GameState newState, Map<String, Object> stateData) {
        if (newState == null) {
            Log.error("GameStateManager", "Cannot transition to null state");
            return false;
        }
        
        if (isTransitioning) {
            Log.warn("GameStateManager", "State transition already in progress, ignoring request to: " + newState.getDisplayName());
            return false;
        }
        
        GameState currentState = context.getCurrentState();
        if (currentState == newState) {
            Log.debug("GameStateManager", "Already in requested state: " + newState.getDisplayName());
            return true;
        }
        
        // Validate transition
        if (!isTransitionAllowed(currentState, newState)) {
            Log.error("GameStateManager", String.format("Invalid transition from %s to %s", 
                currentState.getDisplayName(), newState.getDisplayName()));
            return false;
        }
        
        try {
            performStateTransition(currentState, newState, stateData);
            return true;
        } catch (Exception e) {
            Log.error("GameStateManager", "Failed to transition to state: " + newState.getDisplayName(), e);
            return false;
        }
    }
    
    private boolean isTransitionAllowed(GameState currentState, GameState newState) {
        StateHandler currentHandler = handlers.get(currentState);
        if (currentHandler == null) {
            // If no handler, allow any transition (graceful degradation)
            Log.warn("GameStateManager", "No handler for current state: " + currentState.getDisplayName());
            return true;
        }
        
        GameState[] allowedTransitions = currentHandler.getAllowedTransitions();
        for (GameState allowed : allowedTransitions) {
            if (allowed == newState) {
                return true;
            }
        }
        
        return false;
    }
    
    private void performStateTransition(GameState oldState, GameState newState, Map<String, Object> stateData) {
        isTransitioning = true;
        
        try {
            Log.info("GameStateManager", String.format("Transitioning from %s to %s", 
                oldState.getDisplayName(), newState.getDisplayName()));
            
            // Exit old state
            StateHandler oldHandler = handlers.get(oldState);
            if (oldHandler != null) {
                try {
                    oldHandler.onExitState(context);
                } catch (Exception e) {
                    Log.error("GameStateManager", "Error exiting state: " + oldState.getDisplayName(), e);
                }
            }
            
            // Update context
            context.setState(newState);
            
            // Add any provided state data
            if (stateData != null) {
                for (Map.Entry<String, Object> entry : stateData.entrySet()) {
                    context.setStateData(entry.getKey(), entry.getValue());
                }
            }
            
            // Enter new state
            StateHandler newHandler = handlers.get(newState);
            if (newHandler != null) {
                try {
                    newHandler.onEnterState(context);
                } catch (Exception e) {
                    Log.error("GameStateManager", "Error entering state: " + newState.getDisplayName(), e);
                }
            } else {
                Log.warn("GameStateManager", "No handler registered for state: " + newState.getDisplayName());
            }
            
            // Notify listeners
            notifyStateChanged(oldState, newState);
            
            Log.info("GameStateManager", "State transition completed: " + newState.getDisplayName());
            
        } finally {
            isTransitioning = false;
        }
    }
    
    /**
     * Update the current state. Should be called every frame from the main game loop.
     */
    public void update(float deltaTime) {
        if (isTransitioning) {
            return; // Don't update while transitioning
        }
        
        StateHandler currentHandler = handlers.get(context.getCurrentState());
        if (currentHandler != null) {
            try {
                currentHandler.onUpdateState(context, deltaTime);
            } catch (Exception e) {
                Log.error("GameStateManager", "Error updating state: " + context.getCurrentState().getDisplayName(), e);
            }
        }
    }
    
    /**
     * Update progress and status message for the current state
     */
    public void updateProgress(float progress, String message) {
        float oldProgress = context.getProgress();
        context.updateProgress(progress, message);
        
        // Notify listeners of progress update
        if (Math.abs(progress - oldProgress) > 0.01f) { // Only notify if significant change
            notifyProgressUpdated();
        }
    }
    
    /**
     * Set state data for the current state
     */
    public void setStateData(String key, Object value) {
        context.setStateData(key, value);
    }
    
    /**
     * Get state data from the current state
     */
    public <T> T getStateData(String key, Class<T> type) {
        return context.getStateData(key, type);
    }
    
    // Notification methods
    private void notifyStateChanged(GameState oldState, GameState newState) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(oldState, newState, context);
            } catch (Exception e) {
                Log.error("GameStateManager", "Error notifying state change listener", e);
            }
        }
    }
    
    private void notifyProgressUpdated() {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateProgressUpdated(context);
            } catch (Exception e) {
                Log.error("GameStateManager", "Error notifying progress listener", e);
            }
        }
    }
    
    /**
     * Force transition to error state (emergency use)
     */
    public void transitionToError(String errorMessage) {
        context.setStateData("error_message", errorMessage);
        requestStateChange(GameState.ERROR);
    }
    
    /**
     * Get a summary of the current state for debugging
     */
    public String getStateDebugInfo() {
        return String.format("State: %s, Progress: %.1f%%, Message: %s, Handlers: %d, Listeners: %d", 
            context.getCurrentState().getDisplayName(),
            context.getProgress() * 100,
            context.getStatusMessage(),
            handlers.size(),
            listeners.size());
    }
}