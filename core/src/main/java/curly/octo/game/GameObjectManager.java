package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import curly.octo.gameobjects.GameObject;
import curly.octo.gameobjects.ModelAssetManager;
import curly.octo.gameobjects.PhysicsProperties;
import curly.octo.gameobjects.PlayerObject;
import curly.octo.gameobjects.WorldObject;
import lights.BaseLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GameObjectManager implements Disposable {
    public ArrayList<PlayerObject> activePlayers = new ArrayList<>();
    public PlayerObject localPlayer;

    private final HashMap<String, GameObject> idToGameObjectMap = new HashMap<>();

    private final ArrayList<GameObject> gameObjects = new ArrayList<>();
    private final HashSet<GameObject> gameObjectsToBeRemoved = new HashSet<>();
    private final ArrayList<BaseLight> gameLights = new ArrayList<>();
    private final HashSet<BaseLight> gameLightsToBeRemoved = new HashSet<>();

    private final ModelAssetManager modelAssetManager = new ModelAssetManager();
    private final Array<ModelInstance> renderQueue = new Array<>();

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
        updateRenderQueue();
        removeObjectsAfterUpdate();
    }

    private void sendObjectUpdates() {

    }

    private void updateRenderQueue() {
        renderQueue.clear();
        for (GameObject object : gameObjects) {
            if (!gameObjectsToBeRemoved.contains(object) && object instanceof WorldObject) {
                WorldObject worldObject = (WorldObject) object;
                ModelInstance instance = worldObject.getModelInstance();
                if (instance != null && !object.entityId.equals(localPlayer.entityId)) {
                    renderQueue.add(instance);
                }
            }
        }
    }

    private void removeObjectsAfterUpdate() {
        for(GameObject object : gameObjectsToBeRemoved) {
            if (object instanceof WorldObject) {
                WorldObject worldObject = (WorldObject) object;
                if (worldObject.getModelAssetPath() != null) {
                    modelAssetManager.releaseModelInstance(worldObject.getModelAssetPath());
                }
                worldObject.dispose();
            }
            gameObjects.remove(object);
            idToGameObjectMap.remove(object.entityId);
        }
        gameObjectsToBeRemoved.clear();

        for(BaseLight light : gameLightsToBeRemoved) {
            gameLights.remove(light);
            idToGameObjectMap.remove(light.entityId);
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

        if (gameObject instanceof WorldObject) {
            WorldObject worldObject = (WorldObject) gameObject;

            // Special handling for PlayerObjects
            if (gameObject instanceof PlayerObject) {
                PlayerObject playerObject = (PlayerObject) gameObject;
                // Initialize graphics with model asset manager for proper bounds calculation
                if (!playerObject.isGraphicsInitialized()) {
                    playerObject.initializeGraphicsWithManager(modelAssetManager);
                }
            }

            if (worldObject.getModelAssetPath() != null && worldObject.getBasePhysicsProperties() == PhysicsProperties.DEFAULT) {
                PhysicsProperties props = modelAssetManager.getPhysicsProperties(worldObject.getModelAssetPath());
                worldObject.setBasePhysicsProperties(props);
            }
        }
    }
    public void remove(GameObject gameObject) {
        gameObjectsToBeRemoved.add(gameObject);
    }

    public GameObject getObjectById(String id) {
        return idToGameObjectMap.getOrDefault(id, null);
    }

    public Array<ModelInstance> getRenderQueue() {
        return renderQueue;
    }

    @Override
    public void dispose() {
        for (GameObject object : gameObjects) {
            if (object instanceof WorldObject) {
                ((WorldObject) object).dispose();
            }
        }
        modelAssetManager.dispose();
    }
}
