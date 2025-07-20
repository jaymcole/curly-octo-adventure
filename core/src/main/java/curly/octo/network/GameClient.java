package curly.octo.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;

/**
 * Handles client-side network operations.
 */
public class GameClient {
    private final Client client;
    private final NetworkListener networkListener;
    private final String host;

    /**
     * Creates a new game client that will connect to the specified host.
     * @param host the server hostname or IP address to connect to
     */
    public GameClient(String host) {
        this.host = host;
        client = new Client();
        networkListener = new NetworkListener();
        
        // Register all network classes
        Network.register(client);
        
        // Add network listener
        client.addListener(networkListener);
    }

    /**
     * Connects to the server with the specified timeout.
     * @param timeout the connection timeout in milliseconds
     * @throws IOException if the connection fails
     */
    public void connect(int timeout) throws IOException {
        client.start();
        client.connect(timeout, host, Network.TCP_PORT, Network.UDP_PORT);
        Log.info("Client", "Connected to server at " + host + ":" + Network.TCP_PORT);
    }

    /**
     * Disconnects from the server and releases all resources.
     */
    public void disconnect() {
        if (client != null) {
            client.stop();
            Log.info("Client", "Disconnected from server");
        }
    }

    /**
     * Sends a TCP message to the server.
     * @param message the message to send
     */
    public void sendTCP(Object message) {
        client.sendTCP(message);
    }

    /**
     * Sends a UDP message to the server.
     * @param message the message to send
     */
    public void sendUDP(Object message) {
        client.sendUDP(message);
    }

    /**
     * @return the underlying KryoNet client instance
     */
    public Client getClient() {
        return client;
    }
}
