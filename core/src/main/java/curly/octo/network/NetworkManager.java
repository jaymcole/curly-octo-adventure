package curly.octo.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Simplified network message system that replaces the complex listener pattern.
 * Provides type-safe message handling with minimal boilerplate.
 */
public class NetworkManager {

    // Storage for message handlers by message type
    private static final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
    // Storage for connection-aware message handlers by message type
    private static final Map<Class<?>, List<BiConsumer<Connection, Object>>> connectionHandlers = new HashMap<>();

    // Network endpoints
    private static Server server;
    private static Client client;

    /**
     * Initialize the NetworkManager with a server instance.
     */
    public static void initialize(Server server) {
        NetworkManager.server = server;
        Log.info("NetworkManager", "Initialized with server instance");
    }

    /**
     * Initialize the NetworkManager with a client instance.
     */
    public static void initialize(Client client) {
        NetworkManager.client = client;
        Log.info("NetworkManager", "Initialized with client instance");
    }


    /**
     * Register a handler for a specific message type.
     * Multiple handlers can be registered for the same message type.
     *
     * @param messageType The class of the message to handle
     * @param handler The handler function to call when this message type is received
     */
    @SuppressWarnings("unchecked")
    public static <T extends NetworkMessage> void onReceive(Class<T> messageType, Consumer<T> handler) {
        handlers.computeIfAbsent(messageType, k -> new ArrayList<>())
                .add(obj -> handler.accept((T) obj));

        Log.info("NetworkManager", "Registered handler for " + messageType.getSimpleName());
    }

    /**
     * Register a connection-aware handler for a specific message type.
     * Multiple handlers can be registered for the same message type.
     *
     * @param messageType The class of the message to handle
     * @param handler The handler function to call when this message type is received (receives connection and message)
     */
    @SuppressWarnings("unchecked")
    public static <T extends NetworkMessage> void onReceive(Class<T> messageType, BiConsumer<Connection, T> handler) {
        connectionHandlers.computeIfAbsent(messageType, k -> new ArrayList<>())
                .add((conn, obj) -> handler.accept(conn, (T) obj));

        Log.info("NetworkManager", "Registered connection-aware handler for " + messageType.getSimpleName());
    }

    /**
     * Send a message from server to a specific client.
     */
    public static void sendToClient(int connectionId, NetworkMessage message) {
        if (server == null) {
            Log.error("NetworkManager", "Cannot send to client: server not initialized");
            return;
        }

        Log.info("NetworkManager", "Sending " + message.getClass().getSimpleName() + " to client " + connectionId);
        server.sendToTCP(connectionId, message);
    }

    /**
     * Send a message from server to all connected clients.
     */
    public static void sendToAllClients(NetworkMessage message) {
        if (server == null) {
            Log.error("NetworkManager", "Cannot send to all clients: server not initialized");
            return;
        }

        Log.info("NetworkManager", "Broadcasting " + message.getClass().getSimpleName() + " to all clients");
        server.sendToAllTCP(message);
    }

    /**
     * Send a message from client to server.
     */
    public static void sendToServer(NetworkMessage message) {
        if (client == null) {
            Log.error("NetworkManager", "Cannot send to server: client not initialized");
            return;
        }

        Log.info("NetworkManager", "Sending " + message.getClass().getSimpleName() + " to server");
        client.sendTCP(message);
    }

    /**
     * Route an incoming message to its registered handlers.
     * This method is called by NetworkListener when a message is received.
     */
    @SuppressWarnings("unchecked")
    public static void routeMessage(Connection connection, Object message) {
        if (!(message instanceof NetworkMessage)) {
            // For backwards compatibility, ignore non-NetworkMessage objects
            return;
        }

        Class<?> messageType = message.getClass();
        List<Consumer<Object>> messageHandlers = handlers.get(messageType);
        List<BiConsumer<Connection, Object>> connectionMessageHandlers = connectionHandlers.get(messageType);

        int totalHandlers = (messageHandlers != null ? messageHandlers.size() : 0) +
                           (connectionMessageHandlers != null ? connectionMessageHandlers.size() : 0);

        if (totalHandlers == 0) {
            Log.warn("NetworkManager", "No handlers registered for " + messageType.getSimpleName());
            return;
        }

        Log.info("NetworkManager", "Routing " + messageType.getSimpleName() + " to " + totalHandlers + " handler(s)");

        // Call all registered message-only handlers
        if (messageHandlers != null) {
            for (Consumer<Object> handler : messageHandlers) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    Log.error("NetworkManager", "Error handling " + messageType.getSimpleName() + " (message-only): " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Call all registered connection-aware handlers
        if (connectionMessageHandlers != null) {
            for (BiConsumer<Connection, Object> handler : connectionMessageHandlers) {
                try {
                    handler.accept(connection, message);
                } catch (Exception e) {
                    Log.error("NetworkManager", "Error handling " + messageType.getSimpleName() + " (connection-aware): " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Clear all registered handlers. Useful for cleanup or testing.
     */
    public static void clearHandlers() {
        handlers.clear();
        connectionHandlers.clear();
        Log.info("NetworkManager", "Cleared all message handlers");
    }

    /**
     * Get the number of registered handlers for a message type.
     */
    public static int getHandlerCount(Class<? extends NetworkMessage> messageType) {
        List<Consumer<Object>> messageHandlers = handlers.get(messageType);
        List<BiConsumer<Connection, Object>> connectionMessageHandlers = connectionHandlers.get(messageType);

        int messageOnlyCount = messageHandlers != null ? messageHandlers.size() : 0;
        int connectionAwareCount = connectionMessageHandlers != null ? connectionMessageHandlers.size() : 0;

        return messageOnlyCount + connectionAwareCount;
    }
}
