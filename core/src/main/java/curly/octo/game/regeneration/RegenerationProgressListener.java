package curly.octo.game.regeneration;

/**
 * Clean interface for receiving map regeneration progress updates.
 * Single source of truth for all regeneration events.
 */
public interface RegenerationProgressListener {
    
    /**
     * Called when regeneration phase changes
     */
    void onPhaseChanged(RegenerationPhase phase, String message);
    
    /**
     * Called when progress within current phase changes
     * @param progress Value from 0.0 to 1.0
     */
    void onProgressChanged(float progress);
    
    /**
     * Called when regeneration completes successfully
     */
    void onCompleted();
    
    /**
     * Called when an error occurs during regeneration
     */
    void onError(String errorMessage);
}