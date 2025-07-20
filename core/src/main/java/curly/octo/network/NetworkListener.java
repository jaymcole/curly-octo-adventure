package curly.octo.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;

/**
 * Handles incoming network messages and connection events.
 * Extend this class and override the methods you need.
 */
public class NetworkListener implements Listener {
    /**
     * Called when a connection is received from a client (server-side)
     * or when connected to a server (client-side).
     */
    @Override
    public void connected(Connection connection) {
        Log.info("Network", "Connection established: " + connection.getRemoteAddressTCP().getAddress());
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
        // Handle received objects here
        // Example:
        // if (object instanceof SomeMessage) {
        //     handleSomeMessage((SomeMessage) object);
        // }
    }

//    /**
//     * Called when an error occurs.
//     */
//    @Override
//    public void onError(Connection connection, Throwable throwable) {
//        Log.error("Network", "Error: " + throwable.getMessage(), throwable);
//    }
}
