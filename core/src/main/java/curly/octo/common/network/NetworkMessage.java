package curly.octo.common.network;

/**
 * Base class for all network messages.
 * Provides automatic registration and type safety for the simplified message system.
 */
public abstract class NetworkMessage {

    /**
     * Default constructor required for Kryo serialization.
     * All subclasses must provide a no-arg constructor.
     */
    public NetworkMessage() {
    }

    /**
     * Override this method to provide custom string representation for logging.
     * Default implementation shows the class name.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}
