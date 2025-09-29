package curly.octo.network.messages.legacyMessages;

import curly.octo.network.NetworkMessage;

/**
 * Message to indicate that a chunked map transfer is complete.
 * Sent after all MapChunkMessages have been transmitted.
 */
public class MapTransferCompleteMessage extends NetworkMessage {
    public String mapId;          // Unique identifier for this map transfer

    // Required for Kryo serialization
    public MapTransferCompleteMessage() {}

    public MapTransferCompleteMessage(String mapId) {
        this.mapId = mapId;
    }

    @Override
    public String toString() {
        return "MapTransferCompleteMessage{mapId='" + mapId + "'}";
    }
}
