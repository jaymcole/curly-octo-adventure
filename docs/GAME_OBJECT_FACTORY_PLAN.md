# Game Object Factory - Implementation Plan

## Problem Statement

Character instantiation suffers from race conditions and positioning issues:
1. Objects created before physics engine/terrain mesh is ready
2. Remote players spawn at (0,0,0) instead of their actual position
3. No centralized control - object creation scattered across 6+ locations
4. Map transfer timing issues cause inconsistent state between clients

## Current Architecture Issues

### Object Creation Locations (Fragmented)
| Location | Object Type | Problem |
|----------|-------------|---------|
| `PlayerUtilities.createPlayerObject()` | Local player | No position context |
| `ClientGameMode.java:394-406` | Remote player | Created on-the-fly, position set AFTER physics |
| `NPCSpawnerAgent.java:104-106` | NPC | Direct instantiation |
| `ClientGameWorld.java:211` | Fallback local player | Emergency creation |
| `GameServer.java:127-128` | Server player | No deferred initialization |

### Identified Race Conditions
1. **Auto-physics fires too early** - `GameObjectManager.add()` lines 147-152 initialize physics when `isPhysicsInitialized()` is true, but this only checks if dynamics world exists, NOT if terrain mesh is built
2. **Position set after physics** - Remote players created at (0,0,0), then position applied
3. **NPC election before NPC exists** - `NPCElectionMessage` arrives before client deserializes NPC
4. **Map transfer timing** - Objects may arrive while physics is being rebuilt

## Current State: Factory Already Implemented

> **Note:** The factory pattern classes already exist but are NOT fully integrated into the codebase. Legacy creation paths still bypass the factory.

### Existing Factory Classes

```
core/src/main/java/curly/octo/common/factory/
  - ObjectCreationRequest.java      ✓ EXISTS
  - ObjectLifecycleState.java       ✓ EXISTS (includes FAILED state)
  - ManagedObjectEntry.java         ✓ EXISTS
  - GameObjectFactoryListener.java  ✓ EXISTS
  - GameObjectFactory.java          ✓ EXISTS

core/src/main/java/curly/octo/client/factory/
  - ClientGameObjectFactory.java    ✓ EXISTS

core/src/main/java/curly/octo/server/factory/
  - ServerGameObjectFactory.java    ✓ EXISTS
```

### What's Working
- Position-before-physics pattern correctly implemented (`ClientGameObjectFactory.java:104-112`)
- Duplicate request detection in factory (`GameObjectFactory.java:29-46`)
- Cancellation mechanism via `onObjectRemoved()` (`GameObjectFactory.java:100-112`)
- FAILED lifecycle state exists in `ObjectLifecycleState.java`
- Dependency tracking system functional

---

## Architecture

```
                    GameObjectFactory (Abstract)
                    - Deferred creation queue
                    - Dependency tracking
                    - Lifecycle state management
                           |
         +-----------------+-----------------+
         |                                   |
  ClientGameObjectFactory           ServerGameObjectFactory
  - Physics initialization          - No physics overhead
  - Model loading                   - Position tracking only
  - Full lifecycle                  - Election coordination
```

### Object Lifecycle States

```
PENDING -> AWAITING_DEPENDENCIES -> INITIALIZING -> READY -> ACTIVE
                                        |                      |
                                   (position set BEFORE   (on failure)
                                    physics body created)      |
                                                            FAILED
```

### Dependencies Per Object Type

| Type | Required Dependencies |
|------|----------------------|
| LOCAL_PLAYER | PHYSICS_WORLD, TERRAIN_GEOMETRY, MAP_LOADED, PLAYER_ASSIGNED, ASSET_LOADED |
| REMOTE_PLAYER | PHYSICS_WORLD, MAP_LOADED, ASSET_LOADED |
| SERVER_PLAYER | MAP_LOADED |
| NPC | PHYSICS_WORLD, TERRAIN_GEOMETRY, MAP_LOADED, ASSET_LOADED |

---

## Validated Issues & Required Fixes

### 1. Thread Safety Issue (HIGH PRIORITY)

**Problem:** `GameObjectFactory.listeners` uses `ArrayList` which is NOT thread-safe for concurrent modification.

**Location:** `GameObjectFactory.java`

**Fix:**
```java
// Change from:
protected final List<GameObjectFactoryListener> listeners = new ArrayList<>();

// To:
protected final List<GameObjectFactoryListener> listeners = new CopyOnWriteArrayList<>();
```

### 2. Terrain Geometry Check Unreliable (HIGH PRIORITY)

**Problem:** Using `map.totalTriangleCount > 0` is unreliable. Triangle count can be non-zero but physics body creation can still fail.

**Location:** `ClientGameObjectFactory.java:309`

**Fix:** Add encapsulated method to `GameMap`:
```java
// In GameMap.java
public boolean hasTerrainCollisionMesh() {
    return physicsInitialized && terrainBody != null && totalTriangleCount > 0;
}
```

Update `ClientGameObjectFactory.setCurrentMap()`:
```java
if (map.hasTerrainCollisionMesh()) {
    notifyDependencyReady(Dependency.TERRAIN_GEOMETRY);
}
```

### 3. Public Field Exposure (MEDIUM PRIORITY)

**Problem:** `GameMap.dynamicsWorld` is public, allowing bypass of initialization checks.

**Location:** `GameMap.java:48`

**Files accessing directly:**
- `MapTransferBuildAssetsState.java:92`
- `WalkingCharacter.java:134`
- `GameObjectManager.java:147`

**Fix:** Make private with controlled getter:
```java
// In GameMap.java
private transient btDiscreteDynamicsWorld dynamicsWorld;

public btDiscreteDynamicsWorld getDynamicsWorld() {
    if (!physicsInitialized) {
        throw new IllegalStateException("Physics not initialized");
    }
    return dynamicsWorld;
}
```

### 4. Duplicate Detection Without Prevention (MEDIUM PRIORITY)

**Problem:** `GameObjectManager.add()` detects duplicates but allows them anyway.

**Location:** `GameObjectManager.java:105-114`

**Fix:** Reject duplicates instead of logging:
```java
if (idToGameObjectMap.containsKey(gameObject.entityId)) {
    Log.warn("GameObjectManager", "Rejecting duplicate object ID: " + gameObject.entityId);
    return false; // or throw IllegalArgumentException
}
```

### 5. String Prefix Detection Fragility (LOW PRIORITY)

**Problem:** Entity type determined by `"npc_"` prefix is fragile.

**Locations:**
- `GameObjectManager.java:122`
- `MapTransferBuildAssetsState.java:138`

**Current Pattern:**
```java
if (character.entityId != null && character.entityId.startsWith("npc_"))
```

**Recommendation:** Consider adding explicit `EntityType` field to `WalkingCharacter` for more robust type detection. For now, document the convention clearly.

### 6. Missing Batch Notification (LOW PRIORITY)

**Problem:** `resetDependencies()` affects many objects but processes queue per-object.

**Recommendation:** Add batch operations:
```java
public void beginBatchUpdate();
public void endBatchUpdate();  // Single queue processing at end
```

---

## Integration Steps (Remaining Work)

### Phase 1: Fix Thread Safety & Encapsulation

1. **GameObjectFactory.java** - Change `ArrayList` to `CopyOnWriteArrayList` for listeners

2. **GameMap.java** - Add encapsulation:
   - Add `hasTerrainCollisionMesh()` method
   - Make `dynamicsWorld` private with getter
   - Update all direct access points

3. **GameObjectManager.java** - Reject duplicates instead of allowing

### Phase 2: Complete Integration

4. **ClientGameWorld.java**
   - Add `ClientGameObjectFactory objectFactory` field
   - `setMap()` calls `objectFactory.setCurrentMap(map)`
   - `cleanupForMapRegeneration()` calls `objectFactory.resetDependencies()`
   - Register factory as removal listener on `GameObjectManager`

5. **ClientGameMode.java** - Replace PlayerUpdate handler
   - Lines 394-406: Use `objectFactory.createRemotePlayer(id, position, yaw)` instead of `new WalkingCharacter()`
   - Factory ensures position is set before physics body

6. **GameServer.java** - Replace direct player creation
   - Lines 127-128: Use `serverObjectFactory.createPlayerForConnection()`
   - Add listener for `onObjectActivated` to trigger player assignment

7. **NPCSpawnerAgent.java** - Use factory for NPC creation
   - Replace direct `new WalkingCharacter()` with `serverObjectFactory.createNPC()`

8. **MapTransferBuildAssetsState.java** - Use factory for deserialized objects
   - Route all received `WalkingCharacter` objects through factory

### Phase 3: Cleanup

9. **PlayerUtilities.java** - Remove entirely
   - Delete file once all references are migrated to Factory

10. **GameObjectManager.java**
    - Remove auto-physics initialization (lines 147-152)
    - Factory now handles all physics initialization

---

## Critical Files to Modify

| File | Changes |
|------|---------|
| `GameObjectFactory.java` | Fix thread safety (ArrayList → CopyOnWriteArrayList) |
| `GameMap.java` | Add `hasTerrainCollisionMesh()`, encapsulate `dynamicsWorld` |
| `GameObjectManager.java` | Remove auto-physics; Reject duplicates; Add removal notification |
| `ClientGameMode.java:394-406` | Replace on-the-fly remote player creation |
| `ClientGameWorld.java` | Add factory field, integrate with map loading |
| `GameServer.java:127-128` | Use server factory for player creation |
| `NPCSpawnerAgent.java` | Use server factory for NPC creation |
| `MapTransferBuildAssetsState.java` | Route objects through factory |

---

## Key Design Decisions

1. **Position before physics** - `ObjectCreationRequest` captures spawn position; factory sets it BEFORE `initializePhysics()` call ✓ IMPLEMENTED

2. **Terrain geometry check** - Use `map.hasTerrainCollisionMesh()` (encapsulated) instead of direct `totalTriangleCount` access

3. **Thread safety** - Use `ConcurrentHashMap` for maps and `CopyOnWriteArrayList` for listeners

4. **Stale request timeout** - 30 second timeout for requests waiting on dependencies (needs implementation details)

5. **Map regeneration support** - `resetDependencies()` moves active objects back to AWAITING_DEPENDENCIES

6. **Memory Leak Prevention** - Factory listens to `GameObjectManager` removal events to clean up `activeObjects` map ✓ IMPLEMENTED

7. **Duplicate Prevention** - Factory rejects duplicate requests; Manager rejects duplicate objects

---

## Observability Recommendations

Add lifecycle logging for debugging production issues:

```java
// In GameObjectFactory.java
private void transitionState(ManagedObjectEntry entry, ObjectLifecycleState newState) {
    ObjectLifecycleState oldState = entry.state;
    entry.state = newState;
    Log.debug("GameObjectFactory",
        String.format("Object %s: %s -> %s", entry.request.entityId, oldState, newState));
}
```

---

## Verification Plan

1. **Unit Tests**
   - Test factory queues requests when dependencies missing
   - Test dependency notification processes pending queue
   - Test position is set before physics body created
   - Test object removal cleans up factory state
   - Test duplicate request rejection
   - Test thread safety with concurrent listener modification

2. **Integration Tests**
   - Client joins in-progress game - verify spawn position correct
   - Map regeneration - verify objects recreated at correct positions
   - Multiple clients join simultaneously - verify no race conditions

3. **Manual Testing**
   - Start server, wait for NPCs to spawn
   - Join with client - verify player at spawn point, not (0,0,0)
   - Join with second client during gameplay - verify remote player positions
   - Trigger map regeneration - verify all objects repositioned correctly

---

## Success Criteria

- [ ] No objects spawn at (0,0,0) incorrectly
- [ ] All object creation goes through factory
- [ ] Physics bodies created only after terrain mesh exists
- [ ] Position set before physics initialization
- [ ] Map regeneration properly resets and recreates objects
- [ ] Remote players appear at correct positions immediately
- [ ] No memory leaks when objects are removed
- [ ] Thread-safe listener notification
- [ ] Duplicate objects rejected

---

## Appendix: Detailed Class Designs

### ObjectCreationRequest.java

```java
package curly.octo.common.factory;

import com.badlogic.gdx.math.Vector3;
import java.util.EnumSet;
import java.util.UUID;

public final class ObjectCreationRequest {

    public enum ObjectType {
        LOCAL_PLAYER,
        REMOTE_PLAYER,
        SERVER_PLAYER,
        NPC
    }

    public enum Dependency {
        PHYSICS_WORLD,      // btDiscreteDynamicsWorld exists
        TERRAIN_GEOMETRY,   // Triangle mesh collision built (use hasTerrainCollisionMesh())
        MAP_LOADED,         // GameMap fully deserialized
        PLAYER_ASSIGNED,    // Server has assigned player ID
        ASSET_LOADED        // Model asset is loaded in memory
    }

    public final String requestId;
    public final String entityId;
    public final ObjectType objectType;
    public final Vector3 spawnPosition;
    public final float spawnYaw;
    public final String modelPath;
    public final float height;
    public final float width;
    public final EnumSet<Dependency> requiredDependencies;
    public final long createdTimestamp;
    public final int sourceConnectionId;

    // Builder pattern for construction
    public static class Builder {
        // ... (sets defaults per ObjectType)
    }
}
```

### GameObjectFactory.java (Abstract)

```java
package curly.octo.common.factory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class GameObjectFactory implements GameObjectRemovalListener {

    protected final Map<String, ManagedObjectEntry> pendingObjects = new ConcurrentHashMap<>();
    protected final Map<String, ManagedObjectEntry> activeObjects = new ConcurrentHashMap<>();

    // Thread-safe listener list
    protected final List<GameObjectFactoryListener> listeners = new CopyOnWriteArrayList<>();

    protected volatile boolean physicsWorldReady = false;
    protected volatile boolean terrainGeometryReady = false;
    protected volatile boolean mapLoaded = false;

    public void requestObject(ObjectCreationRequest request) {
        // Reject duplicates
        if (activeObjects.containsKey(request.entityId)) {
            Log.warn("GameObjectFactory", "Rejecting request for existing active object: " + request.entityId);
            return;
        }
        if (pendingObjects.containsKey(request.entityId)) {
            Log.info("GameObjectFactory", "Ignoring duplicate pending request: " + request.entityId);
            return;
        }
        // ... queue creation
    }

    public void notifyDependencyReady(Dependency dependency);
    public void resetDependencies();
    public void update(float delta);

    @Override
    public void onObjectRemoved(String entityId) {
        // Clean up to prevent memory leaks
        if (activeObjects.remove(entityId) != null) {
            Log.info("GameObjectFactory", "Stopped tracking removed object: " + entityId);
        }
        if (pendingObjects.remove(entityId) != null) {
            Log.info("GameObjectFactory", "Cancelled pending request for removed object: " + entityId);
        }
    }

    protected abstract WalkingCharacter createCharacterInstance(ObjectCreationRequest request);
    protected abstract void initializePhysics(ManagedObjectEntry entry);
    protected abstract void activateObject(ManagedObjectEntry entry);
}
```

### ClientGameObjectFactory.java

```java
package curly.octo.client.factory;

public class ClientGameObjectFactory extends GameObjectFactory {

    public void setCurrentMap(GameMap map) {
        this.currentMap = map;
        if (map != null && map.isPhysicsInitialized()) {
            notifyDependencyReady(Dependency.PHYSICS_WORLD);
            notifyDependencyReady(Dependency.MAP_LOADED);
            // Use encapsulated method instead of direct field access
            if (map.hasTerrainCollisionMesh()) {
                notifyDependencyReady(Dependency.TERRAIN_GEOMETRY);
            }
        }
    }

    @Override
    protected void initializePhysics(ManagedObjectEntry entry) {
        ObjectCreationRequest request = entry.request;
        WalkingCharacter character = entry.object;

        // CRITICAL: Position set BEFORE physics
        if (request.spawnPosition != null) {
            character.setInitialPosition(request.spawnPosition);
            character.setYaw(request.spawnYaw);
            Log.info("ClientGameObjectFactory",
                "Set initial position for " + request.entityId + ": " + request.spawnPosition);
        }

        // Initialize physics AFTER position is set
        switch (request.objectType) {
            case LOCAL_PLAYER:
            case NPC:
                character.initializePhysics(
                    currentMap.getDynamicsWorld(),  // Use getter, not direct field
                    request.height,
                    request.width
                );
                break;
            case REMOTE_PLAYER:
                character.initializeRemotePhysics(currentMap, request.height, request.width);
                break;
        }
    }
}
```

### GameMap.java Additions

```java
// Add to GameMap.java

/**
 * Checks if terrain collision mesh is fully ready for physics interactions.
 * More reliable than checking totalTriangleCount directly.
 */
public boolean hasTerrainCollisionMesh() {
    return physicsInitialized && terrainBody != null && totalTriangleCount > 0;
}

/**
 * Gets the dynamics world with initialization check.
 * @throws IllegalStateException if physics not initialized
 */
public btDiscreteDynamicsWorld getDynamicsWorld() {
    if (!physicsInitialized) {
        throw new IllegalStateException("Cannot access dynamicsWorld: physics not initialized");
    }
    return dynamicsWorld;
}
```
