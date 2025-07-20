package curly.octo.network;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;

/**
 * Handles server-side network operations.
 */
public class GameServer {
    private final Server server;
    private final NetworkListener networkListener;

    public GameServer() {
        server = new Server();
        networkListener = new NetworkListener();
        
        // Register all network classes
        Network.register(server);
        
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
}
