# Next Steps for NPC Implementation

## Quick Start - Continue Work

### Current Status
✅ **Phase 1 Complete:** Election system infrastructure
✅ **Phase 2 Complete:** Basic NPC spawning and movement
⚠️ **Phase 3 Status:** Implemented but DISABLED (freeze/election issue)
⏳ **Next Options:**
- Fix Phase 3 election issue (recommended - prevents NPC drift)
- OR skip to Phase 4: NPC physics collision

### What Works Right Now
1. Start server → NPCs spawn at spawn points as green cubes
2. Connect client → NPCs appear and execute WANDER/IDLE instructions
3. Second client can join without crashes
4. NPCs move around based on server instructions
5. Election system announces sync authority (check logs)

### What Doesn't Work Yet
1. **Sync corrections** - ⚠️ DISABLED (causes freeze)
   - Issue: Each client thinks it's the authority
   - Result: Game freezes when enabled
   - Consequence: NPCs will drift between clients over time
2. **No NPC collision** - Players walk through NPCs
3. **Limited behaviors** - Only IDLE and WANDER
4. **Placeholder graphics** - Green cubes only

---

## Phase 3: Implement Sync Correction ⚠️ DISABLED

### Status
⚠️ Code implemented but DISABLED due to freeze issue
🐛 **Bug:** All clients freeze when sync enabled
🔍 **Root Cause:** Each client thinks it's the sync authority (election logic issue)

### What Was Implemented (Currently Disabled)
- Sync message generation at 5 FPS
- Server relay architecture (client → server → all clients)
- Drift correction logic (SNAP for > 0.5 units, LERP for 0.05-0.5 units)
- Election state tracking

### Debug Information Added
Check logs for election diagnosis:
```
"My client ID: <uuid>"
"Elected client ID: <uuid>"
"*** THIS CLIENT IS NOW THE NPC SYNC AUTHORITY ***"
```

If **all clients** show "THIS CLIENT IS NOW THE NPC SYNC AUTHORITY", that's the bug!

### To Re-enable (After Fix):
1. Uncomment lines 693-699 in ClientGameMode.java (sync broadcasting)
2. Uncomment lines 517-525 in ClientGameMode.java (sync handler)
3. Uncomment line 73 in GameServer.java (server relay)
4. Test with two clients

---

## Phase 4: Add NPC Physics (NEXT PRIORITY)

### Goal
Players should collide with NPCs and not walk through them.

### Implementation Steps

#### Step 1: Add Physics Fields to NPCObject
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**Add fields** (around line 40, with other transient fields):
```java
// Physics collision (similar to remote players)
private transient btRigidBody physicsBody;
private transient btCapsuleShape physicsShape;
private transient boolean physicsInitialized = false;
```

#### Step 2: Add initializePhysics() Method to NPCObject
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**Add method** (after the existing initializePhysics for remote physics, around line 165):
```java
/**
 * Initialize physics collision body for NPC.
 * NPCs use kinematic bodies (position controlled manually, not by physics).
 */
public void initializePhysics(GameMap map) {
    if (physicsInitialized || physicsBody != null || position == null) return;

    try {
        float radius = NPC_WIDTH / 2f;
        float height = NPC_HEIGHT - radius * 2f;

        // Create capsule shape
        physicsShape = new btCapsuleShape(radius, height);

        // Position capsule so its center is at NPC center
        Matrix4 transform = new Matrix4().setToTranslation(
            position.x, position.y + height/2f + radius, position.z
        );

        // Create kinematic body (zero mass)
        btRigidBody.btRigidBodyConstructionInfo bodyInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0, null, physicsShape, new Vector3(0,0,0));
        physicsBody = new btRigidBody(bodyInfo);
        bodyInfo.dispose();

        // Set as kinematic (manually controlled position)
        physicsBody.setCollisionFlags(
            physicsBody.getCollisionFlags() |
            btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT
        );
        physicsBody.setWorldTransform(transform);

        // Add to physics world with player collision group
        map.dynamicsWorld.addRigidBody(physicsBody, GameMap.PLAYER_GROUP,
                                       GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP);

        physicsInitialized = true;
        Log.info("NPCObject", "Physics initialized for NPC " + entityId);
    } catch (Exception e) {
        Log.error("NPCObject", "Failed to initialize NPC physics: " + e.getMessage());
    }
}
```

#### Step 3: Update Physics Body Position in NPCObject.update()
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**Modify** the `update()` method (around line 230):
```java
@Override
public void update(float delta) {
    super.update(delta);

    // ... existing instruction execution code ...

    // Update physics body position to match NPC movement
    if (physicsBody != null && physicsInitialized) {
        float radius = NPC_WIDTH / 2f;
        float height = NPC_HEIGHT - radius * 2f;
        Matrix4 transform = new Matrix4().setToTranslation(
            position.x, position.y + height/2f + radius, position.z
        );
        physicsBody.setWorldTransform(transform);
    }
}
```

#### Step 4: Add Physics Initialization Call in GameObjectManager
**File:** `core/src/main/java/curly/octo/client/GameObjectManager.java`

**Modify** the `add(GameObject gameObject)` method (around line 119, in the NPCObject case):
```java
if (gameObject instanceof curly.octo.common.NPCObject) {
    curly.octo.common.NPCObject npcObject = (curly.octo.common.NPCObject) gameObject;
    Log.info("GameObjectManager", "Adding NPC: " + npcObject.entityId);

    // Initialize graphics
    if (!npcObject.isGraphicsInitialized()) {
        npcObject.initializeGraphics(modelAssetManager);
    }

    // Initialize physics (if map is ready)
    // Use Main.getInstance() to get the game world
    if (curly.octo.Main.getInstance() != null &&
        curly.octo.Main.getInstance().getGameWorld() != null) {
        curly.octo.client.ClientGameWorld gameWorld =
            (curly.octo.client.ClientGameWorld) curly.octo.Main.getInstance().getGameWorld();
        if (gameWorld.getMapManager() != null &&
            gameWorld.getMapManager().isPhysicsInitialized()) {
            npcObject.initializePhysics(gameWorld.getMapManager());
            Log.info("GameObjectManager", "NPC physics initialized for: " + npcObject.entityId);
        }
    }
}
```

#### Step 5: Add Physics Cleanup in NPCObject.dispose()
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**Modify** the `dispose()` method (around line 365):
```java
@Override
public void dispose() {
    // Clean up physics
    if (physicsBody != null) {
        physicsBody.dispose();
        physicsBody = null;
    }
    if (physicsShape != null) {
        physicsShape.dispose();
        physicsShape = null;
    }
    physicsInitialized = false;
    graphicsInitialized = false;
}
```

#### Step 6: Build and Test
```bash
./gradlew.bat compileJava
./gradlew.bat lwjgl3:run
```

**Test Plan:**
1. Run game and connect client
2. Observe NPCs - they should have green cubes
3. Try to walk through an NPC - **you should collide and be blocked**
4. NPCs should move around while maintaining collision

**Expected Behavior:**
- Player cannot walk through NPCs
- NPCs act as solid obstacles
- No physics jitter or tunneling

---

## Phase 5: Advanced Instructions (OPTIONAL)

### PATROL - NPCs walk between waypoints
### CHASE - NPCs follow nearby players
### Pathfinding - NPCs navigate around obstacles

See `NPC_IMPLEMENTATION_STATUS.md` for detailed instructions on implementing these features.

---

## Troubleshooting

### "Cannot send to all clients: server not initialized"
**Problem:** Elected client trying to broadcast directly to other clients
**Solution:** ✅ FIXED - Elected client now sends to server, which relays to all clients
**Architecture:** Client → Server → All Clients (server-authoritative relay)

### NPCs Don't Spawn
**Check logs for:** "NPC agents initialized"
- If missing, map might be null when agents created
- Look at `ServerCoordinator.regenerateMap()` - should call `instantiateServerAgents()`

### NPCs Off Map
**Check:** Spawn position calculation in `NPCSpawnerAgent.java`
- Should use raw tile coordinates: `new Vector3(spawnTile.x, spawnTile.y, spawnTile.z)`
- NOT multiplied by MAP_TILE_SIZE

### Second Client Crashes (Bullet JNI Error)
**Check:** `ClientGameMode.java` around line 415
- Should have guard: `if (gameWorld.getMapManager() != null && gameWorld.getMapManager().isPhysicsInitialized())`
**Check:** `ClientGameWorld.java` around line 78
- Should have deferred physics init loop after map setup

### NPCs Not Moving
**Check logs for:** "Received NPC instruction"
- If missing, `NPCBehaviorAgent` might not be running
- Check `NPCBehaviorAgent` is in `ServerCoordinator.serverAgents` list

### NPCs Drift Between Clients
**Expected before Phase 3** - this is what Phase 3 fixes!
- After Phase 3, drift should be < 0.5 units after 1 minute

---

## File Quick Reference

### To modify for Phase 3:
1. `core/src/main/java/curly/octo/client/ClientGameMode.java` - Add sync broadcast and correction
2. `core/src/main/java/curly/octo/common/NPCObject.java` - Add getCurrentInstructionId()

### Key files to understand:
- `NPCObject.java` - NPC entity with instruction execution
- `NPCBehaviorAgent.java` - Generates instructions on server
- `NPCSpawnerAgent.java` - Spawns NPCs on server
- `NPCElectionMessage.java` - Elects sync authority
- `NPCSyncMessage.java` - Position corrections

### Build commands:
```bash
./gradlew.bat compileJava          # Compile only
./gradlew.bat lwjgl3:run           # Run game
./gradlew.bat lwjgl3:jar           # Build JAR
```

---

## Design Decisions Made

### Why client-side simulation?
- Reduces server CPU load
- NPCs remain responsive even with high latency
- Deterministic execution ensures initial consistency

### Why elected peer sync?
- Server doesn't need to track full NPC state
- One client acts as "ground truth" to fix drift
- Scales better than full server authority

### Why seeded Random?
- Ensures all clients execute instructions identically
- Prevents immediate divergence
- Keeps network traffic low (only seed transmitted)

### Why 5 FPS sync rate?
- Balance between sync accuracy and network bandwidth
- Higher = smoother but more traffic
- Lower = less traffic but more visible corrections

---

**Ready to continue? Start with Phase 3, Step 1!**
