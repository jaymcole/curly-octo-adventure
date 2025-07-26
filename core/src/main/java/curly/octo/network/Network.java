package curly.octo.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;
import curly.octo.map.hints.MapHint;
import curly.octo.map.hints.SpawnPointHint;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import curly.octo.map.GameMap;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.network.messages.PlayerAssignmentUpdate;
import curly.octo.network.messages.PlayerRosterUpdate;
import curly.octo.network.messages.PlayerUpdate;
import curly.octo.player.PlayerController;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Base class for network-related functionality.
 * Contains common code for both client and server.
 */
public class Network {
    // This is the TCP and UDP port that the server will listen on.
    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;
    private static GatewayDevice activeGateway = null;

    // This registers objects that will be sent over the network.
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();

        // Configure Kryo for better performance with our data
        kryo.setRegistrationRequired(true); // Require explicit registration for better error messages
        kryo.setReferences(false); // Disable reference tracking to avoid cross-message reference errors
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
        kryo.register(MapTileFillType.class);
        kryo.register(MapTileGeometryType.class);
        kryo.register(MapTileMaterial.class);
        kryo.register(CardinalDirection.class);
        kryo.register(ArrayList.class);
        kryo.register(MapHint.class);
        kryo.register(SpawnPointHint.class);
        kryo.register(MapTile.class);
        kryo.register(MapTile[].class);
        kryo.register(MapTile[][].class);
        kryo.register(MapTile[][][].class);

        // Register network message classes
        kryo.register(MapDataUpdate.class);
        kryo.register(PlayerUpdate.class);

        // Register Player related classes
        kryo.register(PlayerController[].class);
        kryo.register(PlayerController.class);
        kryo.register(Color.class);
//        kryo.register(PlayerRosterUpdate.RosterEntry[].class);
        kryo.register(PlayerRosterUpdate.class);
        kryo.register(PlayerAssignmentUpdate.class);

        // Register VoxelMap class
        kryo.register(GameMap.class);
    }

    /**
     * Attempts to forward ports using UPnP.
     * @param description Description for the port mapping
     * @return true if port forwarding was successful, false otherwise
     */
    public static boolean setupPortForwarding(String description) {
        try {
            // Discover the gateway device
            GatewayDiscover discover = new GatewayDiscover();
            discover.discover();

            activeGateway = discover.getValidGateway();
            if (activeGateway == null) {
                Gdx.app.log("Network", "No valid gateway device found");
                return false;
            }

            // Get local IP address
            InetAddress localAddress = activeGateway.getLocalAddress();
            String localIP = localAddress.getHostAddress();

            // Forward TCP port
            boolean tcpSuccess = activeGateway.addPortMapping(
                    TCP_PORT, TCP_PORT, localIP, "TCP",
                    "Curly Octo " + description + " (TCP)");

            // Forward UDP port
            boolean udpSuccess = activeGateway.addPortMapping(
                    UDP_PORT, UDP_PORT, localIP, "UDP",
                    "Curly Octo " + description + " (UDP)");

            if (tcpSuccess && udpSuccess) {
                Gdx.app.log("Network", "Successfully forwarded ports " + TCP_PORT + "(TCP) and " +
                        UDP_PORT + "(UDP) to " + localIP);
                return true;
            } else {
                Gdx.app.error("Network", "Failed to forward one or more ports");
                return false;
            }

        } catch (Exception e) {
            Gdx.app.error("Network", "Error setting up port forwarding: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes the port forwarding rules that were previously set up.
     */
    public static void removePortForwarding() {
        if (activeGateway != null) {
            try {
                activeGateway.deletePortMapping(TCP_PORT, "TCP");
                activeGateway.deletePortMapping(UDP_PORT, "UDP");
                Gdx.app.log("Network", "Removed port forwarding rules");
            } catch (Exception e) {
                Gdx.app.error("Network", "Error removing port forwarding: " + e.getMessage());
            }
        }
    }
}
