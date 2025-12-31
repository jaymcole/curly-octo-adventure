# NPC Physics Implementation Plan

**Status:** Ready to implement
**Date:** 2025-12-30
**Goal:** Add physics bodies to NPCs so they interact with the game world alongside players

## Design Decisions

Based on user preferences:
- **Collision Shape:** Capsule (like players) - radius=0.3, height=1.2
- **Collision Group:** New NPC_GROUP (separate from players for flexible collision rules)
- **Push Mechanics:** Yes - players can push NPCs with collision forces

## Architecture Overview

### Current State
- NPCs already have `initializePhysics()` method but it's never called
- Current implementation uses box collision without collision groups
- No physics interaction with players or environment

### Target State
- NPCs use capsule collision shapes matching player architecture
- NPCs belong to NPC_GROUP and collide with: GROUND_GROUP | PLAYER_GROUP | NPC_GROUP
- Players can push NPCs using external force system
- Physics initialized automatically when NPC is added to client game world

## Implementation Steps

### 1. Add NPC_GROUP Collision Constant
**File:** `core/src/main/java/curly/octo/common/map/GameMap.java` (line ~38)

**Changes:**
```java
// Add after existing PLAYER_GROUP constant
public static final int NPC_GROUP = 1 << 2;  // 0x04
```

**Rationale:** Separate collision group allows different rules for NPC-NPC vs NPC-Player collisions

---

### 2. Refactor NPCObject.initializePhysics()
**File:** `core/src/main/java/curly/octo/common/NPCObject.java` (lines 131-164)

**Current Issues:**
- Uses `btBoxShape` instead of capsule
- No collision group/mask specified
- Position offset doesn't account for capsule geometry

**Changes:**
1. Replace box with capsule:
   ```java
   // OLD: physicsShape = new btBoxShape(new Vector3(NPC_WIDTH / 2, NPC_HEIGHT / 2, NPC_DEPTH / 2));
   // NEW:
   float capsuleRadius = 0.3f;
   float capsuleHeight = 1.2f;
   physicsShape = new btCapsuleShape(capsuleRadius, capsuleHeight);
   ```

2. Adjust position offset for capsule bottom:
   ```java
   // Capsule center is at height/2 + radius above ground
   Matrix4 transform = new Matrix4();
   transform.setToTranslation(
       position.x,
       position.y + capsuleHeight/2f + capsuleRadius,  // Offset for capsule
       position.z
   );
   transform.rotate(Vector3.Y, yaw);
   ```

3. Add collision groups:
   ```java
   // OLD: dynamicsWorld.addRigidBody(physicsBody);
   // NEW:
   dynamicsWorld.addRigidBody(physicsBody,
       GameMap.NPC_GROUP,  // NPCs are in NPC group
       GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP | GameMap.NPC_GROUP);  // Collide with ground, players, and other NPCs
   ```

**Reference:** PlayerObject.initializeRemotePhysics() (lines 499-532) uses similar pattern

---

### 3. Add Push Mechanics to NPCObject
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**New Field (add around line 48):**
```java
private Vector3 externalForce = new Vector3(0, 0, 0);  // For push mechanics
```

**New Method:**
```java
/**
 * Apply external force to NPC (e.g., from player collision)
 * @param force Force vector to apply
 */
public void applyPushForce(Vector3 force) {
    if (externalForce == null) {
        externalForce = new Vector3();
    }
    externalForce.add(force);
}
```

**Update physics position handling in update() method (lines 249-262):**
```java
// Apply external forces to position
if (externalForce != null && externalForce.len() > 0.01f) {
    position.add(externalForce.x * delta, externalForce.y * delta, externalForce.z * delta);
    externalForce.scl(0.95f);  // Damping
}

// Update physics body position if initialized
if (physicsBody != null && position != null) {
    Matrix4 transform = new Matrix4();
    transform.setToTranslation(
        position.x,
        position.y + 0.9f,  // Capsule offset (height/2 + radius)
        position.z
    );
    transform.rotate(Vector3.Y, yaw);
    physicsBody.setWorldTransform(transform);
}
```

**Reference:** PlayerObject.updatePhysicsMode() (lines 258-305) uses similar external force system

---

### 4. Call NPC Physics Initialization
**File:** `core/src/main/java/curly/octo/client/GameObjectManager.java` (line ~129)

**Changes:**
```java
// Special handling for NPCObjects
if (gameObject instanceof curly.octo.common.NPCObject) {
    curly.octo.common.NPCObject npcObject = (curly.octo.common.NPCObject) gameObject;
    Log.info("GameObjectManager", "Adding NPC: " + npcObject.entityId + " at position: " + npcObject.getPosition());

    // Initialize graphics with placeholder model
    if (!npcObject.isGraphicsInitialized()) {
        npcObject.initializeGraphics(modelAssetManager);
        Log.info("GameObjectManager", "NPC graphics initialized for: " + npcObject.entityId);
    }

    // NEW: Initialize physics
    curly.octo.client.ClientGameWorld gameWorld =
        (curly.octo.client.ClientGameWorld) curly.octo.Main.getInstance().getGameWorld();
    if (gameWorld != null &&
        gameWorld.getMapManager() != null &&
        gameWorld.getMapManager().isPhysicsInitialized()) {
        npcObject.initializePhysics(gameWorld.getMapManager().dynamicsWorld);
        Log.info("GameObjectManager", "NPC physics initialized for: " + npcObject.entityId);
    } else {
        Log.warn("GameObjectManager", "Skipping NPC physics init - map not ready yet for: " + npcObject.entityId);
    }
}
```

---

### 5. Add Player-to-NPC Push Detection (Future Enhancement)
**Status:** Optional - implement after basic physics working

**Approach:**
- In PlayerObject collision handling or PhysicsManager
- Detect when player physics body collides with NPC
- Calculate push direction and magnitude
- Call `npc.applyPushForce(pushVector)`
- Similar to player-player "squish" mechanic (commit 87d612f)

---

### 6. Update Documentation
**File:** `CLAUDE.md` (add new section)

**Add section:**
```markdown
### NPC Physics System

NPCs use physics bodies for collision detection and blocking behavior:

- **Collision Shape:** Capsule (radius=0.3, height=1.2) matching player geometry
- **Collision Group:** NPC_GROUP (separate from players)
- **Collides With:** Ground, players, and other NPCs
- **Movement:** Kinematic rigid body (AI-controlled position, not physics simulation)
- **Push Mechanics:** NPCs can be nudged by player collisions using external force system

**Key Files:**
- `NPCObject.java` - Physics initialization and update logic
- `GameMap.java` - Collision group constants (NPC_GROUP = 0x04)
- `GameObjectManager.java` - Automatic physics initialization when NPC spawns

**Physics Initialization:**
NPCs automatically initialize physics when added to the client game world, provided the map physics system is ready.
```

---

## Testing Plan

### Basic Collision
1. Build and run game with server + 2 clients
2. Spawn NPCs using NPCSpawnerAgent
3. Walk player toward NPC
4. **Expected:** Player should be blocked by NPC (cannot walk through)
5. **Expected:** NPC appears at correct height (not floating or sunk into ground)

### Collision Shape Verification
1. Enable physics debug rendering if available
2. Verify capsule shape appears around NPC
3. **Expected:** Capsule dimensions match code (radius=0.3, height=1.2)

### Push Mechanics
1. Walk player into NPC and hold forward
2. **Expected:** NPC should nudge slightly in push direction
3. Stop player movement
4. **Expected:** NPC should slow down and stop (damping effect)

### Multi-NPC Collision
1. Spawn multiple NPCs close together
2. Walk player into NPC cluster
3. **Expected:** NPCs block player and each other
4. **Expected:** No NPCs overlapping or clipping through each other

---

## Physics Architecture Reference

### Collision Groups
```
GROUND_GROUP  = 1 << 0  // 0x01 - Static map geometry
PLAYER_GROUP  = 1 << 1  // 0x02 - Player characters
NPC_GROUP     = 1 << 2  // 0x04 - NPC characters
```

### Collision Matrix
| Group        | Collides With                          |
|--------------|----------------------------------------|
| GROUND_GROUP | PLAYER_GROUP, NPC_GROUP                |
| PLAYER_GROUP | GROUND_GROUP, PLAYER_GROUP, NPC_GROUP  |
| NPC_GROUP    | GROUND_GROUP, PLAYER_GROUP, NPC_GROUP  |

### Physics Types Comparison
| Feature            | Local Player               | Remote Player          | NPC                    |
|--------------------|----------------------------|------------------------|------------------------|
| Physics Type       | btKinematicCharacterController | btRigidBody (kinematic) | btRigidBody (kinematic) |
| Collision Shape    | Capsule (r=1.0, h=5.0)    | Capsule (r=1.0, h=5.0) | Capsule (r=0.3, h=1.2) |
| Mass               | 0 (kinematic)              | 0 (kinematic)          | 0 (kinematic)          |
| Collision Group    | PLAYER_GROUP               | PLAYER_GROUP           | NPC_GROUP              |
| Position Control   | Physics + Input            | Network sync           | AI + External forces   |
| Gravity            | Yes (via controller)       | No                     | No                     |
| External Forces    | Yes (push mechanics)       | No                     | Yes (push mechanics)   |

---

## File Summary

### Files to Modify:
1. **GameMap.java** - Add NPC_GROUP constant
2. **NPCObject.java** - Refactor initializePhysics() + add push mechanics
3. **GameObjectManager.java** - Call physics initialization
4. **CLAUDE.md** - Document NPC physics architecture

### Key Line Numbers:
- `GameMap.java:38` - Collision group constants
- `NPCObject.java:131-164` - initializePhysics() method
- `NPCObject.java:249-262` - Physics update in update() method
- `GameObjectManager.java:118-129` - NPC add() method

### Reference Files:
- `PlayerObject.java:499-532` - initializeRemotePhysics() (good model for NPC physics)
- `PlayerObject.java:258-305` - updatePhysicsMode() (external force example)
- `PlayerObject.java:87d612f` - Player-player push mechanics (git commit)

---

## Future Enhancements

### Phase 2: Advanced Physics
- Dynamic NPCs (mass > 0) that respond to forces
- Ragdoll physics on NPC death
- Different NPC sizes (small, medium, large capsules)

### Phase 3: Collision Response
- Detect player-NPC collisions explicitly
- Apply directional push forces based on collision normal
- Add push strength based on player velocity

### Phase 4: Pathfinding Integration
- Use physics for obstacle detection during pathfinding
- Avoid other NPCs during movement
- Collision-aware waypoint navigation

---

## Notes

- NPCs use kinematic bodies (not dynamic) because AI controls position, not physics simulation
- Push mechanics are cosmetic - NPCs return to AI-controlled position after push force decays
- Capsule shape provides smooth collision and matches player geometry scale
- Separate NPC_GROUP allows future flexibility (e.g., NPCs could pass through each other but block players)
