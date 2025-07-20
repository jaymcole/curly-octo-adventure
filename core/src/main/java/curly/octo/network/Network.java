package curly.octo.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import curly.octo.map.VoxelMap;
import curly.octo.map.VoxelType;

/**
 * Base class for network-related functionality.
 * Contains common code for both client and server.
 */
public class Network {
    // This is the TCP and UDP port that the server will listen on.
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;

    // This registers objects that will be sent over the network.
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        
        // Configure Kryo for better performance with our data
        kryo.setRegistrationRequired(false); // Allow unregistered classes (for arrays)
        kryo.setReferences(true);
        kryo.setAutoReset(false);

        // Register all network message classes here
        kryo.register(CubeRotationUpdate.class);
        kryo.register(MapDataUpdate.class);
        kryo.register(float[].class);

        // Register VoxelMap class for serialization
        kryo.register(VoxelMap.class);
        kryo.register(VoxelType[][][].class);
        kryo.register(VoxelType[][].class);
        kryo.register(VoxelType[].class);
        kryo.register(VoxelType.class);
    }
}
