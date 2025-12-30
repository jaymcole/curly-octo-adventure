package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

/**
 * Elected client broadcasts NPC position corrections to all other clients.
 * Used to fix minor inconsistencies from deterministic instruction execution.
 */
public class NPCSyncMessage extends NetworkMessage {

    /** Incremental sync counter for detecting missed messages */
    public long syncId;

    /** Server timestamp of this sync (milliseconds) */
    public long timestamp;

    /** Type of synchronization */
    public SyncType syncType;

    /** IDs of NPCs included in this sync */
    public String[] npcIds;

    /** Packed position data: x,y,z triplets for each NPC */
    public float[] positions;

    /** Packed orientation data: yaw,pitch pairs for each NPC (degrees) */
    public float[] orientations;

    /** Active instruction ID for each NPC */
    public long[] activeInstructionIds;

    /**
     * Default constructor required for Kryo serialization.
     */
    public NPCSyncMessage() {
    }

    /**
     * Convenience constructor for creating sync messages.
     */
    public NPCSyncMessage(long syncId, long timestamp, SyncType syncType, int npcCount) {
        this.syncId = syncId;
        this.timestamp = timestamp;
        this.syncType = syncType;
        this.npcIds = new String[npcCount];
        this.positions = new float[npcCount * 3]; // x, y, z per NPC
        this.orientations = new float[npcCount * 2]; // yaw, pitch per NPC
        this.activeInstructionIds = new long[npcCount];
    }

    /**
     * Set position for an NPC at the given index.
     */
    public void setPosition(int index, float x, float y, float z) {
        positions[index * 3] = x;
        positions[index * 3 + 1] = y;
        positions[index * 3 + 2] = z;
    }

    /**
     * Get position for an NPC at the given index.
     */
    public void getPosition(int index, float[] outPosition) {
        outPosition[0] = positions[index * 3];
        outPosition[1] = positions[index * 3 + 1];
        outPosition[2] = positions[index * 3 + 2];
    }

    /**
     * Set orientation for an NPC at the given index.
     */
    public void setOrientation(int index, float yaw, float pitch) {
        orientations[index * 2] = yaw;
        orientations[index * 2 + 1] = pitch;
    }

    /**
     * Get orientation for an NPC at the given index.
     */
    public void getOrientation(int index, float[] outOrientation) {
        outOrientation[0] = orientations[index * 2];
        outOrientation[1] = orientations[index * 2 + 1];
    }

    @Override
    public String toString() {
        return "NPCSyncMessage{" +
                "syncId=" + syncId +
                ", timestamp=" + timestamp +
                ", syncType=" + syncType +
                ", npcCount=" + (npcIds != null ? npcIds.length : 0) +
                '}';
    }

    /**
     * Types of synchronization messages.
     */
    public enum SyncType {
        /** Complete state for all NPCs - sent periodically for consistency */
        FULL,

        /** Only NPCs that have moved significantly - sent frequently */
        DELTA,

        /** Immediate sync for critical events (spawn, death, etc.) */
        CRITICAL
    }
}
