package curly.octo.client;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameObject;
import curly.octo.common.ModelAssetManager;
import curly.octo.common.NPCObject;
import curly.octo.common.PhysicsProperties;
import curly.octo.common.PlayerObject;
import curly.octo.common.WorldObject;
import curly.octo.common.character.GameCharacter;
import curly.octo.common.lights.BaseLight;

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

    private ClientGameWorld gameWorld;  // Reference to game world for physics initialization

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

    private void updateRenderQueue() {
        renderQueue.clear();
        for (GameObject object : gameObjects) {
            if (!gameObjectsToBeRemoved.contains(object) && object instanceof WorldObject) {
                WorldObject worldObject = (WorldObject) object;
                ModelInstance instance = worldObject.getModelInstance();

                // Render all objects except the local player (first-person view)
                if (instance != null && (localPlayer == null || !object.entityId.equals(localPlayer.entityId))) {
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

        if (gameObject instanceof GameCharacter) {
            GameCharacter character = (GameCharacter) gameObject;
            if (character.getModelAssetPath() != null) {
                Model model = modelAssetManager.loadModel(character.getModelAssetPath());
                if (model != null) {
                    character.setModelInstance(modelAssetManager.createModelInstance(character.getModelAssetPath(), model));
                }
            }

            if (gameWorld != null && gameWorld.getMapManager() != null && gameWorld.getMapManager().isPhysicsInitialized()) {
                if (character instanceof PlayerObject) {
                    character.initializePhysics(gameWorld.getMapManager().dynamicsWorld, PlayerObject.PLAYER_HEIGHT, 1.0f);
                } else if (character instanceof NPCObject) {
                    character.initializePhysics(gameWorld.getMapManager().dynamicsWorld, NPCObject.NPC_HEIGHT, NPCObject.NPC_WIDTH);
                }
            }
        } else if (gameObject instanceof WorldObject) {
            WorldObject worldObject = (WorldObject) gameObject;
            if (worldObject.getModelAssetPath() != null) {
                Model model = modelAssetManager.loadModel(worldObject.getModelAssetPath());
                if (model != null) {
                    worldObject.setModelInstance(modelAssetManager.createModelInstance(worldObject.getModelAssetPath(), model));
                }
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

    /**
     * Gets all game objects (defensive copy for safe iteration).
     * Used for NPC sync broadcasting.
     */
    public java.util.List<GameObject> getAllObjects() {
        return new ArrayList<>(gameObjects);
    }

    public Array<ModelInstance> getRenderQueue() {
        return renderQueue;
    }

    /**
     * Disposes and clears all lights. Used during map regeneration to remove old map lights.
     */
    public void clearAllLights() {
        Log.info("GameObjectManager", "Disposing " + gameLights.size() + " lights");

        // Dispose all lights
        for (BaseLight light : gameLights) {
            if (light != null) {
                try {
                    light.destroy(); // Remove from environment
                    // Remove from ID map
                    idToGameObjectMap.remove(light.entityId);
                } catch (Exception e) {
                    Log.error("GameObjectManager", "Error disposing light " + light.entityId + ": " + e.getMessage());
                }
            }
        }

        // Clear all light collections
        gameLights.clear();
        gameLightsToBeRemoved.clear();

        Log.info("GameObjectManager", "All lights cleared and disposed");
    }

    public void setGameWorld(ClientGameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public void dispose() {
        // Dispose all game objects
        for (GameObject object : gameObjects) {
            if (object instanceof WorldObject) {
                ((WorldObject) object).dispose();
            }
        }

        // Dispose all lights
        for (BaseLight light : gameLights) {
            if (light != null) {
                light.destroy(); // Remove from environment
            }
        }
        gameLights.clear();

        modelAssetManager.dispose();
    }
}
