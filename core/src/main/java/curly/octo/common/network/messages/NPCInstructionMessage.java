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
    public HashMap<String, Float> params;

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
