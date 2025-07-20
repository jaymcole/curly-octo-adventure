package curly.octo.network;

import curly.octo.map.VoxelMap;

/**
 * Listener for map data received from the server.
 */
@FunctionalInterface
public interface MapReceivedListener {
    /**
     * Called when map data is received from the server.
     * @param map The deserialized VoxelMap received from the server
     */
    void onMapReceived(VoxelMap map);
}
