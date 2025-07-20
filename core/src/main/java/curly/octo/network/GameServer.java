package curly.octo.network;

import com.badlogic.gdx.math.Quaternion;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;

/**
 * Handles server-side network operations.
 */
public class GameServer {
    private final Server server;
    private final NetworkListener networkListener;
    private CubeRotationListener rotationListener;

    public GameServer() {
        server = new Server();
        networkListener = new NetworkListener(server);

        // Register all network classes
        Network.register(server);

        // Set up network listener
        networkListener.setRotationListener(rotation -> {
            if (this.rotationListener != null) {
                this.rotationListener.onCubeRotationUpdate(rotation);
            }
        });

        // Add network listener
        server.addListener(networkListener);
    }

    /**
     * Starts the server and binds it to the specified ports.
     * @throws IOException if the server fails to start or bind to the ports
     */
    public void start() throws IOException {
        server.start();
        server.bind(Network.TCP_PORT, Network.UDP_PORT);
        Log.info("Server", "Server started on TCP port " + Network.TCP_PORT + " and UDP port " + Network.UDP_PORT);
    }

    /**
     * Stops the server and releases all resources.
     */
    public void stop() {
        if (server != null) {
            server.stop();
            Log.info("Server", "Server stopped");
        }
    }

    /**
     * @return the underlying KryoNet server instance
     */
    public Server getServer() {
        return server;
    }

    /**
     * Broadcasts a cube rotation update to all connected clients
     * @param rotation the rotation to broadcast
     */
    public void broadcastCubeRotation(Quaternion rotation) {
        if (server != null) {
            CubeRotationUpdate update = new CubeRotationUpdate(rotation);
            Log.info("Server", "Broadcasting rotation update: " +
                String.format("x=%.2f, y=%.2f, z=%.2f, w=%.2f",
                    update.x, update.y, update.z, update.w));
            server.sendToAllTCP(update);
        } else {
            Log.warn("Server", "Cannot broadcast rotation - server is null");
        }
    }

    /**
     * Sets the rotation listener for receiving updates
     * @param listener the listener to notify of rotation updates
     */
    public void setRotationListener(CubeRotationListener listener) {
        this.rotationListener = listener;
    }
}
