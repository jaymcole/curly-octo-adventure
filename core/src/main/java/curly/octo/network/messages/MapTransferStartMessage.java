package curly.octo.network.messages;

/**
 * Message to indicate the start of a chunked map transfer.
 * Sent before any MapChunkMessage to prepare the client.
 */
public class MapTransferStartMessage {
    public String mapId;          // Unique identifier for this map transfer
    public int totalChunks;       // Total number of chunks to expect
    public long totalSize;        // Total size of the map in bytes
    
    // Required for Kryo serialization
    public MapTransferStartMessage() {}
    
    public MapTransferStartMessage(String mapId, int totalChunks, long totalSize) {
        this.mapId = mapId;
        this.totalChunks = totalChunks;
        this.totalSize = totalSize;
    }
    
    @Override
    public String toString() {
        return "MapTransferStartMessage{mapId='" + mapId + "', totalChunks=" + totalChunks + 
               ", totalSize=" + totalSize + " bytes}";
    }
}