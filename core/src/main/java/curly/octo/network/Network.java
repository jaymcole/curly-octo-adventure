package curly.octo.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;

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
        
        // Register all network message classes here
        kryo.register(CubeRotationUpdate.class);
        kryo.register(float[].class);
    }
}
