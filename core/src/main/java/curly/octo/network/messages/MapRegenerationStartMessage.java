package curly.octo.network.messages;

/**
 * Sent by the server to notify clients that a new map is being generated
 * and they should prepare for resource cleanup and new map transfer.
 */
public class MapRegenerationStartMessage {
    
    /** Unique ID for this regeneration request (for client synchronization) */
    public long regenerationId;
    
    /** The seed used for the new map generation */
    public long newMapSeed;
    
    /** Optional reason for map regeneration (for debugging/logging) */
    public String reason;
    
    /** Server timestamp when regeneration started */
    public long timestamp;
    
    // No-arg constructor for Kryo serialization
    public MapRegenerationStartMessage() {
    }
    
    public MapRegenerationStartMessage(long newMapSeed, String reason) {
        this.regenerationId = System.currentTimeMillis(); // Use timestamp as unique ID
        this.newMapSeed = newMapSeed;
        this.reason = reason != null ? reason : "Server triggered";
        this.timestamp = this.regenerationId;
    }
    
    public MapRegenerationStartMessage(long regenerationId, long newMapSeed, String reason) {
        this.regenerationId = regenerationId;
        this.newMapSeed = newMapSeed;
        this.reason = reason != null ? reason : "Server triggered";
        this.timestamp = regenerationId;
    }
}