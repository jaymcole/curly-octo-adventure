# NPC Implementation Status

## Overview
This document tracks the implementation of NPCs (Non-Player Characters) with client-side simulation and elected peer synchronization.

## Architecture Design
**Sync Model:** Client-side simulation with elected authority corrections
- Server distributes **instructions** (IDLE, WANDER, PATROL, etc.) to all clients
- Each client **executes instructions deterministically** using seeded RNG
- One client is **elected as sync authority** to broadcast position corrections
- Observer clients apply corrections to fix simulation drift

## Completed Work

### Phase 1: Election System Infrastructure ✅
**Files Created:**
- `core/src/main/java/curly/octo/common/network/messages/NPCInstructionMessage.java`
  - Server distributes behavior instructions to all clients
  - Contains: npcId, instructionId, type (IDLE/WANDER/PATROL/CHASE/CUSTOM_PATH), duration, randomSeed, params

- `core/src/main/java/curly/octo/common/network/messages/NPCElectionMessage.java`
  - Server announces which client is sync authority
  - Contains: electedClientId, electionTimestamp, reason (INITIAL/CLIENT_JOIN/CLIENT_DISCONNECT/FAILOVER/MANUAL)

- `core/src/main/java/curly/octo/common/network/messages/NPCSyncMessage.java`
  - Elected client sends position corrections at 5-10 FPS
  - Contains: syncId, timestamp, syncType (FULL/DELTA/CRITICAL), npcIds[], positions[], orientations[], activeInstructionIds[]

- `core/src/main/java/curly/octo/server/NPCElectionManager.java`
  - Deterministic election (first client alphabetically by ClientUniqueId)
  - Handles re-election on disconnect/join

**Files Modified:**
- `core/src/main/java/curly/octo/common/network/NetworkMessageRegistry.java` - Registered NPC messages
- `core/src/main/java/curly/octo/common/network/KryoNetwork.java` - Registered NPCObject.class
- `core/src/main/java/curly/octo/server/GameServer.java` - Integrated NPCElectionManager, runs elections on player join/disconnect
- `core/src/main/java/curly/octo/client/ClientGameMode.java` - Added election state tracking and message handlers

### Phase 2: Basic NPC Implementation ✅
**Files Created:**
- `core/src/main/java/curly/octo/common/NPCObject.java`
  - Extends WorldObject for rendering and physics
  - Executes instructions deterministically using seeded Random
  - Green cube placeholder model (1.0 × 1.8 × 1.0 units)
  - WANDER: Random walk within radius from center point
  - IDLE: Stand still

- `core/src/main/java/curly/octo/server/serverAgents/NPCSpawnerAgent.java`
  - Spawns 10 NPCs at map spawn points (or origin if no hints)
  - Runs in constructor for immediate spawning

- `core/src/main/java/curly/octo/server/serverAgents/NPCBehaviorAgent.java`
  - Generates instructions every 10 seconds
  - 80% WANDER, 20% IDLE distribution
  - Broadcasts instructions to all clients via NetworkManager

**Files Modified:**
- `core/src/main/java/curly/octo/server/ServerCoordinator.java`
  - Added NPC agent instantiation in `instantiateServerAgents()`
  - **CRITICAL FIX:** Re-instantiate agents after map creation in `regenerateMap()`

- `core/src/main/java/curly/octo/client/GameObjectManager.java`
  - Added NPC graphics initialization
  - **CRITICAL FIX:** Simplified render queue logic to exclude local player only
  - Added position check before player graphics init to prevent frozen models at origin

- `core/src/main/java/curly/octo/server/ServerGameObjectManager.java`
  - Added NPC logging

- `core/src/main/java/curly/octo/client/ClientGameMode.java`
  - Added NPCInstructionMessage handler to execute instructions on NPCs
  - **CRITICAL FIX:** Guard against Bullet Physics crashes by checking if map is initialized before creating remote player physics

- `core/src/main/java/curly/octo/client/ClientGameWorld.java`
  - **CRITICAL FIX:** Initialize deferred remote player physics after map loads

- `core/src/main/java/curly/octo/common/PlayerObject.java`
  - Added `isRemotePhysicsInitialized()` helper method

## Critical Fixes Applied

### Fix 1: NPC Spawn Position (NPCSpawnerAgent.java:62-67)
**Problem:** NPCs spawning off-map due to coordinate system mismatch
**Root Cause:** Incorrectly multiplied spawn coordinates by MAP_TILE_SIZE
**Solution:** Use raw tile coordinates (same as player spawns)
```java
// Before: spawnTile.x * Constants.MAP_TILE_SIZE + ...
// After:  new Vector3(spawnTile.x, spawnTile.y, spawnTile.z)
```

### Fix 2: NPC Agents Not Initializing (ServerCoordinator.java:228)
**Problem:** NPC agents never created - no NPCs spawned
**Root Cause:** `instantiateServerAgents()` called before map exists (mapManager == null)
**Solution:** Re-call `instantiateServerAgents()` after map creation in `regenerateMap()`

### Fix 3: Bullet Physics Threading Crash (ClientGameMode.java:415-419, ClientGameWorld.java:78-86)
**Problem:** Second client crashes with `UnsatisfiedLinkError` in Bullet JNI
**Root Cause:** Remote player physics created before `Bullet.init()` called
**Solution:**
1. Guard physics creation - only init if map exists and physics initialized
2. Deferred initialization - loop through remote players after map loads and init physics

### Fix 4: Local Player Rendering in First-Person (GameObjectManager.java:58)
**Problem:** Player seeing their own model, NPCs appearing "pinned" to player
**Root Cause:** Broken RENDER_SELF logic rendering all objects
**Solution:** Simplified to always exclude local player: `if (localPlayer == null || !object.entityId.equals(localPlayer.entityId))`

## Remaining Work

### Phase 3: Sync Correction System (HIGH PRIORITY)
**Goal:** Prevent simulation drift between clients

#### 3.1 Elected Client Sync Generation
**File to modify:** `core/src/main/java/curly/octo/client/ClientGameMode.java`

Add timer to elected client:
```java
private float npcSyncTimer = 0f;
private static final float NPC_SYNC_INTERVAL = 0.2f; // 5 FPS
```

In `update()` method, if `isNPCSyncAuthority`:
```java
if (isNPCSyncAuthority) {
    npcSyncTimer += deltaTime;
    if (npcSyncTimer >= NPC_SYNC_INTERVAL) {
        broadcastNPCSync();
        npcSyncTimer = 0f;
    }
}
```

Implement `broadcastNPCSync()`:
```java
private void broadcastNPCSync() {
    List<NPCObject> npcs = new ArrayList<>();
    for (GameObject obj : gameWorld.getGameObjectManager().getAllObjects()) {
        if (obj instanceof NPCObject) npcs.add((NPCObject) obj);
    }

    NPCSyncMessage sync = new NPCSyncMessage();
    sync.syncId = System.currentTimeMillis();
    sync.timestamp = sync.syncId;
    sync.syncType = NPCSyncMessage.SyncType.DELTA;

    // Pack NPC data into arrays
    sync.npcIds = new String[npcs.size()];
    sync.positions = new float[npcs.size() * 3];
    sync.orientations = new float[npcs.size() * 2];
    sync.activeInstructionIds = new long[npcs.size()];

    for (int i = 0; i < npcs.size(); i++) {
        NPCObject npc = npcs.get(i);
        sync.npcIds[i] = npc.entityId;
        sync.positions[i*3] = npc.getPosition().x;
        sync.positions[i*3+1] = npc.getPosition().y;
        sync.positions[i*3+2] = npc.getPosition().z;
        sync.orientations[i*2] = npc.getYaw();
        sync.orientations[i*2+1] = 0f; // pitch if needed
        sync.activeInstructionIds[i] = npc.getCurrentInstructionId(); // Need to add this getter
    }

    NetworkManager.sendToAllClients(sync);
}
```

#### 3.2 Observer Client Sync Application
**File to modify:** `core/src/main/java/curly/octo/client/ClientGameMode.java`

Add NPCSyncMessage handler in `setupNetworkListeners()`:
```java
NetworkManager.onReceive(NPCSyncMessage.class, syncMessage -> {
    Gdx.app.postRunnable(() -> {
        // Skip if we're the sync authority
        if (isNPCSyncAuthority) return;

        applySyncCorrections(syncMessage);
    });
});
```

Implement `applySyncCorrections()`:
```java
private void applySyncCorrections(NPCSyncMessage sync) {
    for (int i = 0; i < sync.npcIds.length; i++) {
        GameObject obj = gameWorld.getGameObjectManager().getObjectById(sync.npcIds[i]);
        if (obj instanceof NPCObject) {
            NPCObject npc = (NPCObject) obj;

            Vector3 authorityPos = new Vector3(
                sync.positions[i*3],
                sync.positions[i*3+1],
                sync.positions[i*3+2]
            );

            // Smooth correction instead of snap (reduce jitter)
            Vector3 currentPos = npc.getPosition();
            float distance = currentPos.dst(authorityPos);

            if (distance > 0.5f) {
                // Large drift - snap to authority position
                npc.setPosition(authorityPos);
                Log.warn("NPCSync", "Large drift detected for " + npc.entityId + ": " + distance);
            } else if (distance > 0.05f) {
                // Small drift - lerp to authority position
                npc.setPosition(currentPos.lerp(authorityPos, 0.3f));
            }
            // else: negligible drift, ignore

            npc.setYaw(sync.orientations[i*2]);
        }
    }
}
```

#### 3.3 Required NPCObject Additions
**File to modify:** `core/src/main/java/curly/octo/common/NPCObject.java`

Add field:
```java
private long currentInstructionId = -1;
```

In `executeInstruction()`:
```java
public void executeInstruction(NPCInstructionMessage instruction) {
    this.currentInstruction = instruction;
    this.currentInstructionId = instruction.instructionId;
    // ... rest of method
}
```

Add getter:
```java
public long getCurrentInstructionId() {
    return currentInstructionId;
}
```

### Phase 4: NPC Physics Collision
**Goal:** NPCs can block player movement

**File to modify:** `core/src/main/java/curly/octo/common/NPCObject.java`

Add physics body similar to remote players:
```java
private transient btRigidBody physicsBody;
private transient btCapsuleShape physicsShape;

public void initializePhysics(GameMap map) {
    if (physicsBody != null || position == null) return;

    float radius = NPC_WIDTH / 2f;
    float height = NPC_HEIGHT - radius * 2f;

    physicsShape = new btCapsuleShape(radius, height);

    Matrix4 transform = new Matrix4().setToTranslation(
        position.x, position.y + height/2f + radius, position.z
    );

    btRigidBody.btRigidBodyConstructionInfo bodyInfo =
        new btRigidBody.btRigidBodyConstructionInfo(0, null, physicsShape, new Vector3(0,0,0));
    physicsBody = new btRigidBody(bodyInfo);
    bodyInfo.dispose();

    physicsBody.setCollisionFlags(
        physicsBody.getCollisionFlags() |
        btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT
    );
    physicsBody.setWorldTransform(transform);

    map.dynamicsWorld.addRigidBody(physicsBody, GameMap.PLAYER_GROUP, GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP);
}

@Override
public void update(float delta) {
    super.update(delta);

    // Update physics body position after movement
    if (physicsBody != null) {
        Matrix4 transform = new Matrix4().setToTranslation(
            position.x, position.y + NPC_HEIGHT/2f, position.z
        );
        physicsBody.setWorldTransform(transform);
    }
}
```

**File to modify:** `core/src/main/java/curly/octo/client/GameObjectManager.java`

In `add(GameObject gameObject)`, after NPC graphics initialization:
```java
if (gameObject instanceof NPCObject) {
    NPCObject npcObject = (NPCObject) gameObject;
    // ... existing graphics init ...

    // Initialize physics if map is ready
    if (Main.getInstance().getGameWorld() != null &&
        Main.getInstance().getGameWorld().getMapManager() != null &&
        Main.getInstance().getGameWorld().getMapManager().isPhysicsInitialized()) {
        npcObject.initializePhysics(Main.getInstance().getGameWorld().getMapManager());
    }
}
```

### Phase 5: Advanced Instructions
**Goal:** More complex NPC behaviors

#### 5.1 PATROL Instruction
**File to modify:** `core/src/main/java/curly/octo/common/NPCObject.java`

Add waypoint system:
```java
private List<Vector3> patrolWaypoints;
private int currentWaypointIndex = 0;

// In executeInstruction():
case PATROL:
    // Server sends waypoints via params: "waypoint0_x", "waypoint0_y", etc.
    patrolWaypoints = new ArrayList<>();
    int waypointCount = (int) instruction.getParam("waypointCount", 0f);
    for (int i = 0; i < waypointCount; i++) {
        patrolWaypoints.add(new Vector3(
            instruction.getParam("waypoint" + i + "_x", 0f),
            instruction.getParam("waypoint" + i + "_y", 0f),
            instruction.getParam("waypoint" + i + "_z", 0f)
        ));
    }
    currentWaypointIndex = 0;
    break;

// In update():
if (currentInstruction.type == InstructionType.PATROL && !patrolWaypoints.isEmpty()) {
    Vector3 target = patrolWaypoints.get(currentWaypointIndex);
    Vector3 direction = new Vector3(target).sub(position).nor();

    if (position.dst(target) < 0.5f) {
        // Reached waypoint, move to next
        currentWaypointIndex = (currentWaypointIndex + 1) % patrolWaypoints.size();
    } else {
        position.add(direction.scl(MOVE_SPEED * deltaTime));
        yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90f;
    }
}
```

#### 5.2 CHASE Instruction
**File to modify:** `core/src/main/java/curly/octo/server/serverAgents/NPCBehaviorAgent.java`

Add chase behavior generation:
```java
private void generateChaseInstruction(NPCObject npc) {
    PlayerObject nearestPlayer = objectManager.getNearestPlayer(npc.getPosition());
    if (nearestPlayer != null && npc.getPosition().dst(nearestPlayer.getPosition()) < 20f) {
        NPCInstructionMessage instruction = new NPCInstructionMessage(...);
        instruction.type = InstructionType.CHASE;
        instruction.withParam("targetPlayerId", nearestPlayer.entityId);
        instruction.withParam("maxChaseDistance", 30f);
        instruction.duration = 5f;
        NetworkManager.sendToAllClients(instruction);
    }
}
```

**File to modify:** `core/src/main/java/curly/octo/common/NPCObject.java`

Add chase execution:
```java
case CHASE:
    String targetId = instruction.params.get("targetPlayerId").toString();
    // Store target, update will handle movement
    break;

// In update():
if (currentInstruction.type == InstructionType.CHASE) {
    GameObject targetObj = getGameObjectById(targetPlayerId); // Need reference
    if (targetObj instanceof PlayerObject) {
        Vector3 targetPos = targetObj.getPosition();
        Vector3 direction = new Vector3(targetPos).sub(position).nor();

        float distance = position.dst(targetPos);
        float maxDistance = currentInstruction.getParam("maxChaseDistance", 30f);

        if (distance < maxDistance && distance > 1f) {
            position.add(direction.scl(MOVE_SPEED * 1.5f * deltaTime)); // Faster when chasing
            yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90f;
        }
    }
}
```

### Phase 6: Pathfinding (OPTIONAL)
**Goal:** NPCs navigate around obstacles

Consider integrating a pathfinding library like gdx-ai:
- Add `com.badlogicgames.gdx:gdx-ai:1.8.2` dependency
- Implement A* pathfinding on map grid
- Generate waypoints for WANDER/PATROL that avoid walls

### Phase 7: Visual Improvements
**Goal:** Better NPC appearance

#### 7.1 Replace Green Cube with Model
**File to modify:** `core/src/main/java/curly/octo/common/NPCObject.java`

Replace `initializeGraphics()` to load a .gltf model:
```java
SceneAsset sceneAsset = modelAssetManager.getSceneAsset("models/npc_character.gltf");
setModelInstance(new ModelInstance(sceneAsset.scene.model));
```

#### 7.2 Add Animation
Requires animation controller for walk/idle states - reference PlayerObject animation system if implemented.

## Testing Checklist

### Current State (Should Work)
- [x] NPCs spawn at map spawn points
- [x] NPCs render as green cubes
- [x] NPCs receive WANDER/IDLE instructions every 10 seconds
- [x] NPCs execute instructions deterministically
- [x] Multiple clients see NPCs in same locations initially
- [x] Second client can join without crashes
- [x] NPCs included in MapTransferPayload

### To Verify After Phase 3
- [ ] Elected client broadcasts sync messages at 5 FPS
- [ ] Observer clients receive and apply corrections
- [ ] NPCs stay synchronized across clients after 1+ minutes
- [ ] Drift < 0.5 units after 1 minute of simulation
- [ ] Re-election works when sync authority disconnects

### To Verify After Phase 4
- [ ] Player collides with NPCs (cannot walk through)
- [ ] NPCs block doorways properly
- [ ] No physics jitter or tunneling

### To Verify After Phase 5
- [ ] NPCs patrol between waypoints smoothly
- [ ] NPCs chase nearby players
- [ ] Chase ends at max distance

## Known Limitations

1. **No server-side NPC physics:** Server only tracks state, doesn't simulate physics
2. **No NPC-NPC collision:** NPCs can walk through each other
3. **Simple AI:** Just IDLE/WANDER currently
4. **No persistence:** NPCs reset on map regeneration
5. **Placeholder graphics:** Green cubes only
6. **No health/damage:** NPCs are purely visual/collision obstacles
7. **No spawn variety:** All NPCs use same behavior distribution

## File Reference

### Core NPC Files
- `NPCObject.java` - NPC entity class
- `NPCSpawnerAgent.java` - Server-side spawning
- `NPCBehaviorAgent.java` - Server-side instruction generation
- `NPCElectionManager.java` - Sync authority election

### Network Messages
- `NPCInstructionMessage.java` - Behavior instructions
- `NPCElectionMessage.java` - Sync authority announcements
- `NPCSyncMessage.java` - Position corrections

### Integration Points
- `ServerCoordinator.java:228` - NPC agent initialization after map creation
- `GameObjectManager.java:119-129` - Client-side NPC graphics/physics init
- `ClientGameMode.java:415-419` - Guard against uninitialized physics
- `ClientGameWorld.java:78-86` - Deferred remote player physics init

### Phase 3: Sync Correction System ⚠️ (IMPLEMENTED BUT DISABLED - 2025-12-29)
**Goal:** Prevent simulation drift between clients
**Status:** Code implemented but DISABLED due to election/freeze issue

#### Known Issue:
When enabled, all clients freeze. Suspect each client is incorrectly being set as sync authority.
Need to diagnose election logic before re-enabling.

#### Implementation (Currently Disabled):

**Files Modified:**

1. **ClientGameMode.java** - Added sync broadcasting and correction
   - Lines 85-87: Added npcSyncTimer and NPC_SYNC_INTERVAL fields
   - Lines 679-686: Added sync broadcasting logic in update() method
   - Lines 513-520: Added NPCSyncMessage handler
   - Lines 1000-1049: Added broadcastNPCSync() method (sends to server, not direct broadcast)
   - Lines 1051-1099: Added applySyncCorrections() method

2. **GameObjectManager.java** - Added getAllObjects() method
   - Lines 145-151: Added getAllObjects() for safe iteration during sync

3. **NPCObject.java** - Added instruction ID tracking
   - Line 30: Added currentInstructionId field
   - Line 171: Set currentInstructionId in executeInstruction()
   - Lines 350-356: Added getCurrentInstructionId() getter method

4. **GameServer.java** - Added server relay for sync messages
   - Line 72: Registered NPCSyncMessage handler
   - Lines 171-182: Added handleNPCSync() method to relay messages to all clients

#### How It Works:

**Elected Client (Sync Authority):**
- Generates NPCSyncMessage at 5 FPS (every 0.2 seconds)
- Packs NPC positions, orientations, and instruction IDs into arrays
- Sends to **server** via NetworkManager.sendToServer()

**Server (Relay):**
- Receives NPCSyncMessage from elected client
- Relays to all connected clients via UDP

**Observer Clients:**
- Receive NPCSyncMessage from authority
- Calculate drift distance for each NPC
- Apply correction:
  - Drift > 0.5 units: **SNAP** (instant position update)
  - Drift 0.05-0.5 units: **LERP** (smooth 30% blend)
  - Drift < 0.05 units: **IGNORE** (negligible)
- Log corrections for monitoring

**Benefits:**
- Prevents NPCs from drifting apart over time
- Smooth corrections reduce visual jitter
- Scalable (only 5 FPS sync rate needed)

## Version Info
- **Last Updated:** 2025-12-29
- **Phase Completed:** Phase 2 - Basic NPC Implementation ✅
- **Phase 3 Status:** ⚠️ Implemented but DISABLED (election/freeze issue)
- **Next Phase:** Fix Phase 3 election OR skip to Phase 4 - NPC Physics Collision
- **Build Status:** ✅ Compiles successfully
- **Test Status:** ✅ NPCs spawn and move, ✅ Multiple clients can join
- **Known Issue:** NPCs will drift between clients without Phase 3 sync corrections
