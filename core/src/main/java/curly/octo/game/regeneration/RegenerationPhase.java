package curly.octo.game.regeneration;

/**
 * Simple linear phases of map regeneration.
 * Replaces the complex state machine with clear progression.
 */
public enum RegenerationPhase {
    
    CLEANUP("Cleaning Up", "Removing old map resources..."),
    DOWNLOADING("Downloading", "Receiving new map data..."),
    REBUILDING("Rebuilding", "Creating new game world..."),
    COMPLETE("Complete", "Regeneration finished successfully");
    
    private final String displayName;
    private final String defaultMessage;
    
    RegenerationPhase(String displayName, String defaultMessage) {
        this.displayName = displayName;
        this.defaultMessage = defaultMessage;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDefaultMessage() {
        return defaultMessage;
    }
    
    /**
     * Get the next phase in the sequence, or null if this is the final phase
     */
    public RegenerationPhase getNext() {
        RegenerationPhase[] phases = values();
        int currentIndex = ordinal();
        return currentIndex < phases.length - 1 ? phases[currentIndex + 1] : null;
    }
    
    /**
     * Check if this is the final phase
     */
    public boolean isComplete() {
        return this == COMPLETE;
    }
}