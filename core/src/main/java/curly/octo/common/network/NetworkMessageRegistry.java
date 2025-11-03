package curly.octo.common.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.network.messages.*;
import curly.octo.common.network.messages.legacyMessages.*;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferAllClientProgressMessage;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferBeginMessage;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferCompleteMessage;

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
        registerMessage(kryo, MapTransferStartMessage.class);
        registerMessage(kryo, MapTransferBeginMessage.class);
        registerMessage(kryo, MapTransferCompleteMessage.class);
        registerMessage(kryo, MapChunkMessage.class);
        registerMessage(kryo, MapTransferAllClientProgressMessage.class);

        // Map and game messages
        registerMessage(kryo, MapDataUpdate.class);
        registerMessage(kryo, MapRegenerationStartMessage.class);
        registerMessage(kryo, ClientReadyForMapMessage.class);

        // Player messages (now NetworkMessage types)
        registerMessage(kryo, PlayerAssignmentUpdate.class);
        registerMessage(kryo, PlayerObjectRosterUpdate.class);
        registerMessage(kryo, PlayerDisconnectUpdate.class);
        registerMessage(kryo, PlayerResetMessage.class);
        registerMessage(kryo, PlayerResetMessage.class);

        // Legacy player messages (not NetworkMessage types yet)
        registerMessage(kryo, PlayerUpdate.class);
        registerMessage(kryo, ClientStateChangeMessage.class);
        registerMessage(kryo, ClientIdentificationMessage.class);
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
