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

## Solution: Centralized GameObjectFactory

### Architecture

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
                                        |
                                   (position set BEFORE physics body created)
```

### Dependencies Per Object Type

| Type | Required Dependencies |
|------|----------------------|
| LOCAL_PLAYER | PHYSICS_WORLD, TERRAIN_GEOMETRY, MAP_LOADED, PLAYER_ASSIGNED |
| REMOTE_PLAYER | PHYSICS_WORLD, MAP_LOADED |
| NPC | PHYSICS_WORLD, TERRAIN_GEOMETRY, MAP_LOADED |

## Implementation Steps

### Phase 1: Core Factory Classes
Create new package `curly.octo.common.factory/`:

1. **ObjectCreationRequest.java** - Immutable request with builder pattern
   - Contains: entityId, objectType, spawnPosition, spawnYaw, requiredDependencies
   - Builder sets default dependencies per object type

2. **ObjectLifecycleState.java** - Enum for lifecycle states

3. **ManagedObjectEntry.java** - Tracks object through lifecycle
   - Holds: request, state, object reference, satisfied dependencies, timestamps

4. **GameObjectFactoryListener.java** - Callback interface
   - Methods: `onObjectReady()`, `onObjectActivated()`, `onObjectCreationFailed()`

5. **GameObjectFactory.java** - Abstract base
   - `ConcurrentHashMap` for thread-safe pending/active tracking
   - `requestObject(request)` - queues creation
   - `notifyDependencyReady(dependency)` - processes pending queue
   - `resetDependencies()` - for map regeneration
   - Abstract methods: `createCharacterInstance()`, `initializePhysics()`, `activateObject()`

### Phase 2: Client Implementation
Create `curly.octo.client.factory/`:

6. **ClientGameObjectFactory.java**
   - `setCurrentMap(map)` - notifies dependencies when map ready
   - Checks `map.totalTriangleCount > 0` for TERRAIN_GEOMETRY (not just `isPhysicsInitialized`)
   - `initializePhysics()` - handles LOCAL_PLAYER vs REMOTE_PLAYER physics modes
   - **CRITICAL**: Sets position BEFORE calling `character.initializePhysics()`

### Phase 3: Server Implementation
Create `curly.octo.server.factory/`:

7. **ServerGameObjectFactory.java**
   - Skip physics dependencies (server doesn't simulate physics)
   - `createPlayerForConnection(connectionId, spawnPosition)`
   - Integrate with player assignment system

### Phase 4: Integration Points

8. **GameObjectManager.java** - Remove auto-physics initialization
   - Remove lines 147-152 (auto-physics in `add()`)
   - Factory now controls physics timing
   - Keep model loading (factory calls `add()` after physics ready)

9. **ClientGameWorld.java**
   - Add `ClientGameObjectFactory objectFactory` field
   - `setMap()` calls `objectFactory.setCurrentMap(map)`
   - `cleanupForMapRegeneration()` calls `objectFactory.resetDependencies()`

10. **ClientGameMode.java** - Replace PlayerUpdate handler
    - Line 394-406: Use `objectFactory.createRemotePlayer(id, position, yaw)` instead of `new WalkingCharacter()`
    - Factory ensures position is set before physics body

11. **GameServer.java** - Replace direct player creation
    - Line 127-128: Use `serverObjectFactory.createPlayerForConnection()`
    - Add listener for `onObjectActivated` to trigger player assignment

12. **NPCSpawnerAgent.java** - Use factory for NPC creation
    - Replace direct `new WalkingCharacter()` with `serverObjectFactory.createNPC()`
    - Add listener for election trigger when NPC activates

13. **MapTransferBuildAssetsState.java** - Use factory for deserialized objects
    - Route all received `WalkingCharacter` objects through factory
    - Factory determines type from entityId prefix ("npc_" vs player)

### Phase 5: Deprecate PlayerUtilities

14. **PlayerUtilities.java** - Mark as @Deprecated
    - Add deprecation warnings pointing to factory
    - Keep for backwards compatibility during transition

## Critical Files to Modify

| File | Changes |
|------|---------|
| `GameObjectManager.java:147-152` | Remove auto-physics initialization |
| `ClientGameMode.java:394-406` | Replace on-the-fly remote player creation |
| `ClientGameWorld.java` | Add factory field, integrate with map loading |
| `GameServer.java:127-128` | Use server factory for player creation |
| `NPCSpawnerAgent.java` | Use server factory for NPC creation |
| `MapTransferBuildAssetsState.java` | Route objects through factory |

## New Files to Create

```
core/src/main/java/curly/octo/common/factory/
  - ObjectCreationRequest.java
  - ObjectLifecycleState.java
  - ManagedObjectEntry.java
  - GameObjectFactoryListener.java
  - GameObjectFactory.java

core/src/main/java/curly/octo/client/factory/
  - ClientGameObjectFactory.java

core/src/main/java/curly/octo/server/factory/
  - ServerGameObjectFactory.java
```

## Key Design Decisions

1. **Position before physics** - `ObjectCreationRequest` captures spawn position; factory sets it BEFORE `initializePhysics()` call

2. **Terrain geometry check** - Use `map.totalTriangleCount > 0` instead of just `isPhysicsInitialized()` to verify terrain collision mesh exists

3. **Thread safety** - Use `ConcurrentHashMap` and `volatile` flags for network callback safety

4. **Stale request timeout** - 30 second timeout for requests waiting on dependencies

5. **Map regeneration support** - `resetDependencies()` moves active objects back to AWAITING_DEPENDENCIES

## Verification Plan

1. **Unit Tests**
   - Test factory queues requests when dependencies missing
   - Test dependency notification processes pending queue
   - Test position is set before physics body created

2. **Integration Tests**
   - Client joins in-progress game - verify spawn position correct
   - Map regeneration - verify objects recreated at correct positions
   - Multiple clients join simultaneously - verify no race conditions

3. **Manual Testing**
   - Start server, wait for NPCs to spawn
   - Join with client - verify player at spawn point, not (0,0,0)
   - Join with second client during gameplay - verify remote player positions
   - Trigger map regeneration - verify all objects repositioned correctly

## Success Criteria

- [ ] No objects spawn at (0,0,0) incorrectly
- [ ] All object creation goes through factory
- [ ] Physics bodies created only after terrain mesh exists
- [ ] Position set before physics initialization
- [ ] Map regeneration properly resets and recreates objects
- [ ] Remote players appear at correct positions immediately

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
        NPC
    }

    public enum Dependency {
        PHYSICS_WORLD,      // btDiscreteDynamicsWorld exists
        TERRAIN_GEOMETRY,   // Triangle mesh collision built
        MAP_LOADED,         // GameMap fully deserialized
        PLAYER_ASSIGNED     // Server has assigned player ID
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

public abstract class GameObjectFactory {

    protected final Map<String, ManagedObjectEntry> pendingObjects = new ConcurrentHashMap<>();
    protected final Map<String, ManagedObjectEntry> activeObjects = new ConcurrentHashMap<>();

    protected volatile boolean physicsWorldReady = false;
    protected volatile boolean terrainGeometryReady = false;
    protected volatile boolean mapLoaded = false;

    public void requestObject(ObjectCreationRequest request);
    public void notifyDependencyReady(Dependency dependency);
    public void resetDependencies();
    public void update(float delta);

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
            // CRITICAL: Check triangle count, not just isPhysicsInitialized
            if (map.totalTriangleCount > 0) {
                notifyDependencyReady(Dependency.TERRAIN_GEOMETRY);
            }
        }
    }

    @Override
    protected void initializePhysics(ManagedObjectEntry entry) {
        // CRITICAL: Position set BEFORE physics
        if (entry.request.spawnPosition != null) {
            entry.object.setInitialPosition(entry.request.spawnPosition);
        }

        switch (entry.request.objectType) {
            case LOCAL_PLAYER:
            case NPC:
                entry.object.initializePhysics(currentMap.dynamicsWorld, ...);
                break;
            case REMOTE_PLAYER:
                entry.object.initializeRemotePhysics(currentMap, ...);
                break;
        }
    }
}
```
