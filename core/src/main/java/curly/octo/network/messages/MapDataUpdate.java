package curly.octo.network.messages;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import curly.octo.map.VoxelMap;

/**
 * Network message for sending map data from server to clients.
 */
public class MapDataUpdate implements KryoSerializable {
    public VoxelMap map;

    public MapDataUpdate() {
        // Default constructor required for Kryo
    }

    public MapDataUpdate(VoxelMap map) {
        this.map = map;
    }

    @Override
    public void write(Kryo kryo, Output output) {
        kryo.writeObjectOrNull(output, map, VoxelMap.class);
    }

    @Override
    public void read(Kryo kryo, Input input) {
        this.map = kryo.readObjectOrNull(input, VoxelMap.class);
    }

    public VoxelMap toVoxelMap() {
        return map;
    }
}
