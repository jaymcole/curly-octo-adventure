package curly.octo.network.messages.legacyMessages;

import curly.octo.network.NetworkMessage;

/**
 * Message sent by clients to confirm they are ready to receive new map data.
 * This ensures proper synchronization during map regeneration.
 */
public class ClientReadyForMapMessage extends NetworkMessage {

    /** The client's ID (for server tracking) */
    public String clientId;

    /** The regeneration request ID this readiness is for */
    public long regenerationId;

    /** Timestamp when client became ready */
    public long timestamp;

    // No-arg constructor for Kryo serialization
    public ClientReadyForMapMessage() {
    }

    public ClientReadyForMapMessage(String clientId, long regenerationId) {
        this.clientId = clientId;
        this.regenerationId = regenerationId;
        this.timestamp = System.currentTimeMillis();
    }
}
