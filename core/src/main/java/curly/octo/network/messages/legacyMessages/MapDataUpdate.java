package curly.octo.network.messages.legacyMessages;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import curly.octo.map.GameMap;
import curly.octo.network.NetworkMessage;

public class MapDataUpdate extends NetworkMessage implements KryoSerializable {
    public GameMap map;

    public MapDataUpdate() {
        // Default constructor required for Kryo
    }

    public MapDataUpdate(GameMap map) {
        this.map = map;
    }

    @Override
    public void write(Kryo kryo, Output output) {
        kryo.writeObjectOrNull(output, map, GameMap.class);
    }

    @Override
    public void read(Kryo kryo, Input input) {
        this.map = kryo.readObjectOrNull(input, GameMap.class);
    }

    public GameMap toVoxelMap() {
        return map;
    }
}
