package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

/**
 * Message broadcast by server to announce NPC sync authority elections.
 * Each NPC is assigned to exactly one client for position synchronization.
 */
public class NPCElectionMessage extends NetworkMessage {

    /**
     * Reason for this election.
     */
    public enum ElectionReason {
        INITIAL,           // NPC just spawned
        CLIENT_DISCONNECT, // Previous authority disconnected
        MANUAL             // Manually triggered
    }

    /** Entity ID of the NPC this election is for */
    public String npcId;

    /** ClientUniqueId of the elected sync authority for this NPC */
    public String electedClientId;

    /** Timestamp when this election was conducted (milliseconds since epoch) */
    public long electionTimestamp;

    /** Reason for this election */
    public ElectionReason reason;

    /**
     * Default constructor required for Kryo serialization.
     */
    public NPCElectionMessage() {
    }

    /**
     * Convenience constructor for creating election messages.
     */
    public NPCElectionMessage(String npcId, String electedClientId, long electionTimestamp, ElectionReason reason) {
        this.npcId = npcId;
        this.electedClientId = electedClientId;
        this.electionTimestamp = electionTimestamp;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "NPCElectionMessage{" +
                "npcId='" + npcId + '\'' +
                ", electedClientId='" + electedClientId + '\'' +
                ", electionTimestamp=" + electionTimestamp +
                ", reason=" + reason +
                '}';
    }
}
