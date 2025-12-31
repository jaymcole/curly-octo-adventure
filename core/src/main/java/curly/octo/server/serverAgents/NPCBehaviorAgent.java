package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.NPCObject;
import curly.octo.common.WorldObject;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.NPCInstructionMessage;
import curly.octo.server.ServerGameObjectManager;

import java.util.Random;

/**
 * Server-side agent that generates and broadcasts NPC behavior instructions.
 * Clients execute these instructions deterministically.
 */
public class NPCBehaviorAgent extends BaseAgent {

    private final Random random;
    private float instructionTimer = 0f;
    private static final float INSTRUCTION_INTERVAL = 5.0f; // Fallback timer (was: primary timer)
    private final curly.octo.common.map.GameMap mapManager;
    private final java.util.HashMap<String, NPCInstructionMessage> activeInstructions = new java.util.HashMap<>();

    public NPCBehaviorAgent(ServerGameObjectManager objectManager, curly.octo.common.map.GameMap mapManager) {
        super(objectManager);
        this.random = new Random();
        this.mapManager = mapManager;
    }

    @Override
    public void update(float deltaTime) {
        instructionTimer += deltaTime;

        // Fallback timer only (in case completion detection fails)
        // Primary instruction generation is now completion-based (triggered from GameServer)
        if (instructionTimer >= INSTRUCTION_INTERVAL * 3) {  // 30 seconds fallback
            instructionTimer = 0f;
            Log.warn("NPCBehaviorAgent", "Fallback timer triggered - regenerating all NPC instructions");
            generateInstructions();
        }
    }

    /**
     * Generate and broadcast instructions for all NPCs.
     * Used by fallback timer only.
     */
    private void generateInstructions() {
        for (WorldObject obj : objectManager.getNPCs()) {
            if (obj instanceof NPCObject) {
                NPCObject npc = (NPCObject) obj;
                // Use null position to force using NPC's current position
                generateInstructionForNPC(npc, null);
            }
        }
    }

    /**
     * Generate a waypoint-based instruction for a specific NPC.
     *
     * @param npc The NPC object
     * @param currentPos Current position (from server tracking), or null to use NPC's position
     */
    private void generateInstructionForNPC(NPCObject npc, Vector3 currentPos) {
        // Use provided position or fall back to NPC's stored position
        Vector3 startPos = currentPos != null ? currentPos : npc.getPosition();

        if (startPos == null) {
            Log.error("NPCBehaviorAgent", "Cannot generate instruction - NPC " + npc.entityId + " has no position");
            return;
        }

        // Generate waypoint list as tile indices (10-20 waypoints along straight path)
        java.util.List<int[]> waypointTileIndices = generateWaypointTileIndices(npc.entityId, startPos);

        if (waypointTileIndices.isEmpty()) {
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

        // Add waypoint tile indices
        instruction.withWaypointTileIndices(waypointTileIndices);

        // Keep speed param for backward compatibility
        instruction.withParam("speed", 0.3f);

        // Store as active instruction
        activeInstructions.put(npc.entityId, instruction);

        // DIAGNOSTIC: Log waypoint data array
        Log.info("NPCBehaviorAgent", "DIAGNOSTIC - waypointTileIndices array length: " +
                 instruction.waypointTileIndices.length + " (expected: " + (waypointTileIndices.size() * 3) + ")");
        if (waypointTileIndices.size() > 0) {
            int[] first = waypointTileIndices.get(0);
            int[] last = waypointTileIndices.get(waypointTileIndices.size() - 1);
            Log.info("NPCBehaviorAgent", "  First waypoint tile: (" + first[0] + ", " + first[1] + ", " + first[2] + ")");
            Log.info("NPCBehaviorAgent", "  Last waypoint tile: (" + last[0] + ", " + last[1] + ", " + last[2] + ")");
        }

        // Broadcast to all clients
        NetworkManager.sendToAllClients(instruction);

        Log.info("NPCBehaviorAgent", "Sent path with " + waypointTileIndices.size() +
                 " waypoint tiles to NPC " + npc.entityId + " (speed: 0.3)");
    }

    /**
     * Generate a list of waypoint tile indices using grid-based pathfinding.
     * Creates waypoints for each tile step to prevent diagonal movement.
     * Chooses a far destination on the other side of the map.
     *
     * @param npcId NPC entity ID (for logging)
     * @param startPos Current NPC world position
     * @return List of tile index triplets [x,y,z] (empty if generation fails)
     */
    private java.util.List<int[]> generateWaypointTileIndices(String npcId, Vector3 startPos) {
        java.util.List<int[]> waypointTiles = new java.util.ArrayList<>();

        if (mapManager == null) {
            Log.error("NPCBehaviorAgent", "Cannot generate waypoints - no map reference");
            return waypointTiles;
        }

        // Convert start position to tile indices
        int startTileX = (int)(startPos.x / curly.octo.common.Constants.MAP_TILE_SIZE);
        int startTileY = (int)(startPos.y / curly.octo.common.Constants.MAP_TILE_SIZE);
        int startTileZ = (int)(startPos.z / curly.octo.common.Constants.MAP_TILE_SIZE);

        // Get all floor tiles at same height as NPC (±2 tile indices for flexibility)
        java.util.ArrayList<curly.octo.common.map.MapTile> candidateTiles = new java.util.ArrayList<>();

        for (curly.octo.common.map.MapTile tile : mapManager.getAllTiles()) {
            int tileY = (int)(tile.y / curly.octo.common.Constants.MAP_TILE_SIZE);
            int heightDiff = Math.abs(tileY - startTileY);

            if (heightDiff <= 2 &&
                tile.geometryType != curly.octo.common.map.enums.MapTileGeometryType.EMPTY) {
                candidateTiles.add(tile);
            }
        }

        if (candidateTiles.isEmpty()) {
            Log.warn("NPCBehaviorAgent", "No candidate tiles found for NPC " + npcId +
                     " at tile height " + startTileY);
            return waypointTiles;
        }

        // Find a far destination: sort candidates by Manhattan distance and pick from the farthest 25%
        candidateTiles.sort((a, b) -> {
            int aTileX = (int)(a.x / curly.octo.common.Constants.MAP_TILE_SIZE);
            int aTileZ = (int)(a.z / curly.octo.common.Constants.MAP_TILE_SIZE);
            int bTileX = (int)(b.x / curly.octo.common.Constants.MAP_TILE_SIZE);
            int bTileZ = (int)(b.z / curly.octo.common.Constants.MAP_TILE_SIZE);

            int distA = Math.abs(aTileX - startTileX) + Math.abs(aTileZ - startTileZ);
            int distB = Math.abs(bTileX - startTileX) + Math.abs(bTileZ - startTileZ);

            return Integer.compare(distB, distA);  // Sort descending (farthest first)
        });

        // Pick from the farthest 25% of tiles
        int farTilePoolSize = Math.max(1, candidateTiles.size() / 4);
        curly.octo.common.map.MapTile destTile = candidateTiles.get(random.nextInt(farTilePoolSize));

        int destTileX = (int)(destTile.x / curly.octo.common.Constants.MAP_TILE_SIZE);
        int destTileY = (int)(destTile.y / curly.octo.common.Constants.MAP_TILE_SIZE);
        int destTileZ = (int)(destTile.z / curly.octo.common.Constants.MAP_TILE_SIZE);

        int manhattanDistance = Math.abs(destTileX - startTileX) + Math.abs(destTileZ - startTileZ);
        Log.info("NPCBehaviorAgent", "🎯 NEW PATH for " + npcId + ": tile [" +
                 startTileX + "," + startTileY + "," + startTileZ + "] → [" +
                 destTileX + "," + destTileY + "," + destTileZ + "] (distance: " +
                 manhattanDistance + " tiles)");

        // Generate grid-based path (Manhattan-style, no diagonals)
        // Move along X axis, then Z axis (or randomly alternate for variety)
        int currentX = startTileX;
        int currentY = startTileY;
        int currentZ = startTileZ;

        // Randomly decide whether to move X-first or Z-first for path variety
        boolean xFirst = random.nextBoolean();

        while (currentX != destTileX || currentZ != destTileZ) {
            // Alternate between X and Z movement based on strategy
            boolean moveX = false;

            if (xFirst) {
                // X-first strategy: move X until aligned, then move Z
                moveX = (currentX != destTileX);
            } else {
                // Z-first strategy: move Z until aligned, then move X
                moveX = (currentZ == destTileZ) && (currentX != destTileX);
            }

            if (moveX) {
                // Move one step along X axis
                currentX += (destTileX > currentX) ? 1 : -1;
            } else {
                // Move one step along Z axis
                currentZ += (destTileZ > currentZ) ? 1 : -1;
            }

            // Convert to world position for walkability check
            float worldX = currentX * curly.octo.common.Constants.MAP_TILE_SIZE;
            float worldY = currentY * curly.octo.common.Constants.MAP_TILE_SIZE;
            float worldZ = currentZ * curly.octo.common.Constants.MAP_TILE_SIZE;

            // Validate waypoint is walkable
            if (mapManager.isPositionWalkable(worldX, worldY, worldZ)) {
                waypointTiles.add(new int[]{currentX, currentY, currentZ});
            } else {
                Log.warn("NPCBehaviorAgent", "Encountered non-walkable tile at [" +
                          currentX + "," + currentY + "," + currentZ + "] - stopping path early");
                break;  // Stop if we hit a wall
            }
        }

        Log.info("NPCBehaviorAgent", "Generated " + waypointTiles.size() + " grid waypoints for NPC " + npcId +
                 " (expected ~" + manhattanDistance + " for Manhattan path)");

        return waypointTiles;
    }

    /**
     * Get the current active instruction for an NPC.
     *
     * @param npcId NPC entity ID
     * @return Active instruction, or null if none
     */
    public NPCInstructionMessage getCurrentInstruction(String npcId) {
        return activeInstructions.get(npcId);
    }

    /**
     * Generate immediate instruction for NPC (triggered by completion or spawn).
     *
     * @param npcId NPC entity ID
     * @param currentPos Current position from server tracking
     */
    public void generateImmediateInstruction(String npcId, Vector3 currentPos) {
        curly.octo.common.GameObject obj = objectManager.getObjectById(npcId);
        if (obj instanceof NPCObject) {
            NPCObject npc = (NPCObject) obj;
            generateInstructionForNPC(npc, currentPos);
        } else {
            Log.warn("NPCBehaviorAgent", "Cannot generate instruction - NPC " + npcId + " not found");
        }
    }

    /**
     * Generate immediate instruction for a newly spawned NPC.
     *
     * @param npc The NPC object
     */
    public void generateInitialInstruction(NPCObject npc) {
        generateInstructionForNPC(npc, npc.getPosition());
    }
}
