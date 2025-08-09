package curly.octo.game;

import curly.octo.gameobjects.GameObject;
import curly.octo.player.PlayerController;
import lights.BaseLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GameObjectManager {
    public ArrayList<PlayerController> activePlayers = new ArrayList<>();
    public PlayerController localPlayerController;

    private final HashMap<String, GameObject> idToGameObjectMap = new HashMap<>();

    private final ArrayList<GameObject> gameObjects = new ArrayList<>();
    private final HashSet<GameObject> gameObjectsToBeRemoved = new HashSet<>();
    private final ArrayList<BaseLight> gameLights = new ArrayList<>();
    private final HashSet<BaseLight> gameLightsToBeRemoved = new HashSet<>();

    public void update(float delta) {
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

        if (localPlayerController != null) {
            localPlayerController.update(delta);
        }

        removeObjectsAfterUpdate();
    }

    private void removeObjectsAfterUpdate() {
        for(GameObject object : gameObjectsToBeRemoved) {
            gameObjects.remove(object);
        }
        gameObjectsToBeRemoved.clear();

        for(BaseLight light : gameLightsToBeRemoved) {
            gameLights.remove(light);
        }
        gameLightsToBeRemoved.clear();
    }

    private void addToStringToObjectMap(GameObject object) {
        idToGameObjectMap.put(object.entityId, object);
    }

    public void add(BaseLight light) {
        gameLights.add(light);
        addToStringToObjectMap(light);
    }
    public void remove(BaseLight light) {
        gameLightsToBeRemoved.add(light);
    }

    public void add(GameObject gameObject) {
        gameObjects.add(gameObject);
        addToStringToObjectMap(gameObject);
    }
    public void remove(GameObject gameObject) {
        gameObjectsToBeRemoved.add(gameObject);
    }

    public GameObject getObjectById(String id) {
        return idToGameObjectMap.getOrDefault(id, null);
    }
}
