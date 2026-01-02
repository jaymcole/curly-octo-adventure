package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

/**
 * Sent by elected client when NPC completes its current path.
 * Includes final position to avoid server recalculation.
 * Server immediately generates new instruction upon receipt.
 */
public class NPCPathCompleteMessage extends NetworkMessage {

    /** Entity ID of the NPC that completed its path */
    public String npcId;

    /** Final position where NPC stopped: [x, y, z] */
    public float[] finalPosition = new float[3];

    /** Final yaw orientation in degrees */
    public float yaw;

    /** Timestamp when completion was detected (milliseconds) */
    public long timestamp;

    /** Instruction ID that was completed (for validation) */
    public long instructionId;

    /**
     * Default constructor for Kryo serialization.
     */
    public NPCPathCompleteMessage() {
    }

    /**
     * Convenience constructor.
     */
    public NPCPathCompleteMessage(String npcId, float x, float y, float z,
                                   float yaw, long instructionId) {
        this.npcId = npcId;
        this.finalPosition[0] = x;
        this.finalPosition[1] = y;
        this.finalPosition[2] = z;
        this.yaw = yaw;
        this.instructionId = instructionId;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "NPCPathCompleteMessage{" +
                "npcId='" + npcId + '\'' +
                ", finalPosition=[" + finalPosition[0] + "," +
                finalPosition[1] + "," + finalPosition[2] + "]" +
                ", yaw=" + yaw +
                ", instructionId=" + instructionId +
                ", timestamp=" + timestamp +
                '}';
    }
}
