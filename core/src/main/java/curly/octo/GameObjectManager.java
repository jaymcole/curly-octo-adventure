package curly.octo;

import curly.octo.gameobjects.GameObject;
import curly.octo.player.PlayerController;
import lights.BaseLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class GameObjectManager {
    public static PlayerController playerController;

    private static final HashMap<String, GameObject> idToGameObjectMap = new HashMap<>();

    private static final ArrayList<GameObject> gameObjects = new ArrayList<>();
    private static final HashSet<GameObject> gameObjectsToBeRemoved = new HashSet<>();
    private static final ArrayList<BaseLight> gameLights = new ArrayList<>();
    private static final HashSet<BaseLight> gameLightsToBeRemoved = new HashSet<>();

    public static void update(float delta) {
        for(GameObject objects : gameObjects) {
            if (!gameObjectsToBeRemoved.contains(objects)) {
                objects.update(delta);
            }
        }

        for(BaseLight light : gameLights) {
            if (!gameLightsToBeRemoved.contains(light)) {
                light.update(delta);
            }
        }

        if (playerController != null) {
            playerController.update(delta);
        }

        removeObjectsAfterUpdate();
    }

    private static void removeObjectsAfterUpdate() {
        for(GameObject object : gameObjectsToBeRemoved) {
            gameObjects.remove(object);
        }
        gameObjectsToBeRemoved.clear();

        for(BaseLight light : gameLightsToBeRemoved) {
            gameLights.remove(light);
        }
        gameLightsToBeRemoved.clear();
    }

    private static void addObjectToUUIDToObjectMap(GameObject object) {
        idToGameObjectMap.put(object.entityId.toString(), object);
    }

    public static void add(BaseLight light) {
        gameLights.add(light);
        addObjectToUUIDToObjectMap(light);
    }

    public static void remove(BaseLight light) {
        gameLightsToBeRemoved.add(light);
    }

    public static void add(GameObject gameObject) {
        gameObjects.add(gameObject);
        addObjectToUUIDToObjectMap(gameObject);
    }

    public static void remove(GameObject gameObject) {
        gameObjectsToBeRemoved.add(gameObject);
    }

    public static GameObject getObjectById(UUID id) {
        return getObjectById(id.toString());
    }

    public static GameObject getObjectById(String id) {
        return idToGameObjectMap.getOrDefault(id, null);
    }
}
