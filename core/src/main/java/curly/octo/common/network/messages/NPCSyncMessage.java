package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

/**
 * Proximity-authority client broadcasts position correction for a single NPC.
 * Sent by the client closest to the NPC to correct drift on other clients.
 * Simple, robust format with minimal fields.
 */
public class NPCSyncMessage extends NetworkMessage {

    /** ID of the NPC this sync is for */
    public String npcId;

    /** Position data: [x, y, z] - initialized to prevent serialization errors */
    public float[] position = new float[3];

    /** Yaw rotation in degrees */
    public float yaw;

    /** Timestamp when this correction was sent (milliseconds) */
    public long timestamp;

    /**
     * Default constructor required for Kryo serialization.
     * Arrays initialized in field declaration to prevent corruption.
     */
    public NPCSyncMessage() {
    }

    /**
     * Convenience constructor for creating sync messages.
     */
    public NPCSyncMessage(String npcId, float x, float y, float z, float yaw) {
        this.npcId = npcId;
        this.position[0] = x;
        this.position[1] = y;
        this.position[2] = z;
        this.yaw = yaw;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Set position data.
     */
    public void setPosition(float x, float y, float z) {
        if (position == null) position = new float[3];
        position[0] = x;
        position[1] = y;
        position[2] = z;
    }

    @Override
    public String toString() {
        return "NPCSyncMessage{" +
                "npcId='" + npcId + '\'' +
                ", position=[" + (position != null && position.length == 3 ?
                    position[0] + "," + position[1] + "," + position[2] : "null") + "]" +
                ", yaw=" + yaw +
                ", timestamp=" + timestamp +
                '}';
    }
}
