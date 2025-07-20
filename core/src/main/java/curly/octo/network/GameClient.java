package curly.octo.network;

import com.badlogic.gdx.math.Quaternion;
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
    private CubeRotationListener rotationListener;
    private MapReceivedListener mapReceivedListener;
    private Quaternion lastRotation;

    /**
     * Creates a new game client that will connect to the specified host.
     * @param host the server hostname or IP address to connect to
     */
    public GameClient(String host) {
        this.host = host;
        client = new Client();
        
        // Register all network classes
        Network.register(client);
        
        // Create network listener with client reference
        networkListener = new NetworkListener(client);
        
        // Set up network listener
        networkListener.setRotationListener(rotation -> {
            this.lastRotation = rotation;
            if (this.rotationListener != null) {
                this.rotationListener.onCubeRotationUpdate(rotation);
            }
        });
        
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
    
    /**
     * Sends a cube rotation update to the server
     * @param rotation the new rotation to send
     */
    public void sendCubeRotation(Quaternion rotation) {
        if (client.isConnected()) {
            client.sendTCP(new CubeRotationUpdate(rotation));
        }
    }
    
    /**
     * Sets the rotation listener for receiving updates
     * @param listener the listener to notify of rotation updates
     */
    public void setRotationListener(CubeRotationListener listener) {
        this.rotationListener = listener;
    }
    
    public void setMapReceivedListener(MapReceivedListener listener) {
        this.mapReceivedListener = listener;
        this.networkListener.setMapReceivedListener(listener);
    }
    
    /**
     * @return the last received rotation, or null if none received yet
     */
    public Quaternion getLastRotation() {
        return lastRotation;
    }
}
