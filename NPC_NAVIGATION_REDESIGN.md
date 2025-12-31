# NPC Navigation System Redesign

**Date:** 2025-12-31
**Status:** In Progress
**Goal:** Redesign from client-generated waypoints to server-provided waypoint lists with completion-based instruction generation

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Current Architecture Analysis](#current-architecture-analysis)
3. [Target Architecture](#target-architecture)
4. [Implementation Plan](#implementation-plan)
5. [Technical Details](#technical-details)
6. [Testing Strategy](#testing-strategy)

---

## Problem Statement

### Current Issues
From logs dated 2025-12-31:
```
[NPCObject] NPC npc_e2e3286a failed primary waypoint search, trying relaxed constraints...
[NPCObject] NPC npc_e2e3286a failed to find suitable waypoint - staying in place
[NPCObject] NPC npc_e2e3286a REACHED waypoint at (-5.1, 31.5, 5.0) - generating new waypoint...
```

**Root Cause:**
- NPC stuck at position (-5.1, 31.5, 5.0) with height 31.5
- Client-side waypoint generation uses ±2 unit height constraint
- Map has 207,273 tiles but most are at different heights
- All waypoint attempts fail validation (wrong height or too far)
- Fallback sets waypoint to current position (stuck in place)

### User Requirements
From conversation 2025-12-31:

> "My expectation is that the server will send a list of tiles to navigate to. For the time being, this list should be extremely simple to navigate to using only straight lines. On each client the NPC will navigate on it's own through each waypoint provided by the server and stop at the final one. Meanwhile the authoritative client for this NPC will be broadcasting the NPC's updated position. The server will use this broadcasted position to check whether the NPC is at the final waypoint in the list. If the NPC is at the end then it will send new instructions. If not, the server will continue waiting."

**Key Requirements:**
1. Server generates and sends waypoint list (not parameters for client generation)
2. Waypoints should be navigable with straight-line paths
3. Clients navigate through waypoints sequentially
4. Authoritative client broadcasts position
5. Server tracks position and detects completion
6. Server sends new instructions only when path completed (not timer-based)
7. Long paths (10-20+ waypoints) for wandering behavior

**User Selections:**
- Approach: Full redesign with server-provided waypoint lists
- Pathfinding: Simple straight-line paths
- Server State: Need NPC positions (also required for squishy collision)
- Path Length: Long paths (10-20+ waypoints)

---

## Current Architecture Analysis

### 1. Instruction Generation (Server-Side)

**File:** `NPCBehaviorAgent.java`
**Location:** `core/src/main/java/curly/octo/server/serverAgents/`

**Current Behavior (Lines 29-84):**
- **Timer-based:** Fixed 10-second interval (`INSTRUCTION_INTERVAL = 10.0f`)
- **No state tracking:** Server doesn't know NPC positions or completion status
- **Parameter-based:** Sends `mapWide=1.0f, speed=0.3f` parameters
- **Random instruction type:** 80% WANDER, 20% IDLE
- **Deterministic seed:** Sends `random.nextLong()` for client-side RNG

**Instruction Content:**
```java
NPCInstructionMessage instruction = new NPCInstructionMessage(
    npc.entityId,
    System.currentTimeMillis(),      // instructionId
    InstructionType.WANDER,          // type
    System.currentTimeMillis(),      // serverTimestamp
    INSTRUCTION_INTERVAL,            // duration (10s)
    random.nextLong()                // randomSeed
);
instruction.withParam("mapWide", 1.0f);
instruction.withParam("speed", 0.3f);
```

**Problem:** Server has no feedback loop - continuous timer ignores NPC state

---

### 2. Waypoint Generation (Client-Side)

**File:** `NPCObject.java`
**Location:** `core/src/main/java/curly/octo/common/`

**Current Behavior (Lines 277-391):**
- **Client-generated:** Each client independently generates waypoints
- **Deterministic:** Uses seeded Random from instruction for sync
- **Map-wide mode:** Picks random tiles from entire map (207,273 tiles)
- **Constraints:**
  - Height: ±2 units from current position
  - Distance: 10-30 units from current position
  - Walkability: Must pass `isPositionWalkable()` check

**Algorithm (Lines 310-354):**
```java
// Try up to 100 attempts (MAX_WAYPOINT_ATTEMPTS * 10)
for (int attempt = 0; attempt < 100; attempt++) {
    MapTile tile = allTiles.get(instructionRng.nextInt(allTiles.size()));

    float heightDiff = Math.abs(tile.y - currentHeight);
    if (heightDiff > 2.0f) continue;  // CONSTRAINT: same floor only

    float distance = position.dst(tile.x, tile.y, tile.z);
    if (distance < 10.0f || distance > 30.0f) continue;  // CONSTRAINT: mid-range

    if (gameMap.isPositionWalkable(tile.x, tile.y, tile.z)) {
        currentWaypoint.set(tile.x, tile.y, tile.z);
        return;  // SUCCESS
    }
}
// FALLBACK: Stay in place
currentWaypoint.set(position);
```

**Problem:** Constraints too strict for vertically-varied maps. No waypoint queue - only one at a time.

---

### 3. Navigation (Client-Side)

**File:** `NPCObject.java`
**Function:** `calculateWanderVelocity()` (Lines 546-575)

**Current Behavior:**
```java
// Check if reached waypoint
if (position.dst2(currentWaypoint) < 0.5f) {
    generateNewWaypoint();  // Generate next waypoint immediately
}

// Navigate toward waypoint
Vector3 direction = new Vector3(currentWaypoint).sub(position);
direction.y = 0;  // Horizontal only
direction.nor();
yaw = Math.toDegrees(Math.atan2(direction.x, direction.z));
return direction.scl(movementSpeed);  // Apply speed
```

**Physics Application (Lines 469-484):**
```java
characterController.setWalkDirection(walkVelocity);
position.set(ghostObject.getWorldTransform().getTranslation(tempVector));
```

**Problem:** No waypoint queue - generates one at a time on reaching each waypoint

---

### 4. Authority System

**File:** `NPCElectionManager.java`
**Location:** `core/src/main/java/curly/octo/server/`

**Election Algorithm (Lines 42-75):**
1. Get all active client profiles
2. Sort clients alphabetically by ClientUniqueId
3. First client becomes authority for all NPCs
4. Store in `npcAuthorities` HashMap

**Elections Triggered By:**
- NPC spawn (`NPCSpawnerAgent`, line 113)
- Client disconnect (lines 84-114)
- Manual trigger (future use)

**Authority Responsibilities:**
- Broadcast position every 5 seconds (ClientGameMode lines 88-89)
- Other clients receive sync corrections (lines 1105-1158)

---

### 5. Position Sync

**File:** `ClientGameMode.java`
**Broadcast:** `broadcastNPCSync()` (Lines 1039-1098)

**Current Behavior:**
- **Frequency:** Every 5 seconds (`NPC_SYNC_INTERVAL = 5.0f`)
- **Optimization:** Only broadcasts if moved > 0.05 units
- **Authority only:** Only managed NPCs broadcast
- **Message:** `NPCSyncMessage(npcId, position[x,y,z], yaw, timestamp)`
- **Relay:** Server forwards to all other clients (GameServer lines 178-185)

**Server Handling (GameServer.handleNPCSync(), Lines 178-185):**
```java
public void handleNPCSync(Connection connection, NPCSyncMessage syncMessage) {
    // Simple relay - NO PROCESSING OR STORAGE
    for (Connection conn : server.getConnections()) {
        if (readyClients.contains(conn.getID()) && conn.getID() != connection.getID()) {
            conn.sendUDP(syncMessage);  // Relay to all except sender
        }
    }
}
```

**Problem:** Server doesn't store or process position data - no completion tracking possible

---

### 6. Network Messages

**File:** `NPCInstructionMessage.java`
**Location:** `core/src/main/java/curly/octo/common/network/messages/`

**Current Structure (Lines 13-32):**
```java
public class NPCInstructionMessage {
    public String npcId;
    public long instructionId;
    public InstructionType type;              // IDLE, WANDER, PATROL, CHASE, CUSTOM_PATH
    public long serverTimestamp;
    public float duration;
    public long randomSeed;
    public HashMap<String, Float> params;     // LIMITATION: Float values only
}
```

**HashMap Usage:**
- `withParam("mapWide", 1.0f)` - flag for map-wide mode
- `withParam("speed", 0.3f)` - movement speed
- **Limitation:** Cannot store Vector3 or arrays

**Kryo Registration (NetworkMessageRegistry.java line 64):**
```java
registerMessage(kryo, NPCInstructionMessage.class);
registerMessage(kryo, NPCInstructionMessage.InstructionType.class);
kryo.register(java.util.HashMap.class);  // KryoNetwork.java line 76
```

**Problem:** Cannot send waypoint arrays without message structure change

---

### 7. Kryo Serialization Patterns

**File:** `KryoNetwork.java`

**Supported Types:**
```java
// Primitives and arrays
kryo.register(float[].class);           // Line 58 - WORKS
kryo.register(Vector3.class);           // Line 67 - WORKS
kryo.register(ArrayList.class);         // Line 75 - WORKS

// Critical config
kryo.setOptimizedGenerics(false);       // Line 52 - Prevents corruption
```

**Array Pattern (NPCSyncMessage.java lines 15-16):**
```java
/** Must initialize to prevent serialization errors */
public float[] position = new float[3];
```

**Best Practice:** Initialize arrays in field declaration, not constructor

---

## Target Architecture

### System Flow Diagram

```
SERVER                          CLIENT (Authority)              CLIENT (Observer)
------                          ------------------              -----------------

NPCSpawnerAgent
  └─> Spawns NPC
  └─> Election ──────────────> Receives election ───────────> Receives election
                                └─> Sets authority              └─> Observer mode

GameServer
  └─> NPC position tracking
      (receives sync broadcasts)

NPCBehaviorAgent
  └─> Generates waypoint list
      (10-20 waypoints)
      [using map data]
  └─> Creates instruction ────> Receives instruction ────────> Receives instruction
      └─> waypointData[]        └─> Loads into queue           └─> Loads into queue
                                    └─> Sets waypoint[0]           └─> Sets waypoint[0]

                                UPDATE LOOP:                    UPDATE LOOP:
                                ├─> Navigate to waypoint[i]     ├─> Navigate to waypoint[i]
                                ├─> Reached? i++                ├─> Reached? i++
                                └─> End? Stop                   └─> End? Stop

                                SYNC BROADCAST (5s):
                                └─> Send position to server ──> Server stores position
                                    └─> Relay to observers ──> Receives sync
                                                                └─> Apply correction

GameServer
  └─> Check if NPC reached
      final waypoint
      └─> YES: Trigger new
          instruction immediately
      └─> NO: Keep waiting

[Loop repeats when path completed]
```

### Key Differences from Current

| Aspect | Current | Target |
|--------|---------|--------|
| Waypoint Source | Client-generated | Server-generated |
| Waypoint Count | 1 at a time | 10-20 in queue |
| Server Position Tracking | None | HashMap storage |
| Instruction Timing | Fixed 10s timer | Completion-based |
| Feedback Loop | None | Server monitors completion |
| Path Complexity | Random single tiles | Straight-line multi-waypoint paths |

---

## Implementation Plan

### Phase 1: Network Message Updates

#### 1.1 Update NPCInstructionMessage
**File:** `core/src/main/java/curly/octo/common/network/messages/NPCInstructionMessage.java`

**Add field after line 32:**
```java
/** Waypoint data: [x1,y1,z1, x2,y2,z2, ..., xN,yN,zN] */
public float[] waypointData = new float[0];  // MUST initialize to prevent corruption
```

**Add convenience method:**
```java
public NPCInstructionMessage withWaypoints(java.util.List<com.badlogic.gdx.math.Vector3> waypoints) {
    this.waypointData = new float[waypoints.size() * 3];
    for (int i = 0; i < waypoints.size(); i++) {
        com.badlogic.gdx.math.Vector3 wp = waypoints.get(i);
        waypointData[i * 3] = wp.x;
        waypointData[i * 3 + 1] = wp.y;
        waypointData[i * 3 + 2] = wp.z;
    }
    return this;
}
```

**Mark deprecated:**
```java
@Deprecated
public HashMap<String, Float> params = new HashMap<>();  // Use waypointData instead
```

**Why float[] instead of Vector3[]:**
- Follows NPCSyncMessage pattern (proven safe)
- Simpler Kryo serialization
- Avoids Vector3[] registration complexity
- Must initialize in field declaration

---

### Phase 2: Server-Side Position Tracking

#### 2.1 Add NPC Position Storage
**File:** `core/src/main/java/curly/octo/server/GameServer.java`

**Add after line 42 (after connectionToPlayerMap):**
```java
/** Tracks current positions of all NPCs (updated via sync broadcasts) */
private final HashMap<String, Vector3> npcPositions = new HashMap<>();

/** Tracks last position update timestamp per NPC */
private final HashMap<String, Long> npcLastUpdateTime = new HashMap<>();
```

#### 2.2 Update handleNPCSync()
**Replace lines 178-185:**
```java
public void handleNPCSync(Connection connection, NPCSyncMessage syncMessage) {
    // 1. STORE NPC POSITION ON SERVER (new functionality)
    Vector3 pos = new Vector3(
        syncMessage.position[0],
        syncMessage.position[1],
        syncMessage.position[2]
    );
    npcPositions.put(syncMessage.npcId, pos);
    npcLastUpdateTime.put(syncMessage.npcId, System.currentTimeMillis());

    Log.debug("GameServer", "Updated NPC " + syncMessage.npcId +
              " position: " + String.format("(%.1f, %.1f, %.1f)", pos.x, pos.y, pos.z));

    // 2. RELAY TO ALL CLIENTS EXCEPT SENDER (existing behavior)
    for (Connection conn : server.getConnections()) {
        if (readyClients.contains(conn.getID()) && conn.getID() != connection.getID()) {
            conn.sendUDP(syncMessage);
        }
    }

    // 3. CHECK IF NPC COMPLETED PATH (new functionality)
    checkNPCCompletion(syncMessage.npcId, pos);
}
```

#### 2.3 Add Helper Methods
**Add to GameServer.java:**
```java
/**
 * Get the last known position of an NPC.
 * @param npcId NPC entity ID
 * @return Position vector, or null if not tracked
 */
public Vector3 getNPCPosition(String npcId) {
    return npcPositions.get(npcId);
}

/**
 * Remove NPC from position tracking (called when NPC despawns).
 * @param npcId NPC entity ID
 */
public void removeNPCPosition(String npcId) {
    npcPositions.remove(npcId);
    npcLastUpdateTime.remove(npcId);
}
```

---

### Phase 3: Server-Side Waypoint Generation

#### 3.1 Add Map Reference to NPCBehaviorAgent
**File:** `core/src/main/java/curly/octo/server/serverAgents/NPCBehaviorAgent.java`

**Add field after line 19:**
```java
private final curly.octo.common.map.GameMap mapManager;
```

**Update constructor (line 23):**
```java
public NPCBehaviorAgent(ServerGameObjectManager objectManager, curly.octo.common.map.GameMap mapManager) {
    super(objectManager);
    this.random = new Random();
    this.mapManager = mapManager;
}
```

**Update ServerCoordinator instantiation:**
**File:** `core/src/main/java/curly/octo/server/ServerCoordinator.java`
**Find NPCBehaviorAgent construction and pass mapManager:**
```java
npcBehaviorAgent = new NPCBehaviorAgent(objectManager, mapManager);
```

#### 3.2 Add Instruction Tracking
**Add to NPCBehaviorAgent after line 21:**
```java
/** Tracks active instructions per NPC for completion checking */
private final HashMap<String, NPCInstructionMessage> activeInstructions = new HashMap<>();
```

**Add methods:**
```java
/**
 * Get the current active instruction for an NPC.
 */
public NPCInstructionMessage getCurrentInstruction(String npcId) {
    return activeInstructions.get(npcId);
}

/**
 * Generate immediate instruction for NPC (triggered by completion or spawn).
 */
public void generateImmediateInstruction(String npcId, Vector3 currentPos) {
    curly.octo.common.WorldObject obj = objectManager.getObjectById(npcId);
    if (obj instanceof curly.octo.common.NPCObject) {
        curly.octo.common.NPCObject npc = (curly.octo.common.NPCObject) obj;
        generateInstructionForNPC(npc, currentPos);
    }
}
```

#### 3.3 Implement Waypoint List Generation
**Add helper method to NPCBehaviorAgent:**
```java
/**
 * Generate a list of waypoints for simple straight-line path.
 * Creates 10-20 waypoints from current position to random destination.
 *
 * @param npcId NPC entity ID
 * @param startPos Current NPC position
 * @return List of waypoints (empty if generation fails)
 */
private java.util.List<com.badlogic.gdx.math.Vector3> generateWaypointList(String npcId, com.badlogic.gdx.math.Vector3 startPos) {
    java.util.List<com.badlogic.gdx.math.Vector3> waypoints = new java.util.ArrayList<>();

    if (mapManager == null) {
        Log.error("NPCBehaviorAgent", "Cannot generate waypoints - no map reference");
        return waypoints;
    }

    // Get all floor tiles at same height as NPC (±5 units for some flexibility)
    float currentHeight = startPos.y;
    java.util.ArrayList<curly.octo.common.map.MapTile> candidateTiles = new java.util.ArrayList<>();

    for (curly.octo.common.map.MapTile tile : mapManager.getAllTiles()) {
        // Filter for walkable tiles at similar height
        float heightDiff = Math.abs(tile.y - currentHeight);
        if (heightDiff <= 5.0f &&
            tile.geometryType != curly.octo.common.map.MapTileGeometryType.EMPTY) {
            candidateTiles.add(tile);
        }
    }

    if (candidateTiles.isEmpty()) {
        Log.warn("NPCBehaviorAgent", "No candidate tiles found for NPC " + npcId);
        return waypoints;
    }

    // Pick random destination tile
    curly.octo.common.map.MapTile destTile = candidateTiles.get(random.nextInt(candidateTiles.size()));
    com.badlogic.gdx.math.Vector3 destination = new com.badlogic.gdx.math.Vector3(
        destTile.x,
        destTile.y,
        destTile.z
    );

    // Generate 10-20 waypoints along straight line
    int numWaypoints = 10 + random.nextInt(11);  // Random 10-20

    for (int i = 1; i <= numWaypoints; i++) {
        float t = (float) i / numWaypoints;  // Interpolation factor

        com.badlogic.gdx.math.Vector3 waypoint = new com.badlogic.gdx.math.Vector3(
            startPos.x + (destination.x - startPos.x) * t,
            startPos.y,  // Keep at same height for simplicity
            startPos.z + (destination.z - startPos.z) * t
        );

        // Optional: Validate waypoint is walkable
        if (mapManager.isPositionWalkable(waypoint.x, waypoint.y, waypoint.z)) {
            waypoints.add(waypoint);
        } else {
            Log.debug("NPCBehaviorAgent", "Skipped non-walkable waypoint at " + waypoint);
        }
    }

    Log.info("NPCBehaviorAgent", "Generated " + waypoints.size() + " waypoints for NPC " + npcId +
             " from " + startPos + " to " + destination);

    return waypoints;
}
```

#### 3.4 Modify generateInstructionForNPC()
**Replace lines 54-84 with:**
```java
private void generateInstructionForNPC(curly.octo.common.NPCObject npc, com.badlogic.gdx.math.Vector3 currentPos) {
    // Use provided position or fall back to NPC's stored position
    com.badlogic.gdx.math.Vector3 startPos = currentPos != null ? currentPos : npc.getPosition();

    if (startPos == null) {
        Log.error("NPCBehaviorAgent", "Cannot generate instruction - NPC has no position");
        return;
    }

    // Generate waypoint list
    java.util.List<com.badlogic.gdx.math.Vector3> waypoints = generateWaypointList(npc.entityId, startPos);

    if (waypoints.isEmpty()) {
        Log.warn("NPCBehaviorAgent", "Failed to generate waypoints for NPC " + npc.entityId);
        return;
    }

    // Create instruction with waypoint list
    NPCInstructionMessage instruction = new NPCInstructionMessage(
        npc.entityId,
        System.currentTimeMillis(),  // instructionId
        NPCInstructionMessage.InstructionType.WANDER,
        System.currentTimeMillis(),  // serverTimestamp
        999999.0f,                   // duration - effectively infinite (completion-based now)
        random.nextLong()            // randomSeed (kept for future use)
    );

    // Add waypoint data
    instruction.withWaypoints(waypoints);

    // Store as active instruction
    activeInstructions.put(npc.entityId, instruction);

    // Broadcast to all clients
    curly.octo.common.network.NetworkManager.sendToAllClients(instruction);

    Log.info("NPCBehaviorAgent", "Sent path with " + waypoints.size() +
             " waypoints to NPC " + npc.entityId);
}
```

#### 3.5 Update Timer-Based Generation
**Modify update() method (lines 29-36):**
```java
@Override
public void update(float deltaTime) {
    instructionTimer += deltaTime;

    // Keep timer as FALLBACK only (in case completion detection fails)
    if (instructionTimer >= INSTRUCTION_INTERVAL * 3) {  // 30 seconds fallback
        instructionTimer = 0f;
        Log.warn("NPCBehaviorAgent", "Fallback timer triggered - regenerating all NPC instructions");
        generateInstructions();
    }
}

private void generateInstructions() {
    for (curly.octo.common.WorldObject obj : objectManager.getNPCs()) {
        if (obj instanceof curly.octo.common.NPCObject) {
            curly.octo.common.NPCObject npc = (curly.octo.common.NPCObject) obj;
            // Use null position to force using NPC's current position
            generateInstructionForNPC(npc, null);
        }
    }
}
```

#### 3.6 Update Initial Instruction
**Modify generateInitialInstruction() (lines 89-91):**
```java
public void generateInitialInstruction(curly.octo.common.NPCObject npc) {
    generateInstructionForNPC(npc, npc.getPosition());
}
```

---

### Phase 4: Client-Side Waypoint Queue Navigation

#### 4.1 Add Waypoint Queue to NPCObject
**File:** `core/src/main/java/curly/octo/common/NPCObject.java`

**Add fields after line 37 (after wanderRadius):**
```java
// Waypoint queue navigation (server-provided paths)
private transient java.util.List<Vector3> waypointQueue;
private transient int currentWaypointIndex;
private transient Vector3 targetWaypoint;
```

**Update initializeTransientFields() (lines 93-102):**
```java
private void initializeTransientFields() {
    this.currentWaypoint = new Vector3();
    this.wanderCenter = new Vector3();
    this.lastSyncedPosition = new Vector3();
    this.targetPosition = new Vector3();
    this.lastPosition = new Vector3();
    this.interpolationAlpha = 1.0f;
    this.movementSpeed = 0.3f;
    this.stuckTimer = 0f;

    // Waypoint queue (new)
    this.waypointQueue = new java.util.ArrayList<>();
    this.currentWaypointIndex = 0;
    this.targetWaypoint = new Vector3();
}
```

#### 4.2 Modify executeInstruction()
**Replace WANDER case (lines 221-242) with:**
```java
case WANDER:
    // Extract waypoint list from instruction
    waypointQueue.clear();
    currentWaypointIndex = 0;

    float[] waypointData = instruction.waypointData;
    if (waypointData != null && waypointData.length > 0) {
        // Parse waypoint data: [x1,y1,z1, x2,y2,z2, ...]
        for (int i = 0; i < waypointData.length; i += 3) {
            if (i + 2 < waypointData.length) {  // Ensure we have x,y,z
                Vector3 waypoint = new Vector3(
                    waypointData[i],
                    waypointData[i + 1],
                    waypointData[i + 2]
                );
                waypointQueue.add(waypoint);
            }
        }

        if (!waypointQueue.isEmpty()) {
            targetWaypoint.set(waypointQueue.get(0));
            movementSpeed = instruction.params.getOrDefault("speed", 0.3f);

            Log.info("NPCObject", "NPC " + entityId + " received path with " +
                     waypointQueue.size() + " waypoints");
            Log.info("NPCObject", "  First waypoint: " +
                     String.format("(%.1f, %.1f, %.1f)",
                         targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
        } else {
            Log.warn("NPCObject", "NPC " + entityId + " received empty waypoint list");
        }
    } else {
        Log.warn("NPCObject", "NPC " + entityId + " received WANDER instruction with no waypoint data");
    }
    break;
```

#### 4.3 Replace calculateWanderVelocity()
**Replace lines 546-575 with:**
```java
/**
 * Calculate wander velocity for physics-based movement.
 * Navigates through waypoint queue provided by server.
 *
 * @param delta Time delta
 * @return Velocity vector for character controller
 */
private Vector3 calculateWanderVelocity(float delta) {
    if (position == null || targetWaypoint == null || waypointQueue.isEmpty()) {
        return new Vector3(0, 0, 0);  // No path to follow
    }

    // Check if reached current target waypoint
    float distanceToWaypoint = position.dst(targetWaypoint);
    if (distanceToWaypoint < 0.5f) {
        // Advance to next waypoint in queue
        currentWaypointIndex++;

        if (currentWaypointIndex < waypointQueue.size()) {
            // More waypoints to go
            targetWaypoint.set(waypointQueue.get(currentWaypointIndex));

            Log.info("NPCObject", "NPC " + entityId + " reached waypoint " +
                     currentWaypointIndex + "/" + waypointQueue.size() +
                     " - next target: " + String.format("(%.1f, %.1f, %.1f)",
                         targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
        } else {
            // Reached end of path - STOP
            Log.info("NPCObject", "NPC " + entityId + " COMPLETED full path (" +
                     waypointQueue.size() + " waypoints)");
            return new Vector3(0, 0, 0);  // Stop moving
        }
    }

    // Calculate direction toward current target waypoint
    Vector3 direction = new Vector3(targetWaypoint).sub(position);
    direction.y = 0;  // Keep movement horizontal (no flying)

    if (direction.len2() > 0.01f) {
        direction.nor();

        // Update yaw to face movement direction
        yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));

        // Return velocity (units per second) for character controller
        return direction.scl(movementSpeed);
    }

    return new Vector3(0, 0, 0);
}
```

#### 4.4 Remove Old Waypoint Generation
**Delete these methods (no longer needed):**
- `generateNewWaypoint()` (lines 260-275)
- `generateMapWideWaypoint()` (lines 277-391)
- `generateRadiusWaypoint()` (lines 374-407)

**Remove gameMap field (line 59):**
```java
// DELETE: private transient curly.octo.common.map.GameMap gameMap;
```

**Remove setGameMap() method (lines 636-638):**
```java
// DELETE: public void setGameMap(curly.octo.common.map.GameMap gameMap) { ... }
```

#### 4.5 Add Getters for Debug Rendering
**Add after getCurrentInstruction() (line 672):**
```java
/**
 * Get the waypoint queue for debug visualization.
 * @return List of waypoints, or null if none
 */
public java.util.List<Vector3> getWaypointQueue() {
    return waypointQueue;
}

/**
 * Get current waypoint index in queue.
 * @return Index (0-based)
 */
public int getCurrentWaypointIndex() {
    return currentWaypointIndex;
}

/**
 * Get the current target waypoint being navigated to.
 * @return Target waypoint vector
 */
public Vector3 getTargetWaypoint() {
    return targetWaypoint;
}
```

---

### Phase 5: Server Completion Detection

#### 5.1 Add Completion Checking to GameServer
**File:** `core/src/main/java/curly/octo/server/GameServer.java`

**Add method after handleNPCSync():**
```java
/**
 * Check if NPC has completed its current path and trigger new instruction if so.
 * Called after each position sync update.
 *
 * @param npcId NPC entity ID
 * @param currentPos Current position from sync message
 */
private void checkNPCCompletion(String npcId, Vector3 currentPos) {
    // Get NPC's current instruction from behavior agent
    NPCInstructionMessage instruction = npcBehaviorAgent.getCurrentInstruction(npcId);

    if (instruction == null) {
        return;  // No active instruction
    }

    if (instruction.waypointData == null || instruction.waypointData.length < 3) {
        return;  // No waypoints in instruction
    }

    // Extract final waypoint from instruction (last 3 floats)
    int lastIndex = instruction.waypointData.length - 3;
    Vector3 finalWaypoint = new Vector3(
        instruction.waypointData[lastIndex],
        instruction.waypointData[lastIndex + 1],
        instruction.waypointData[lastIndex + 2]
    );

    // Check if NPC is within completion threshold of final waypoint
    float distance = currentPos.dst(finalWaypoint);
    float COMPLETION_THRESHOLD = 1.0f;  // 1 unit tolerance

    if (distance < COMPLETION_THRESHOLD) {
        // NPC COMPLETED PATH - generate new instruction immediately
        Log.info("GameServer", "NPC " + npcId + " completed path (distance to final: " +
                 String.format("%.2f", distance) + ") - generating new instruction");

        npcBehaviorAgent.generateImmediateInstruction(npcId, currentPos);
    }
}
```

**Note:** This method is already called in the updated handleNPCSync() from Phase 2.2

#### 5.2 Add NPCBehaviorAgent Reference
**File:** `core/src/main/java/curly/octo/server/GameServer.java`

**Add field after line 41:**
```java
private NPCBehaviorAgent npcBehaviorAgent;
```

**Add setter method:**
```java
/**
 * Set the NPC behavior agent for completion-based instruction generation.
 * Called by ServerCoordinator during initialization.
 */
public void setNPCBehaviorAgent(NPCBehaviorAgent agent) {
    this.npcBehaviorAgent = agent;
}
```

**Update ServerCoordinator:**
**File:** `core/src/main/java/curly/octo/server/ServerCoordinator.java`

**Find where GameServer is created and add after NPCBehaviorAgent construction:**
```java
// After: npcBehaviorAgent = new NPCBehaviorAgent(objectManager, mapManager);
gameServer.setNPCBehaviorAgent(npcBehaviorAgent);
```

---

### Phase 6: Fix Debug Rendering

#### 6.1 Reduce Logging Verbosity
**File:** `core/src/main/java/curly/octo/client/rendering/debug/DebugRenderer.java`

**Replace lines 332-394 with:**
```java
public void renderNPCPaths(Camera camera, java.util.ArrayList<curly.octo.common.NPCObject> npcs) {
    if (npcs == null || npcs.isEmpty()) {
        return;
    }

    // REMOVED: Per-frame logging (too spammy)

    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    shapeRenderer.setProjectionMatrix(camera.combined);
    shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

    // Color scheme for NPC visualization
    Color pathColor = new Color(0.2f, 1.0f, 0.2f, 1.0f);        // Bright green
    Color waypointColor = new Color(1.0f, 1.0f, 0.0f, 1.0f);   // Yellow
    Color completedColor = new Color(0.5f, 0.5f, 0.5f, 0.5f);  // Gray (passed waypoints)

    for (curly.octo.common.NPCObject npc : npcs) {
        if (npc == null || npc.getPosition() == null) {
            continue;
        }

        Vector3 npcPos = npc.getPosition();
        java.util.List<Vector3> waypointQueue = npc.getWaypointQueue();
        int currentIndex = npc.getCurrentWaypointIndex();

        // Draw waypoint queue as connected path
        if (waypointQueue != null && !waypointQueue.isEmpty()) {
            Vector3 prev = npcPos;

            for (int i = 0; i < waypointQueue.size(); i++) {
                Vector3 wp = waypointQueue.get(i);

                // Choose color based on whether waypoint is passed or upcoming
                if (i < currentIndex) {
                    shapeRenderer.setColor(completedColor);  // Already passed
                } else {
                    shapeRenderer.setColor(pathColor);       // Upcoming
                }

                // Draw line segment
                shapeRenderer.line(prev, wp);

                // Draw waypoint sphere
                if (i >= currentIndex) {
                    shapeRenderer.setColor(waypointColor);
                    drawSphereWireframe(wp, 0.3f, 8);
                }

                prev = wp;
            }
        }
    }

    shapeRenderer.end();
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
}
```

#### 6.2 Remove Logging from ClientGameWorld
**File:** `core/src/main/java/curly/octo/client/ClientGameWorld.java`

**Remove lines 320-321 and 325:**
```java
// DELETE: com.esotericsoftware.minlog.Log.info("ClientGameWorld", "Found " + npcs.size() + ...
// DELETE: com.esotericsoftware.minlog.Log.warn("ClientGameWorld", "No NPCs found for path rendering");
```

**Keep only:**
```java
// Render NPC paths (waypoints, wander zones)
java.util.ArrayList<curly.octo.common.NPCObject> npcs = new java.util.ArrayList<>();
for (curly.octo.common.GameObject obj : getGameObjectManager().getAllObjects()) {
    if (obj instanceof curly.octo.common.NPCObject) {
        npcs.add((curly.octo.common.NPCObject) obj);
    }
}
if (!npcs.isEmpty()) {
    mapRenderer.getDebugRenderer().renderNPCPaths(camera, npcs);
}
```

---

## Testing Strategy

### Build and Compile
```bash
./gradlew.bat compileJava
./gradlew.bat build
```

**Expected:** Clean build with no errors

### Test 1: Server Startup and NPC Spawn
**Actions:**
1. Start server
2. Connect client
3. Observe NPC spawn

**Expected Logs:**
```
[NPCBehaviorAgent] Generated 15 waypoints for NPC npc_abc123 from (10, 30, 5) to (45, 30, 25)
[NPCBehaviorAgent] Sent path with 15 waypoints to NPC npc_abc123
```

### Test 2: Client Receives Waypoint List
**Actions:**
1. Client receives instruction
2. Check logs for waypoint count

**Expected Logs:**
```
[ClientGameMode] === RECEIVED NPCInstructionMessage ===
[ClientGameMode] NPC: npc_abc123, Type: WANDER, Duration: 999999.0, InstructionID: ...
[NPCObject] NPC npc_abc123 received path with 15 waypoints
[NPCObject]   First waypoint: (12.5, 30.0, 6.8)
```

### Test 3: NPC Navigation
**Actions:**
1. Observe NPC movement
2. Watch waypoint progress in logs

**Expected Logs:**
```
[NPCObject] NPC npc_abc123 reached waypoint 1/15 - next target: (15.0, 30.0, 8.5)
[NPCObject] NPC npc_abc123 reached waypoint 2/15 - next target: (17.5, 30.0, 10.3)
...
[NPCObject] NPC npc_abc123 COMPLETED full path (15 waypoints)
```

### Test 4: Debug Visualization (F3)
**Actions:**
1. Press F3 to enable character debug mode
2. Look for green lines and yellow spheres

**Expected:**
- Green lines connecting waypoints
- Yellow spheres at each waypoint
- Gray lines for completed segments
- Path should update as NPC progresses

### Test 5: Server Completion Detection
**Actions:**
1. Wait for NPC to reach final waypoint
2. Check server logs for completion

**Expected Logs:**
```
[GameServer] NPC npc_abc123 completed path (distance to final: 0.3) - generating new instruction
[NPCBehaviorAgent] Generated 12 waypoints for NPC npc_abc123 from (45, 30, 25) to (20, 30, 60)
[NPCBehaviorAgent] Sent path with 12 waypoints to NPC npc_abc123
```

### Test 6: Multi-Client Sync
**Actions:**
1. Connect second client
2. Verify both see same NPC behavior

**Expected:**
- Both clients see NPC following same path
- Waypoint visualization identical on both
- Sync corrections keep position aligned (< 2 unit drift)

### Test 7: Fallback Timer
**Actions:**
1. Let server run for 30 seconds without movement
2. Check for fallback timer warning

**Expected Logs:**
```
[NPCBehaviorAgent] Fallback timer triggered - regenerating all NPC instructions
```

---

## Rollback Plan

If critical issues occur during testing:

### Quick Fixes
1. **Compilation errors:** Comment out new code, restore old methods temporarily
2. **NPC not moving:** Check waypointData array length and parsing
3. **Server crash:** Disable checkNPCCompletion() call

### Full Rollback
1. Restore old timer-based approach:
   - Revert NPCBehaviorAgent.update() to 10s timer
   - Re-enable client-side waypoint generation
   - Remove server completion checking

2. Keep position tracking:
   - Server position storage is non-breaking
   - Can be used for future squishy collision

3. Git branch:
   - All changes on `npc-navigation-redesign` branch
   - Can checkout `main` to restore previous version

---

## Implementation Checklist

### Phase 1: Network Messages
- [ ] Add `waypointData` field to NPCInstructionMessage
- [ ] Add `withWaypoints()` convenience method
- [ ] Mark `params` as deprecated
- [ ] Test Kryo serialization

### Phase 2: Server Position Tracking
- [ ] Add `npcPositions` HashMap to GameServer
- [ ] Update `handleNPCSync()` to store positions
- [ ] Add `getNPCPosition()` helper
- [ ] Add `removeNPCPosition()` cleanup

### Phase 3: Server Waypoint Generation
- [ ] Add `mapManager` field to NPCBehaviorAgent
- [ ] Update constructor to accept mapManager
- [ ] Add `activeInstructions` tracking
- [ ] Implement `generateWaypointList()` method
- [ ] Update `generateInstructionForNPC()` to use waypoints
- [ ] Modify timer to 30s fallback
- [ ] Update ServerCoordinator to pass mapManager

### Phase 4: Client Waypoint Queue
- [ ] Add waypoint queue fields to NPCObject
- [ ] Initialize in `initializeTransientFields()`
- [ ] Update `executeInstruction()` to parse waypoint array
- [ ] Replace `calculateWanderVelocity()` with queue navigation
- [ ] Delete old waypoint generation methods
- [ ] Remove `gameMap` reference
- [ ] Add getter methods for debug rendering

### Phase 5: Completion Detection
- [ ] Add `checkNPCCompletion()` to GameServer
- [ ] Add `npcBehaviorAgent` reference to GameServer
- [ ] Add setter method
- [ ] Update ServerCoordinator to wire agent reference

### Phase 6: Debug Rendering
- [ ] Update `renderNPCPaths()` to use waypoint queue
- [ ] Remove verbose logging from DebugRenderer
- [ ] Remove logging from ClientGameWorld
- [ ] Test visualization with F3

### Phase 7: Testing
- [ ] Compile and build
- [ ] Test server startup
- [ ] Test client receives waypoints
- [ ] Test NPC navigation
- [ ] Test debug visualization
- [ ] Test completion detection
- [ ] Test multi-client sync
- [ ] Test fallback timer

---

## Known Issues and Future Enhancements

### Known Limitations
1. **Simple pathfinding:** Straight-line paths may hit obstacles
2. **No obstacle avoidance:** NPC may get stuck on complex terrain
3. **Height restriction:** Only picks destinations at similar height
4. **No A* pathfinding:** May not find optimal routes

### Future Enhancements
1. **A* pathfinding:** Implement server-side A* for obstacle-free paths
2. **Multi-floor navigation:** Support staircases and height changes
3. **Dynamic waypoints:** Adjust path in response to obstacles
4. **Behavior types:** Different pathfinding for PATROL vs WANDER vs CHASE
5. **Squishy collision:** Use NPC position tracking for player-NPC push mechanics
6. **Position validation:** Server arbitrates conflicting position updates
7. **Stale position cleanup:** Remove positions for disconnected clients

---

## References

### Files Modified
1. `core/src/main/java/curly/octo/common/network/messages/NPCInstructionMessage.java`
2. `core/src/main/java/curly/octo/server/GameServer.java`
3. `core/src/main/java/curly/octo/server/serverAgents/NPCBehaviorAgent.java`
4. `core/src/main/java/curly/octo/common/NPCObject.java`
5. `core/src/main/java/curly/octo/client/rendering/debug/DebugRenderer.java`
6. `core/src/main/java/curly/octo/client/ClientGameWorld.java`
7. `core/src/main/java/curly/octo/server/ServerCoordinator.java`

### Key Concepts
- **Waypoint Queue:** Sequential navigation through server-provided waypoints
- **Completion-Based Instructions:** Server monitors progress and sends new paths when done
- **Server Position Tracking:** Server maintains NPC positions for completion detection
- **Straight-Line Pathfinding:** Simple interpolation between start and destination
- **Authority System:** One client broadcasts position, others follow via sync corrections

### Related Documentation
- `CLAUDE.md` - Project overview and architecture
- `README.md` - Build and run instructions
- Network protocol documentation (future)

---

**End of Document**
