package curly.octo.game.state;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Holds the current state data, progress information, and metadata for the game state machine.
 * This class is thread-safe to handle updates from both main game thread and network thread.
 */
public class StateContext {
    
    private volatile GameState currentState;
    private volatile GameState previousState;
    private final Map<String, Object> stateData;
    private volatile float progress; // 0.0 to 1.0
    private volatile String statusMessage;
    private volatile long stateStartTime;
    private volatile long lastUpdateTime;
    
    public StateContext() {
        this(GameState.LOBBY);
    }
    
    public StateContext(GameState initialState) {
        this.currentState = initialState;
        this.previousState = null;
        this.stateData = new ConcurrentHashMap<>();
        this.progress = 0.0f;
        this.statusMessage = initialState.getDefaultDescription();
        this.stateStartTime = System.currentTimeMillis();
        this.lastUpdateTime = this.stateStartTime;
    }
    
    // State management
    public GameState getCurrentState() {
        return currentState;
    }
    
    public GameState getPreviousState() {
        return previousState;
    }
    
    /**
     * Internal method for state transitions - should only be called by GameStateManager
     */
    protected void setState(GameState newState) {
        this.previousState = this.currentState;
        this.currentState = newState;
        this.stateStartTime = System.currentTimeMillis();
        this.lastUpdateTime = this.stateStartTime;
        
        // Reset progress and update status message when entering new state
        this.progress = 0.0f;
        if (this.statusMessage == null || this.statusMessage.equals(previousState.getDefaultDescription())) {
            this.statusMessage = newState.getDefaultDescription();
        }
    }
    
    // Progress and status management
    public float getProgress() {
        return progress;
    }
    
    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress)); // Clamp to 0-1 range
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String message) {
        this.statusMessage = message;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public void updateProgress(float progress, String message) {
        setProgress(progress);
        setStatusMessage(message);
    }
    
    // Timing information
    public long getStateStartTime() {
        return stateStartTime;
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public long getTimeInCurrentState() {
        return System.currentTimeMillis() - stateStartTime;
    }
    
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }
    
    // State data management - for storing arbitrary data needed by state handlers
    @SuppressWarnings("unchecked")
    public <T> T getStateData(String key, Class<T> type) {
        Object value = stateData.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    public <T> T getStateData(String key, Class<T> type, T defaultValue) {
        T value = getStateData(key, type);
        return value != null ? value : defaultValue;
    }
    
    public void setStateData(String key, Object value) {
        if (value == null) {
            stateData.remove(key);
        } else {
            stateData.put(key, value);
        }
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public boolean hasStateData(String key) {
        return stateData.containsKey(key);
    }
    
    public void clearStateData() {
        stateData.clear();
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    // Utility methods
    public boolean isInState(GameState state) {
        return this.currentState == state;
    }
    
    public boolean wasInState(GameState state) {
        return this.previousState == state;
    }
    
    public boolean isMapRegenerating() {
        return currentState.isMapRegenerationState();
    }
    
    public boolean isPlayable() {
        return currentState.isPlayableState();
    }
    
    public boolean hasError() {
        return currentState.isErrorState();
    }
    
    @Override
    public String toString() {
        return String.format("StateContext{state=%s, progress=%.2f, message='%s', timeInState=%dms}", 
            currentState, progress, statusMessage, getTimeInCurrentState());
    }
}