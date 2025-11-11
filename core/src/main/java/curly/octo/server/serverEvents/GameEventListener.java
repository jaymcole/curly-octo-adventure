package curly.octo.server.serverEvents;

import curly.octo.server.serverEvents.events.GameEvent;

@FunctionalInterface
public interface GameEventListener<T extends GameEvent> {
    void onEvent(T event);
}
