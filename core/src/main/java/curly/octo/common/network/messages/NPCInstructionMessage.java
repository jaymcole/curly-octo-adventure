package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

import java.util.HashMap;

/**
 * Server distributes NPC behavior instructions to all clients.
 * Clients execute instructions deterministically using provided parameters.
 */
public class NPCInstructionMessage extends NetworkMessage {

    /** Unique identifier for the NPC this instruction applies to */
    public String npcId;

    /** Unique instruction version number */
    public long instructionId;

    /** Type of behavior to execute */
    public InstructionType type;

    /** Server timestamp when instruction starts (milliseconds) */
    public long serverTimestamp;

    /** How long the instruction lasts (seconds) */
    public float duration;

    /** Random seed for deterministic randomness */
    public long randomSeed;

    /** Type-specific parameters (e.g., radius, speed, waypoints) */
    @Deprecated  // Use waypointData for paths instead
    public HashMap<String, Float> params;

    /**
     * Waypoint data for server-provided paths: [x1,y1,z1, x2,y2,z2, ..., xN,yN,zN]
     * Must be initialized to prevent Kryo serialization corruption.
     * @deprecated Use waypointTileIndices instead (clearer for debugging)
     */
    @Deprecated
    public float[] waypointData = new float[0];

    /**
     * Waypoint tile indices for server-provided paths: [x1,y1,z1, x2,y2,z2, ..., xN,yN,zN]
     * Each triplet represents a tile index position that will be converted to world coordinates.
     * Must be initialized to prevent Kryo serialization corruption.
     */
    public int[] waypointTileIndices = new int[0];

    /**
     * Default constructor required for Kryo serialization.
     */
    public NPCInstructionMessage() {
        this.params = new HashMap<>();
    }

    /**
     * Convenience constructor for creating instructions.
     */
    public NPCInstructionMessage(String npcId, long instructionId, InstructionType type,
                                 long serverTimestamp, float duration, long randomSeed) {
        this.npcId = npcId;
        this.instructionId = instructionId;
        this.type = type;
        this.serverTimestamp = serverTimestamp;
        this.duration = duration;
        this.randomSeed = randomSeed;
        this.params = new HashMap<>();
    }

    /**
     * Add a parameter to this instruction.
     */
    public NPCInstructionMessage withParam(String key, float value) {
        this.params.put(key, value);
        return this;
    }

    /**
     * Set waypoint list for this instruction using tile indices.
     * Each waypoint is represented as (tileX, tileY, tileZ) indices.
     *
     * @param tileIndices List of tile index triplets: [(x1,y1,z1), (x2,y2,z2), ...]
     * @return This instruction (for fluent chaining)
     */
    public NPCInstructionMessage withWaypointTileIndices(java.util.List<int[]> tileIndices) {
        this.waypointTileIndices = new int[tileIndices.size() * 3];
        for (int i = 0; i < tileIndices.size(); i++) {
            int[] tile = tileIndices.get(i);
            waypointTileIndices[i * 3] = tile[0];      // x
            waypointTileIndices[i * 3 + 1] = tile[1];  // y
            waypointTileIndices[i * 3 + 2] = tile[2];  // z
        }
        return this;
    }

    /**
     * Set waypoint list for this instruction.
     * Converts list of Vector3 waypoints to flat float array format.
     *
     * @param waypoints List of waypoints to navigate
     * @return This instruction (for fluent chaining)
     * @deprecated Use withWaypointTileIndices instead
     */
    @Deprecated
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

    @Override
    public String toString() {
        return "NPCInstructionMessage{" +
                "npcId='" + npcId + '\'' +
                ", instructionId=" + instructionId +
                ", type=" + type +
                ", duration=" + duration +
                ", params=" + params.size() +
                '}';
    }

    /**
     * Types of NPC behaviors that can be instructed.
     */
    public enum InstructionType {
        /** Stand still in place */
        IDLE,

        /** Random walk within a radius */
        WANDER,

        /** Follow a path of waypoints */
        PATROL,

        /** Chase a target player */
        CHASE,

        /** Follow a precise time-based path */
        CUSTOM_PATH
    }
}
