package curly.octo.server.serverEvents.events;

public abstract class GameEvent {
    private final long timestamp = System.currentTimeMillis();

    public long getTimestamp() {
        return timestamp;
    }
}
