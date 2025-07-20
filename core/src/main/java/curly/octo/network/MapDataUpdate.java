package curly.octo.network;

import curly.octo.map.VoxelMap;

/**
 * Network message for sending map data from server to clients.
 */
public class MapDataUpdate {
    public int width;
    public int height;
    public int depth;
    public byte[] voxelData;
    public long seed;

    public MapDataUpdate() {
        // Default constructor required for Kryo
    }

    public MapDataUpdate(VoxelMap map) {
        this.width = map.getWidth();
        this.height = map.getHeight();
        this.depth = map.getDepth();
        this.seed = map.getSeed();
//        this.voxelData = map.serialize();
    }

    public VoxelMap toVoxelMap() {
        VoxelMap map = new VoxelMap(width, height, depth, seed);
//        map.deserialize(voxelData);
        return map;
    }
}
