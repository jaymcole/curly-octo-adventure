package curly.octo.client.clientStates.mapTransferStates;

import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.client.GameClient;

import java.util.HashMap;

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

    private static HashMap<String, Integer> clientUniqueIdToClientChunkProgressMap;
    public static void updateAllClientProgress(HashMap<String, Integer> newClientProgress) {
        // Store the new progress map
        MapTransferSharedStatics.clientUniqueIdToClientChunkProgressMap = new HashMap<>(newClientProgress);

        // Get the current client's unique ID to exclude from the UI
        String currentClientId = null;
        GameClient gameClient = StateManager.getGameClient();
        if (gameClient != null) {
            currentClientId = gameClient.getClientUniqueId();
        }

        // Update the UI with the new client progress data
        MapTransferScreen.updateAllClientProgress(
            MapTransferSharedStatics.clientUniqueIdToClientChunkProgressMap,
            currentClientId,
            totalChunks
        );
    }

    public static HashMap<String, Integer> getAllClientProgress() {
        return clientUniqueIdToClientChunkProgressMap != null
            ? new HashMap<>(clientUniqueIdToClientChunkProgressMap)
            : new HashMap<>();
    }
}
