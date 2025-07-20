package curly.octo.network;

import curly.octo.map.VoxelMap;

/**
 * Network message for sending map data from server to clients.
 */
public class MapDataUpdate {
    public VoxelMap map;

    public MapDataUpdate() {
        // Default constructor required for Kryo
    }

    public MapDataUpdate(VoxelMap map) {
        this.map = map;
    }

    public VoxelMap toVoxelMap() {
        // Since we're now sending the entire map directly, just return it
        return map;
    }
}
