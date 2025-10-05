package curly.octo.game.clientStates.mapTransfer;

import curly.octo.game.clientStates.StateManager;

public class MapTransferSharedStatics {

    public static void resetProgressVariables() {
        totalSize = 0;
        totalChunks = 0;
        chunksReceived = 0;
        chunks = null;
    }

    private static String mapId;
    public static String getMapId () {
        return mapId;
    }
    public static void setMapId(String id) {
        mapId = id;
    }

    private static long totalSize;
    public static long getTotalSize () {
        return totalSize;
    }
    public static void setTotalSize(long totalSize) {
        MapTransferSharedStatics.totalSize = totalSize;
    }

    private static int totalChunks;
    public static int getTotalChunks () {
        return totalChunks;
    }
    public static void setTotalChunks(int totalChunks) {
        MapTransferSharedStatics.totalChunks = totalChunks;
    }

    private static int chunksReceived;
    public static int getChunksReceived () {
        MapTransferScreen.updateMapTransferProgress(chunksReceived, totalChunks);
        return chunksReceived;
    }
    public static void setChunksReceived(int chunksReceived) {
        MapTransferSharedStatics.chunksReceived = chunksReceived;
    }

    public static byte[][] chunks;
}
