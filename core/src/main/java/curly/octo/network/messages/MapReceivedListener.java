package curly.octo.network.messages;

import curly.octo.map.GameMap;

/**
 * Listener for map data received from the server.
 */
@FunctionalInterface
public interface MapReceivedListener {
    /**
     * Called when map data is received from the server.
     * @param map The deserialized VoxelMap received from the server
     */
    void onMapReceived(GameMap map);
}
