package curly.octo.network.messages.mapTransferMessages;

public class MapTransferBeginMessage {

    public String mapId;          // Unique identifier for this map transfer
    public int totalChunks;       // Total number of chunks to expect
    public long totalSize;        // Total size of the map in bytes

    public MapTransferBeginMessage() {}

    public MapTransferBeginMessage(String mapId, int totalChunks, long totalSize) {
        this.mapId = mapId;
        this.totalChunks = totalChunks;
        this.totalSize = totalSize;
    }

    @Override
    public String toString() {
        return "MapTransferBeginMessage{mapId='" + mapId + "', totalChunks=" + totalChunks +
            ", totalSize=" + totalSize + " bytes}";
    }
}
