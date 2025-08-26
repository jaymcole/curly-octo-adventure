package curly.octo.network.messages;

/**
 * Message to indicate that a chunked map transfer is complete.
 * Sent after all MapChunkMessages have been transmitted.
 */
public class MapTransferCompleteMessage {
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