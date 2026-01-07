package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.WorldObject;
import curly.octo.common.character.WalkingCharacter;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.NPCInstructionMessage;
import curly.octo.server.ServerGameObjectManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * Server-side agent that generates and broadcasts NPC behavior instructions.
 * Clients execute these instructions deterministically.
 */
public class NPCBehaviorAgent extends BaseAgent {

    private final Random random;
    private float instructionTimer = 0f;
    private static final float INSTRUCTION_INTERVAL = 5.0f; // Fallback timer (was: primary timer)
    private final curly.octo.common.map.GameMap mapManager;
    private final HashMap<String, NPCInstructionMessage> activeInstructions = new HashMap<>();

    public NPCBehaviorAgent(ServerGameObjectManager objectManager, curly.octo.common.map.GameMap mapManager) {
        super(objectManager);
        this.random = new Random();
        this.mapManager = mapManager;
    }

    @Override
    public void update(float deltaTime) {
        // No periodic updates needed - instruction generation is event-driven
        // Triggered by NPCPathCompleteMessage from clients or initial spawn
    }

    /**
     * Generate and broadcast instructions for all NPCs.
     * Used by fallback timer only.
     */
    private void generateInstructions() {
        for (WorldObject obj : objectManager.getNPCs()) {
            if (obj instanceof WalkingCharacter) {
                WalkingCharacter npc = (WalkingCharacter) obj;
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
    private void generateInstructionForNPC(WalkingCharacter npc, Vector3 currentPos) {
        // Use provided position or fall back to NPC's stored position
        Vector3 startPos = currentPos != null ? currentPos : npc.getPosition();

        if (startPos == null) {
            Log.error("NPCBehaviorAgent", "Cannot generate instruction - NPC " + npc.entityId + " has no position");
            return;
        }

        // Generate waypoint list as tile indices (10-20 waypoints along straight path)
        List<int[]> waypointTileIndices = generateWaypointTileIndices(npc.entityId, startPos);
        waypointTileIndices = reduceWaypoints(waypointTileIndices);

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

        // Broadcast to all clients
        NetworkManager.sendToAllClients(instruction);

        Log.info("NPCBehaviorAgent", "Sent path with " + waypointTileIndices.size() +
                 " waypoint tiles to NPC " + npc.entityId + " (speed: 0.3)");
    }


    /**
     * Takes in a list of waypoint and removes redundant points.
     * Waypoints are removed if they lay on the same line between neighboring waypoints.
     * @param waypoints
     * @return a new reduced list of tile indices where each point indicates a change in direction.
     */
    private List<int[]> reduceWaypoints(List<int[]> waypoints) {
        // Handle edge cases
        if (waypoints == null || waypoints.size() <= 2) {
            return waypoints;
        }

        java.util.ArrayList<int[]> reduced = new java.util.ArrayList<>();
        reduced.add(waypoints.get(0)); // Always keep first waypoint

        // Check each waypoint to see if it represents a direction change
        for (int i = 1; i < waypoints.size() - 1; i++) {
            int[] prev = waypoints.get(i - 1);
            int[] curr = waypoints.get(i);
            int[] next = waypoints.get(i + 1);

            // If current point is NOT collinear with prev and next, it's a direction change - keep it
            if (!areCollinear(prev, curr, next)) {
                reduced.add(curr);
            }
        }

        reduced.add(waypoints.get(waypoints.size() - 1)); // Always keep last waypoint
        return reduced;
    }

    /**
     * Check if three points are collinear (lie on the same line).
     * Uses cross product: if AB × BC = 0, then points are collinear.
     */
    private boolean areCollinear(int[] a, int[] b, int[] c) {
        // Vector from A to B
        int abX = b[0] - a[0];
        int abY = b[1] - a[1];
        int abZ = b[2] - a[2];

        // Vector from B to C
        int bcX = c[0] - b[0];
        int bcY = c[1] - b[1];
        int bcZ = c[2] - b[2];

        // Cross product AB × BC
        int crossX = abY * bcZ - abZ * bcY;
        int crossY = abZ * bcX - abX * bcZ;
        int crossZ = abX * bcY - abY * bcX;

        // Points are collinear if cross product is zero vector
        return crossX == 0 && crossY == 0 && crossZ == 0;
    }

    /**
     * Check if an NPC with given radius can navigate from one tile to another.
     * Validates that adjacent tiles don't have walls that would block the NPC's capsule.
     *
     * @param fromX Source tile X index
     * @param fromY Source tile Y index
     * @param fromZ Source tile Z index
     * @param toX Destination tile X index
     * @param toY Destination tile Y index
     * @param toZ Destination tile Z index
     * @param npcRadius Radius of NPC capsule collision shape
     * @return true if path segment is safe for NPC to traverse
     */
    private boolean isPathSegmentSafe(int fromX, int fromY, int fromZ,
                                      int toX, int toY, int toZ,
                                      float npcRadius) {
        // Determine movement direction
        int dx = Integer.compare(toX, fromX);
        int dz = Integer.compare(toZ, fromZ);

        // Check perpendicular tiles for walls that would block NPC capsule
        // If moving in X direction, check Z neighbors
        // If moving in Z direction, check X neighbors

        if (dx != 0) {
            // Moving along X - check tiles above/below in Z
            for (int offsetZ : new int[]{-1, 1}) {
                curly.octo.common.map.MapTile adjacent = mapManager.getTile(toX, toY, toZ + offsetZ);
                if (adjacent != null && adjacent.geometryType != curly.octo.common.map.enums.MapTileGeometryType.EMPTY) {
                    // Wall exists - check if within NPC radius
                    float wallDistance = Math.abs(offsetZ) * curly.octo.common.Constants.MAP_TILE_SIZE;
                    if (wallDistance < npcRadius + 0.2f) {  // 0.2 safety margin
                        return false;  // Too narrow for NPC
                    }
                }
            }
        }

        if (dz != 0) {
            // Moving along Z - check tiles left/right in X
            for (int offsetX : new int[]{-1, 1}) {
                curly.octo.common.map.MapTile adjacent = mapManager.getTile(toX + offsetX, toY, toZ);
                if (adjacent != null && adjacent.geometryType != curly.octo.common.map.enums.MapTileGeometryType.EMPTY) {
                    float wallDistance = Math.abs(offsetX) * curly.octo.common.Constants.MAP_TILE_SIZE;
                    if (wallDistance < npcRadius + 0.2f) {
                        return false;
                    }
                }
            }
        }

        return true;  // Path is clear
    }

    private boolean isTileWalkable(int x, int y, int z) {
        float wx = x * curly.octo.common.Constants.MAP_TILE_SIZE;
        float wy = y * curly.octo.common.Constants.MAP_TILE_SIZE;
        float wz = z * curly.octo.common.Constants.MAP_TILE_SIZE;
        return mapManager.isPositionWalkable(wx, wy, wz);
    }

    private int[] findNearestWalkableTile(int startX, int startY, int startZ) {
        // Check start tile first
        if (isTileWalkable(startX, startY, startZ)) return new int[]{startX, startY, startZ};

        // Check neighbors (Radius 1)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx==0 && dy==0 && dz==0) continue;
                    if (isTileWalkable(startX + dx, startY + dy, startZ + dz)) {
                        return new int[]{startX + dx, startY + dy, startZ + dz};
                    }
                }
            }
        }
        return null;
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
    private List<int[]> generateWaypointTileIndices(String npcId, Vector3 startPos) {
        List<int[]> waypointTiles = new ArrayList<>();

        if (mapManager == null) {
            Log.error("NPCBehaviorAgent", "Cannot generate waypoints - no map reference");
            return waypointTiles;
        }

        // Convert start position to tile indices
        // Use Math.floor to correctly map world coordinates to grid indices (0.0 to 1.99 -> 0)
        int startTileX = (int)Math.floor(startPos.x / curly.octo.common.Constants.MAP_TILE_SIZE);
        int startTileY = (int)Math.floor(startPos.y / curly.octo.common.Constants.MAP_TILE_SIZE);
        int startTileZ = (int)Math.floor(startPos.z / curly.octo.common.Constants.MAP_TILE_SIZE);

        // Validate and snap start position if the exact tile is not walkable
        // This handles cases where the NPC is standing on the edge of a tile or slightly floating
        int[] snappedStart = findNearestWalkableTile(startTileX, startTileY, startTileZ);
        if (snappedStart != null) {
             if (snappedStart[0] != startTileX || snappedStart[1] != startTileY || snappedStart[2] != startTileZ) {
                 startTileX = snappedStart[0];
                 startTileY = snappedStart[1];
                 startTileZ = snappedStart[2];
             }
        } else {
             Log.warn("NPCBehaviorAgent", "Could not find any walkable tile near start position for " + npcId);
             return waypointTiles;
        }

        // Get all WALKABLE positions at same Y level (empty space with floor below)
        ArrayList<curly.octo.common.map.MapTile> candidateTiles = new ArrayList<>();

        for (curly.octo.common.map.MapTile tile : mapManager.getAllTiles()) {
            int tileY = (int)Math.floor(tile.y / curly.octo.common.Constants.MAP_TILE_SIZE);

            // NPC stands in empty space, so select EMPTY tiles at the NPC's Y level
            // that have solid ground directly below them
            if (tileY == startTileY &&
                tile.geometryType == curly.octo.common.map.enums.MapTileGeometryType.EMPTY) {

                // Check if there's solid ground below this empty space
                float groundCheckY = tile.y - curly.octo.common.Constants.MAP_TILE_SIZE;
                curly.octo.common.map.MapTile groundTile = mapManager.getTileFromWorldCoordinates(
                    tile.x, groundCheckY, tile.z
                );

                if (groundTile != null &&
                    groundTile.geometryType != curly.octo.common.map.enums.MapTileGeometryType.EMPTY) {
                    candidateTiles.add(tile);
                }
            }
        }

        if (candidateTiles.isEmpty()) {
            Log.warn("NPCBehaviorAgent", "No candidate tiles found for NPC " + npcId +
                     " at tile height " + startTileY);
            return waypointTiles;
        }

        // Find a far destination: sort candidates by Manhattan distance and pick from the farthest 25%
        int finalStartTileX = startTileX;
        int finalStartTileZ = startTileZ;
        candidateTiles.sort((a, b) -> {
            int aTileX = (int)Math.floor(a.x / curly.octo.common.Constants.MAP_TILE_SIZE);
            int aTileZ = (int)Math.floor(a.z / curly.octo.common.Constants.MAP_TILE_SIZE);
            int bTileX = (int)Math.floor(b.x / curly.octo.common.Constants.MAP_TILE_SIZE);
            int bTileZ = (int)Math.floor(b.z / curly.octo.common.Constants.MAP_TILE_SIZE);

            int distA = Math.abs(aTileX - finalStartTileX) + Math.abs(aTileZ - finalStartTileZ);
            int distB = Math.abs(bTileX - finalStartTileX) + Math.abs(bTileZ - finalStartTileZ);

            return Integer.compare(distB, distA);  // Sort descending (farthest first)
        });

        // Pick from the farthest 25% of tiles
        int farTilePoolSize = Math.max(1, candidateTiles.size() / 4);
        curly.octo.common.map.MapTile destTile = candidateTiles.get(random.nextInt(farTilePoolSize));

        int destTileX = (int)Math.floor(destTile.x / curly.octo.common.Constants.MAP_TILE_SIZE);
        int destTileY = (int)Math.floor(destTile.y / curly.octo.common.Constants.MAP_TILE_SIZE);
        int destTileZ = (int)Math.floor(destTile.z / curly.octo.common.Constants.MAP_TILE_SIZE);

        // A* Pathfinding
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Set<Long> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(startTileX, startTileY, startTileZ, 0, Math.abs(destTileX - startTileX) + Math.abs(destTileZ - startTileZ), null);
        openSet.add(startNode);

        PathNode targetNode = null;
        int maxIterations = 5000; // Safety limit
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            PathNode current = openSet.poll();

            long key = mapManager.constructKeyFromIndexCoordinates(current.x, current.y, current.z);
            if (closedSet.contains(key)) continue;
            closedSet.add(key);

            if (current.x == destTileX && current.z == destTileZ) {
                targetNode = current;
                break;
            }

            // Neighbors: +X, -X, +Z, -Z
            int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};

            for (int[] dir : directions) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                int ny = current.y; // Constant Y - 2D navigation only

                long nKey = mapManager.constructKeyFromIndexCoordinates(nx, ny, nz);
                if (closedSet.contains(nKey)) continue;

                // Check walkability
                if (!isTileWalkable(nx, ny, nz)) {
                    continue;
                }

                // Check segment safety
                if (!isPathSegmentSafe(current.x, current.y, current.z, nx, ny, nz, 1.0f)) {
                    continue;
                }

                int newGCost = current.gCost + 1;
                int newHCost = Math.abs(nx - destTileX) + Math.abs(nz - destTileZ);

                openSet.add(new PathNode(nx, ny, nz, newGCost, newHCost, current));
            }
        }

        if (targetNode != null) {
            // Reconstruct path
            PathNode node = targetNode;
            while (node.parent != null) {
                waypointTiles.add(new int[]{node.x, node.y, node.z});
                node = node.parent;
            }
            Collections.reverse(waypointTiles);
        } else {
            Log.warn("NPCBehaviorAgent", "A* failed to find path to [" + destTileX + "," + destTileY + "," + destTileZ + "] after " + iterations + " iterations");
        }

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
        if (obj instanceof WalkingCharacter) {
            WalkingCharacter npc = (WalkingCharacter) obj;
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
    public void generateInitialInstruction(WalkingCharacter npc) {
        Log.info("NPCBehaviorAgent", "generateInitialInstruction called for NPC: " + npc.entityId + " at position: " + npc.getPosition());
        generateInstructionForNPC(npc, npc.getPosition());
    }

    /**
     * Node for A* pathfinding
     */
    private static class PathNode implements Comparable<PathNode> {
        int x, y, z;
        int gCost;
        int hCost;
        PathNode parent;

        public PathNode(int x, int y, int z, int gCost, int hCost, PathNode parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.gCost = gCost;
            this.hCost = hCost;
            this.parent = parent;
        }

        public int fCost() { return gCost + hCost; }

        @Override
        public int compareTo(PathNode o) {
            return Integer.compare(this.fCost(), o.fCost());
        }
    }
}
