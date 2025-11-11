package curly.octo.server.serverEvents;

import curly.octo.server.serverEvents.events.GameEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBus {
    private static final Map<Class<? extends GameEvent>, List<GameEventListener<?>>> listeners = new HashMap<>();

    public static <T extends GameEvent> void register(Class<T> eventType, GameEventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public static <T extends GameEvent> void fire(T event) {
        List<GameEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (GameEventListener<?> listener : eventListeners) {
                ((GameEventListener<T>) listener).onEvent(event);
            }
        }
    }}
