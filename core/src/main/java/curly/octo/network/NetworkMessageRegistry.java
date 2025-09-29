package curly.octo.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.minlog.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Automatically discovers and registers NetworkMessage subclasses with Kryo.
 * Replaces the manual registration process in Network.java.
 */
public class NetworkMessageRegistry {

    private static final Set<Class<? extends NetworkMessage>> registeredMessages = new HashSet<>();

    /**
     * Register all known NetworkMessage subclasses with Kryo.
     * This method should be called during network initialization.
     */
    public static void registerAllMessages(Kryo kryo) {
        Log.info("NetworkMessageRegistry", "Starting automatic message registration");

        // For now, we'll manually register the known message types
        // In the future, this could use reflection to scan the classpath
        registerMessageTypes(kryo);

        Log.info("NetworkMessageRegistry", "Registered " + registeredMessages.size() + " network message types");
    }

    /**
     * Register the known message types with Kryo.
     * This replaces the manual registration in Network.java.
     */
    private static void registerMessageTypes(Kryo kryo) {
        // Base NetworkMessage class
        registerMessage(kryo, NetworkMessage.class);

        // Map transfer messages
        registerMessage(kryo, curly.octo.network.messages.MapTransferStartMessage.class);
        registerMessage(kryo, curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage.class);
        registerMessage(kryo, curly.octo.network.messages.MapChunkMessage.class);
        registerMessage(kryo, curly.octo.network.messages.MapTransferCompleteMessage.class);

        // Map and game messages
        registerMessage(kryo, curly.octo.network.messages.MapDataUpdate.class);
        registerMessage(kryo, curly.octo.network.messages.MapRegenerationStartMessage.class);
        registerMessage(kryo, curly.octo.network.messages.ClientReadyForMapMessage.class);

        // Player messages (now NetworkMessage types)
        registerMessage(kryo, curly.octo.network.messages.PlayerAssignmentUpdate.class);
        registerMessage(kryo, curly.octo.network.messages.PlayerObjectRosterUpdate.class);
        registerMessage(kryo, curly.octo.network.messages.PlayerDisconnectUpdate.class);
        registerMessage(kryo, curly.octo.network.messages.PlayerResetMessage.class);

        // Legacy player messages (not NetworkMessage types yet)
        registerMessage(kryo, curly.octo.network.messages.PlayerUpdate.class);
    }

    /**
     * Register a single message type with Kryo and track it.
     */
    private static void registerMessage(Kryo kryo, Class<?> messageClass) {
        try {
            kryo.register(messageClass);
            if (NetworkMessage.class.isAssignableFrom(messageClass)) {
                registeredMessages.add((Class<? extends NetworkMessage>) messageClass);
            }
            Log.info("NetworkMessageRegistry", "Registered: " + messageClass.getSimpleName());
        } catch (Exception e) {
            Log.error("NetworkMessageRegistry", "Failed to register " + messageClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Get all registered message types.
     */
    public static Set<Class<? extends NetworkMessage>> getRegisteredMessages() {
        return new HashSet<>(registeredMessages);
    }

    /**
     * Check if a message type is registered.
     */
    public static boolean isRegistered(Class<? extends NetworkMessage> messageType) {
        return registeredMessages.contains(messageType);
    }

    /**
     * Get the count of registered message types.
     */
    public static int getRegisteredCount() {
        return registeredMessages.size();
    }
}