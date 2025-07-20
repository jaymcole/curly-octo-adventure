package curly.octo.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;

/**
 * Handles incoming network messages and connection events.
 * Extend this class and override the methods you need.
 */
public class NetworkListener implements Listener {
    private CubeRotationListener rotationListener;
    private ConnectionListener connectionListener;
    private MapReceivedListener mapReceivedListener;
    private Server server;
    private Client client;

    public NetworkListener() {
    }

    public NetworkListener(Server server) {
        this.server = server;
    }

    public NetworkListener(Client client) {
        this.client = client;
    }

    public void setRotationListener(CubeRotationListener listener) {
        this.rotationListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setMapReceivedListener(MapReceivedListener listener) {
        this.mapReceivedListener = listener;
    }
    /**
     * Called when a connection is received from a client (server-side)
     * or when connected to a server (client-side).
     */
    @Override
    public void connected(Connection connection) {
        Log.info("Network", "Connection established: " + connection.getRemoteAddressTCP().getAddress());
        if (connectionListener != null) {
            connectionListener.connected(connection);
        }
    }

    /**
     * Called when a connection is lost or disconnected.
     */
    @Override
    public void disconnected(Connection connection) {
        Log.info("Network", "Connection closed: " + connection.getRemoteAddressTCP().getAddress());
    }

    /**
     * Called when an object is received from a remote connection.
     * Override this method to handle specific message types.
     */
    @Override
    public void received(Connection connection, Object object) {
        Log.info("Network", "Received object of type: " + (object != null ? object.getClass().getSimpleName() : "null"));
        if (object instanceof CubeRotationUpdate) {
            Log.info("Network", "Handling CubeRotationUpdate from " + connection.getRemoteAddressTCP().getAddress());
            handleCubeRotationUpdate((CubeRotationUpdate) object, connection);
        }
    }

    private void handleCubeRotationUpdate(CubeRotationUpdate update, Connection sender) {
        Log.info("Network", "Handling rotation update. Server: " + (server != null) + ", Listener: " + (rotationListener != null));

        // If we're a server, forward the update to all clients except the sender
        if (server != null) {
            Log.info("Network", "Forwarding update to all clients except " + sender.getID());
            server.sendToAllExceptTCP(sender.getID(), update);
        }

        // If we're a client or server, notify the local listener
        if (rotationListener != null) {
            Log.info("Network", "Notifying rotation listener of update");
            rotationListener.onCubeRotationUpdate(update.getRotation());
        } else {
            Log.warn("Network", "No rotation listener set to handle update");
        }
    }

//    /**
//     * Called when an error occurs.
//     */
//    @Override
//    public void onError(Connection connection, Throwable throwable) {
//        Log.error("Network", "Error: " + throwable.getMessage(), throwable);
//    }
}
