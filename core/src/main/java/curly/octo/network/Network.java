package curly.octo.network;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import curly.octo.map.VoxelMap;
import curly.octo.map.VoxelType;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.player.PlayerController;

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
        kryo.setRegistrationRequired(true); // Require explicit registration for better error messages
        kryo.setReferences(true);
        kryo.setAutoReset(false);

        // Register primitive arrays first
        kryo.register(byte[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(boolean[].class);
        kryo.register(long.class);

        // Register LibGDX math classes
        kryo.register(com.badlogic.gdx.math.Vector3.class);
        kryo.register(com.badlogic.gdx.math.Quaternion.class);

        // Register enum types
        kryo.register(VoxelType.class);
        kryo.register(VoxelType[].class);
        kryo.register(VoxelType[][].class);
        kryo.register(VoxelType[][][].class);

        // Register network message classes
        kryo.register(MapDataUpdate.class);

        // Register Player related classes
        kryo.register(PlayerController[].class);
        kryo.register(PlayerController.class);
        kryo.register(Color.class);

        // Register VoxelMap class
        kryo.register(VoxelMap.class);
    }
}
