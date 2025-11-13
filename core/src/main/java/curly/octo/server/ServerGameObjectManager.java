package curly.octo.server;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameObject;
import curly.octo.common.PlayerObject;
import curly.octo.common.WorldObject;
import curly.octo.common.lights.BaseLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Server-side game object manager.
 * Manages all game entities (players, objects, lights) without rendering dependencies.
 * Used for authoritative state tracking, dispute arbitration, and NPC direction.
 */
public class ServerGameObjectManager {
    // Player management
    public ArrayList<PlayerObject> activePlayers = new ArrayList<>();

    // ID-based lookup for all game objects
    private final HashMap<String, GameObject> idToGameObjectMap = new HashMap<>();

    // Entity collections
    private final ArrayList<GameObject> gameObjects = new ArrayList<>();
    private final HashSet<GameObject> gameObjectsToBeRemoved = new HashSet<>();

    //TODO: Is there any reason to separate lights on the server-side manager?
    private final ArrayList<BaseLight> gameLights = new ArrayList<>();
    private final HashSet<BaseLight> gameLightsToBeRemoved = new HashSet<>();

    /**
     * Updates all game objects and lights.
     * Does NOT handle rendering - server only tracks state.
     */
    public void update(float delta) {
        // Update all game objects
        for (GameObject object : gameObjects) {
            if (!gameObjectsToBeRemoved.contains(object)) {
                object.update(delta);
            }
        }

        // Update all lights
        for (BaseLight light : gameLights) {
            if (!gameLightsToBeRemoved.contains(light)) {
                light.update(delta);
            }
        }

        // Clean up removed objects
        removeObjectsAfterUpdate();
    }

    /**
     * Removes objects that were marked for removal during the update cycle.
     */
    private void removeObjectsAfterUpdate() {
        // Remove game objects
        for (GameObject object : gameObjectsToBeRemoved) {
            if (object instanceof WorldObject) {
                WorldObject worldObject = (WorldObject) object;
                // Server doesn't have graphics, so we just dispose physics/logic
                worldObject.dispose();
            }
            gameObjects.remove(object);
            idToGameObjectMap.remove(object.entityId);

            // Remove from active players if it's a player
            if (object instanceof PlayerObject) {
                activePlayers.remove(object);
            }
        }
        gameObjectsToBeRemoved.clear();

        // Remove lights
        for (BaseLight light : gameLightsToBeRemoved) {
            gameLights.remove(light);
            idToGameObjectMap.remove(light.entityId);
        }
        gameLightsToBeRemoved.clear();
    }

    /**
     * Adds an object to the ID lookup map.
     */
    private void addToIdMap(GameObject object) {
        idToGameObjectMap.put(object.entityId, object);
    }

    // =====================================
    // ADD/REMOVE METHODS
    // =====================================

    /**
     * Adds a light to the game world (server-side tracking only).
     */
    public void add(BaseLight light) {
        gameLights.add(light);
        addToIdMap(light);
    }

    /**
     * Marks a light for removal.
     */
    public void remove(BaseLight light) {
        gameLightsToBeRemoved.add(light);
    }

    /**
     * Adds a game object (no graphics initialization - server only tracks state).
     */
    public void add(GameObject gameObject) {
        gameObjects.add(gameObject);
        addToIdMap(gameObject);

        // Track players separately for quick access
        if (gameObject instanceof PlayerObject) {
            activePlayers.add((PlayerObject) gameObject);
            Log.info("ServerGameObjectManager", "Added player: " + gameObject.entityId);
        }
    }

    /**
     * Marks a game object for removal.
     */
    public void remove(GameObject gameObject) {
        gameObjectsToBeRemoved.add(gameObject);
    }

    // =====================================
    // LOOKUP METHODS (for arbitration)
    // =====================================

    /**
     * Gets an object by its unique ID.
     * @param id The entity ID
     * @return The GameObject, or null if not found
     */
    public GameObject getObjectById(String id) {
        return idToGameObjectMap.getOrDefault(id, null);
    }

    /**
     * Gets a player by their unique ID.
     * @param id The player entity ID
     * @return The PlayerObject, or null if not found or not a player
     */
    public PlayerObject getPlayerById(String id) {
        GameObject obj = getObjectById(id);
        if (obj instanceof PlayerObject) {
            return (PlayerObject) obj;
        }
        return null;
    }

    /**
     * Gets all objects within a radius of a position (for spatial queries/arbitration).
     * @param position Center position
     * @param radius Search radius
     * @return List of objects within radius
     */
    public List<GameObject> getObjectsInRadius(Vector3 position, float radius) {
        List<GameObject> nearbyObjects = new ArrayList<>();
        float radiusSquared = radius * radius;

        for (GameObject obj : gameObjects) {
            if (!gameObjectsToBeRemoved.contains(obj)) {
                Vector3 objPos = obj.getPosition();
                float distSquared = position.dst2(objPos);
                if (distSquared <= radiusSquared) {
                    nearbyObjects.add(obj);
                }
            }
        }

        return nearbyObjects;
    }

    /**
     * Gets all players within a radius of a position.
     * @param position Center position
     * @param radius Search radius
     * @return List of players within radius
     */
    public List<PlayerObject> getPlayersInRadius(Vector3 position, float radius) {
        List<PlayerObject> nearbyPlayers = new ArrayList<>();
        float radiusSquared = radius * radius;

        for (PlayerObject player : activePlayers) {
            if (!gameObjectsToBeRemoved.contains(player)) {
                Vector3 playerPos = player.getPosition();
                float distSquared = position.dst2(playerPos);
                if (distSquared <= radiusSquared) {
                    nearbyPlayers.add(player);
                }
            }
        }

        return nearbyPlayers;
    }

    /**
     * Gets all active players (defensive copy).
     * @return List of all active players
     */
    public List<PlayerObject> getAllPlayers() {
        return new ArrayList<>(activePlayers);
    }

    /**
     * Gets all game objects (defensive copy).
     * @return List of all game objects
     */
    public List<GameObject> getAllObjects() {
        return new ArrayList<>(gameObjects);
    }

    /**
     * Gets the total count of active objects.
     * @return Number of active game objects
     */
    public int getObjectCount() {
        return gameObjects.size();
    }

    /**
     * Gets the total count of active players.
     * @return Number of active players
     */
    public int getPlayerCount() {
        return activePlayers.size();
    }

    // =====================================
    // VALIDATION METHODS (for arbitration)
    // =====================================

    /**
     * Validates if a position update from a client is reasonable.
     * Can be expanded to check for teleportation, speed hacks, etc.
     *
     * @param playerId The player making the update
     * @param newPosition The new position claimed by client
     * @param maxSpeed Maximum allowed speed (units per second)
     * @param deltaTime Time since last update
     * @return true if the update is valid, false if suspicious
     */
    public boolean validatePositionUpdate(String playerId, Vector3 newPosition, float maxSpeed, float deltaTime) {
        PlayerObject player = getPlayerById(playerId);
        if (player == null) {
            Log.warn("ServerGameObjectManager", "Cannot validate position for unknown player: " + playerId);
            return false;
        }

        Vector3 oldPosition = player.getPosition();
        float distance = oldPosition.dst(newPosition);
        float maxAllowedDistance = maxSpeed * deltaTime * 1.5f; // 50% tolerance

        if (distance > maxAllowedDistance) {
            Log.warn("ServerGameObjectManager", "Suspicious position update for player " + playerId +
                    ": moved " + distance + " units in " + deltaTime + "s (max allowed: " + maxAllowedDistance + ")");
            return false;
        }

        return true;
    }

    /**
     * Checks if an object exists and is valid (not marked for removal).
     * @param id The entity ID
     * @return true if object exists and is valid
     */
    public boolean isObjectValid(String id) {
        GameObject obj = getObjectById(id);
        return obj != null && !gameObjectsToBeRemoved.contains(obj);
    }

    // =====================================
    // NPC DIRECTION METHODS
    // =====================================

    /**
     * Gets NPCs (non-player WorldObjects) for server-side direction.
     * NPCs are directed by the server but not fully simulated.
     *
     * @return List of NPC objects
     */
    public List<WorldObject> getNPCs() {
        List<WorldObject> npcs = new ArrayList<>();
        for (GameObject obj : gameObjects) {
            if (obj instanceof WorldObject && !(obj instanceof PlayerObject)) {
                npcs.add((WorldObject) obj);
            }
        }
        return npcs;
    }

    /**
     * Finds the nearest player to a given position (for NPC targeting).
     * @param position The position to search from
     * @return The nearest player, or null if no players exist
     */
    public PlayerObject getNearestPlayer(Vector3 position) {
        PlayerObject nearest = null;
        float nearestDistSquared = Float.MAX_VALUE;

        for (PlayerObject player : activePlayers) {
            if (!gameObjectsToBeRemoved.contains(player)) {
                float distSquared = position.dst2(player.getPosition());
                if (distSquared < nearestDistSquared) {
                    nearestDistSquared = distSquared;
                    nearest = player;
                }
            }
        }

        return nearest;
    }

    // =====================================
    // LIGHT MANAGEMENT (for map regeneration)
    // =====================================

    /**
     * Clears all lights from the game world.
     * Used during map regeneration to remove old map lights.
     */
    public void clearAllLights() {
        Log.info("ServerGameObjectManager", "Clearing " + gameLights.size() + " lights");

        // Dispose all lights
        for (BaseLight light : gameLights) {
            if (light != null) {
                try {
                    light.destroy(); // Remove from environment
                    idToGameObjectMap.remove(light.entityId);
                } catch (Exception e) {
                    Log.error("ServerGameObjectManager", "Error disposing light " + light.entityId + ": " + e.getMessage());
                }
            }
        }

        // Clear all light collections
        gameLights.clear();
        gameLightsToBeRemoved.clear();

        Log.info("ServerGameObjectManager", "All lights cleared");
    }

    /**
     * Clears all game objects (for map regeneration or server reset).
     * Does NOT clear players - use removePlayer() for that.
     */
    public void clearAllObjects() {
        Log.info("ServerGameObjectManager", "Clearing " + gameObjects.size() + " game objects");

        // Dispose all non-player objects
        for (GameObject obj : new ArrayList<>(gameObjects)) {
            if (!(obj instanceof PlayerObject)) {
                if (obj instanceof WorldObject) {
                    ((WorldObject) obj).dispose();
                }
                gameObjects.remove(obj);
                idToGameObjectMap.remove(obj.entityId);
            }
        }

        gameObjectsToBeRemoved.clear();
        Log.info("ServerGameObjectManager", "All non-player objects cleared");
    }

    /**
     * Disposes all resources (for server shutdown).
     */
    public void dispose() {
        Log.info("ServerGameObjectManager", "Disposing all resources");

        // Dispose all game objects
        for (GameObject object : gameObjects) {
            if (object instanceof WorldObject) {
                ((WorldObject) object).dispose();
            }
        }

        // Dispose all lights
        for (BaseLight light : gameLights) {
            if (light != null) {
                light.destroy();
            }
        }

        // Clear all collections
        gameObjects.clear();
        activePlayers.clear();
        gameLights.clear();
        idToGameObjectMap.clear();
        gameObjectsToBeRemoved.clear();
        gameLightsToBeRemoved.clear();

        Log.info("ServerGameObjectManager", "Disposal complete");
    }
}
