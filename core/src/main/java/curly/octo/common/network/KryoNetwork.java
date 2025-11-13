package curly.octo.common.network;

import curly.octo.common.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.Direction;
import curly.octo.common.map.enums.MapTileGeometryType;
import curly.octo.common.map.enums.MapTileMaterial;
import curly.octo.common.map.hints.LightHint;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.hints.SpawnPointHint;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.network.messages.legacyMessages.MapDataUpdate;
import curly.octo.common.network.messages.PlayerAssignmentUpdate;
import curly.octo.common.network.messages.PlayerDisconnectUpdate;
import curly.octo.common.GameObject;
import curly.octo.common.network.messages.MapTransferPayload;
import curly.octo.common.network.messages.PlayerObjectRosterUpdate;
import curly.octo.common.network.messages.PlayerUpdate;
import curly.octo.common.PlayerObject;
import curly.octo.common.WorldObject;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Base class for network-related functionality.
 * Contains common code for both client and server.
 */
public class KryoNetwork {
    // This is the TCP and UDP port that the server will listen on.
    public static final int TCP_PORT = Constants.NETWORK_TCP_PORT;
    public static final int UDP_PORT = Constants.NETWORK_UDP_PORT;
    private static GatewayDevice activeGateway = null;

    // This registers objects that will be sent over the network.
    public static void register(EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();

        // Configure Kryo for better performance with our data
        kryo.setRegistrationRequired(true); // Require explicit registration for better error messages
        kryo.setReferences(false); // Disable reference tracking to avoid cross-message reference errors
        kryo.setAutoReset(false);

        // CRITICAL: Disable generic type optimization to prevent corruption with complex nested generics
        // This fixes the "Cannot read field 'arguments' because 'genericType' is null" error
        // that occurs with GameMap's HashMap<Class, HashMap<Long, ArrayList<MapHint>>> field
        kryo.setOptimizedGenerics(false);

        // Register primitive arrays first
        kryo.register(byte[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(boolean[].class);
        kryo.register(long.class);
        kryo.register(Long.class);
        kryo.register(String.class);
        kryo.register(Class.class);

        // Register LibGDX math classes
        kryo.register(com.badlogic.gdx.math.Vector3.class);
        kryo.register(com.badlogic.gdx.math.Quaternion.class);

        // Register enum types
        kryo.register(MapTileFillType.class);
        kryo.register(MapTileGeometryType.class);
        kryo.register(MapTileMaterial.class);
        kryo.register(Direction.class);
        kryo.register(ArrayList.class);
        kryo.register(java.util.HashMap.class);
        kryo.register(MapHint.class);
        kryo.register(MapTile.class);
        kryo.register(MapTile[].class);
        kryo.register(MapTile[][].class);
        kryo.register(MapTile[][][].class);

        // Map Hints
        kryo.register(SpawnPointHint.class);
        kryo.register(LightHint.class);

        // Auto-register all network messages using the new registry
        NetworkMessageRegistry.registerAllMessages(kryo);

        // Register legacy non-NetworkMessage classes
        kryo.register(MapDataUpdate.class);
        kryo.register(PlayerUpdate.class);
        kryo.register(PlayerDisconnectUpdate.class);

        // Register game object hierarchy
        kryo.register(GameObject.class);
        kryo.register(WorldObject.class);
        kryo.register(PlayerObject[].class);
        kryo.register(PlayerObject.class);
        kryo.register(Color.class);

        kryo.register(PlayerObjectRosterUpdate.class);
        kryo.register(PlayerAssignmentUpdate.class);

        // Register VoxelMap class
        kryo.register(GameMap.class);

        // Register map transfer payload (combines map + game objects)
        kryo.register(MapTransferPayload.class);
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

            // Forward gameplay ports
            boolean tcpSuccess = activeGateway.addPortMapping(
                    TCP_PORT, TCP_PORT, localIP, "TCP",
                    "Curly Octo " + description + " Gameplay (TCP)");

            boolean udpSuccess = activeGateway.addPortMapping(
                    UDP_PORT, UDP_PORT, localIP, "UDP",
                    "Curly Octo " + description + " Gameplay (UDP)");

            // Forward bulk transfer ports
            boolean bulkTcpSuccess = activeGateway.addPortMapping(
                    Constants.BULK_TRANSFER_TCP_PORT, Constants.BULK_TRANSFER_TCP_PORT, localIP, "TCP",
                    "Curly Octo " + description + " Bulk Transfer (TCP)");

            boolean bulkUdpSuccess = activeGateway.addPortMapping(
                    Constants.BULK_TRANSFER_UDP_PORT, Constants.BULK_TRANSFER_UDP_PORT, localIP, "UDP",
                    "Curly Octo " + description + " Bulk Transfer (UDP)");

            if (tcpSuccess && udpSuccess && bulkTcpSuccess && bulkUdpSuccess) {
                Gdx.app.log("Network", "Successfully forwarded gameplay ports " + TCP_PORT +
                        "(TCP) and " + UDP_PORT + "(UDP)");
                Gdx.app.log("Network", "Successfully forwarded bulk transfer ports " +
                        Constants.BULK_TRANSFER_TCP_PORT + "(TCP) and " +
                        Constants.BULK_TRANSFER_UDP_PORT + "(UDP) to " + localIP);
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
                // Remove gameplay port mappings
                activeGateway.deletePortMapping(TCP_PORT, "TCP");
                activeGateway.deletePortMapping(UDP_PORT, "UDP");

                // Remove bulk transfer port mappings
                activeGateway.deletePortMapping(Constants.BULK_TRANSFER_TCP_PORT, "TCP");
                activeGateway.deletePortMapping(Constants.BULK_TRANSFER_UDP_PORT, "UDP");

                Gdx.app.log("Network", "Removed all port forwarding rules");
            } catch (Exception e) {
                Gdx.app.error("Network", "Error removing port forwarding: " + e.getMessage());
            }
        }
    }
}
