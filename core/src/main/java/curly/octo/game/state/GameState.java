package curly.octo.game.state;

/**
 * Defines all possible game states in the application.
 * This enum is designed to be extensible for future features.
 */
public enum GameState {
    
    // Connection and lobby states
    LOBBY("In Lobby", "Waiting for connection"),
    CONNECTING("Connecting", "Connecting to server..."),
    CONNECTED("Connected", "Connected to server"),
    
    // Map regeneration states - the focus of Phase 1
    MAP_REGENERATION_PREPARING("Preparing", "Server is preparing new map..."),
    MAP_REGENERATION_CLEANUP("Cleaning Up", "Cleaning up current resources..."),
    MAP_REGENERATION_DOWNLOADING("Downloading", "Downloading new map data..."),
    MAP_REGENERATION_REBUILDING("Rebuilding", "Rebuilding world from new map..."),
    MAP_REGENERATION_COMPLETE("Complete", "Map regeneration complete"),
    
    // Normal gameplay
    PLAYING("Playing", "In game"),
    
    // Error and disconnection states
    CONNECTION_LOST("Disconnected", "Connection to server lost"),
    ERROR("Error", "An error occurred");
    
    private final String displayName;
    private final String defaultDescription;
    
    GameState(String displayName, String defaultDescription) {
        this.displayName = displayName;
        this.defaultDescription = defaultDescription;
    }
    
    /**
     * Gets the user-friendly display name for this state
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the default description for this state
     */
    public String getDefaultDescription() {
        return defaultDescription;
    }
    
    /**
     * Checks if this state is part of the map regeneration process
     */
    public boolean isMapRegenerationState() {
        return this == MAP_REGENERATION_PREPARING || 
               this == MAP_REGENERATION_CLEANUP || 
               this == MAP_REGENERATION_DOWNLOADING || 
               this == MAP_REGENERATION_REBUILDING ||
               this == MAP_REGENERATION_COMPLETE;
    }
    
    /**
     * Checks if this state allows normal gameplay
     */
    public boolean isPlayableState() {
        return this == PLAYING;
    }
    
    /**
     * Checks if this state represents an error or disconnection
     */
    public boolean isErrorState() {
        return this == CONNECTION_LOST || this == ERROR;
    }
}