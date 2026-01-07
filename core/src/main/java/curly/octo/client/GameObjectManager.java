package curly.octo.client;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameObject;
import curly.octo.common.ModelAssetManager;
import curly.octo.common.PhysicsProperties;
import curly.octo.common.WorldObject;
import curly.octo.common.character.GameCharacter;
import curly.octo.common.character.NPCBrain;
import curly.octo.common.character.WalkingCharacter;
import curly.octo.common.lights.BaseLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GameObjectManager implements Disposable {
    public ArrayList<WalkingCharacter> activePlayers = new ArrayList<>();
    public WalkingCharacter localPlayer;

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

            if (object instanceof WalkingCharacter) {
                activePlayers.remove(object);
            }
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
        Log.info("GameObjectManager", "[GOM_DEBUG] Adding object: " + gameObject.getClass().getSimpleName() + " ID: " + gameObject.entityId);

        if (idToGameObjectMap.containsKey(gameObject.entityId)) {
            Log.warn("GameObjectManager", "[GOM_DEBUG] DUPLICATE OBJECT ID DETECTED: " + gameObject.entityId);
            // Optional: return or handle duplicate
        }

        gameObjects.add(gameObject);
        addToStringToObjectMap(gameObject);

        if (gameObject instanceof WalkingCharacter) {
            WalkingCharacter character = (WalkingCharacter) gameObject;
            Log.info("GameObjectManager", "[GOM_DEBUG] Object is WalkingCharacter. Brain: " + (character.getBrain() != null ? character.getBrain().getClass().getSimpleName() : "null"));

            // Re-initialize transient brain on the client after deserialization
            if (character.getBrain() == null) {
                if (character.entityId != null && character.entityId.startsWith("npc_")) {
                    Log.info("GameObjectManager", "[DEBUG_NPC] Re-initializing NPCBrain for deserialized character " + character.entityId);
                    character.setBrain(new NPCBrain());
                } else {
                    // It's a player! Add to activePlayers
                    activePlayers.add(character);
                    Log.info("GameObjectManager", "[GOM_DEBUG] Added player to activePlayers: " + character.entityId);
                }
            } else if (character.getBrain() instanceof NPCBrain) {
                 // It's an NPC (brain already set, maybe locally created)
            } else {
                 // It's a player with a brain (maybe local player)
                 if (!activePlayers.contains(character)) {
                     activePlayers.add(character);
                     Log.info("GameObjectManager", "[GOM_DEBUG] Added player to activePlayers: " + character.entityId);
                 }
            }

            if (character.getModelAssetPath() != null) {
                Model model = modelAssetManager.loadModel(character.getModelAssetPath());
                if (model != null) {
                    character.setModelInstance(modelAssetManager.createModelInstance(character.getModelAssetPath(), model));
                }
            }

            if (gameWorld != null && gameWorld.getMapManager() != null && gameWorld.getMapManager().isPhysicsInitialized()) {
                Log.info("GameObjectManager", "Auto-initializing physics for character: " + character.entityId + " at position: " + character.getPosition());
                character.initializePhysics(gameWorld.getMapManager().dynamicsWorld, character.getCharacterHeight(), character.getCharacterWidth());
            } else {
                Log.info("GameObjectManager", "Skipping auto-physics init for " + character.entityId + " - gameWorld: " + (gameWorld != null) + ", mapManager: " + (gameWorld != null ? gameWorld.getMapManager() != null : "N/A") + ", physicsInitialized: " + (gameWorld != null && gameWorld.getMapManager() != null ? gameWorld.getMapManager().isPhysicsInitialized() : "N/A"));
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

        Log.info("GameObjectManager", "All lights cleared");
    }

    public void clearAllObjects() {
        Log.info("GameObjectManager", "Clearing all objects...");
        for (GameObject obj : gameObjects) {
            if (obj instanceof WorldObject) {
                ((WorldObject) obj).dispose();
            }
        }
        gameObjects.clear();
        idToGameObjectMap.clear();
        activePlayers.clear();
        localPlayer = null;
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
