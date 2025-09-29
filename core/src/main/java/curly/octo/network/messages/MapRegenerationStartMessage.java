package curly.octo.network.messages;

import curly.octo.network.NetworkMessage;

/**
 * Sent by the server to notify clients that a new map is being generated
 * and they should prepare for resource cleanup and new map transfer.
 */
public class MapRegenerationStartMessage extends NetworkMessage {
    
    /** Unique ID for this regeneration request (for client synchronization) */
    public long regenerationId;
    
    /** The seed used for the new map generation */
    public long newMapSeed;
    
    /** Optional reason for map regeneration (for debugging/logging) */
    public String reason;
    
    /** Server timestamp when regeneration started */
    public long timestamp;

    /** Flag indicating if this is initial map generation (host startup) vs regeneration */
    public boolean isInitialGeneration = false;

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
        this.isInitialGeneration = false;
    }

    public MapRegenerationStartMessage(long newMapSeed, String reason, boolean isInitialGeneration) {
        this.regenerationId = System.currentTimeMillis(); // Use timestamp as unique ID
        this.newMapSeed = newMapSeed;
        this.reason = reason != null ? reason : "Server triggered";
        this.timestamp = this.regenerationId;
        this.isInitialGeneration = isInitialGeneration;
    }

    public MapRegenerationStartMessage(long regenerationId, long newMapSeed, String reason, boolean isInitialGeneration) {
        this.regenerationId = regenerationId;
        this.newMapSeed = newMapSeed;
        this.reason = reason != null ? reason : "Server triggered";
        this.timestamp = regenerationId;
        this.isInitialGeneration = isInitialGeneration;
    }
}