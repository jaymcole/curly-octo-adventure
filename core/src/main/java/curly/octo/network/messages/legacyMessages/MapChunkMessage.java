package curly.octo.network.messages.legacyMessages;

import curly.octo.network.NetworkMessage;

/**
 * Message containing a chunk of map data for chunked map transfer.
 * Maps are broken into small chunks to avoid large network buffers.
 */
public class MapChunkMessage extends NetworkMessage {
    public String mapId;          // Unique identifier for this map transfer
    public int chunkIndex;        // Index of this chunk (0-based)
    public int totalChunks;       // Total number of chunks for this map
    public byte[] chunkData;      // The actual chunk data (max 8KB)

    // Required for Kryo serialization
    public MapChunkMessage() {}

    public MapChunkMessage(String mapId, int chunkIndex, int totalChunks, byte[] chunkData) {
        this.mapId = mapId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData;
    }

    @Override
    public String toString() {
        return "MapChunkMessage{mapId='" + mapId + "', chunk=" + chunkIndex + "/" + totalChunks +
               ", size=" + (chunkData != null ? chunkData.length : 0) + " bytes}";
    }
}
