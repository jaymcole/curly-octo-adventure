package curly.octo.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.network.NetworkManager;
// All message-specific imports removed - now handled by NetworkManager

/**
 * Handles incoming network messages and connection events.
 * Extend this class and override the methods you need.
 */
public class NetworkListener implements Listener {
    private final GameServer gameServer;

    public NetworkListener(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    /**
     * Called when a connection is received from a client (server-side)
     * or when connected to a server (client-side).
     */
    @Override
    public void connected(Connection connection) {
        Log.info("Network", "Connection established: " + connection.getRemoteAddressTCP().getAddress());
        if (gameServer != null) {
            gameServer.handleClientConnected(connection);
        }
    }

    /**
     * Called when a connection is lost or disconnected.
     */
    @Override
    public void disconnected(Connection connection) {
        Log.info("Network", "Connection closed: " + connection.getID());
        if (gameServer != null) {
            gameServer.handleClientDisconnected(connection);
        }
    }

    /**
     * Called when an object is received from a remote connection.
     * Routes messages through the new NetworkManager system for simplified handling.
     */
    @Override
    public void received(Connection connection, Object object) {
        NetworkManager.routeMessage(connection, object);
    }
}
