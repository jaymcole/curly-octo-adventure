package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

/**
 * Server announces NPC sync authority election results to all clients.
 * One client is elected to broadcast synchronization corrections.
 */
public class NPCElectionMessage extends NetworkMessage {

    /** ClientUniqueId of the elected sync authority */
    public String electedClientId;

    /** Server timestamp when election occurred (milliseconds) */
    public long electionTimestamp;

    /** Reason for the election */
    public ElectionReason reason;

    /**
     * Default constructor required for Kryo serialization.
     */
    public NPCElectionMessage() {
    }

    /**
     * Convenience constructor for creating election messages.
     */
    public NPCElectionMessage(String electedClientId, long electionTimestamp, ElectionReason reason) {
        this.electedClientId = electedClientId;
        this.electionTimestamp = electionTimestamp;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "NPCElectionMessage{" +
                "electedClientId='" + electedClientId + '\'' +
                ", electionTimestamp=" + electionTimestamp +
                ", reason=" + reason +
                '}';
    }

    /**
     * Reasons for triggering an NPC sync authority election.
     */
    public enum ElectionReason {
        /** Initial election when server starts */
        INITIAL,

        /** Client joined and became first in sorted order */
        CLIENT_JOIN,

        /** Elected client disconnected, need new authority */
        CLIENT_DISCONNECT,

        /** Elected client became unresponsive, forced failover */
        FAILOVER,

        /** Manual re-election triggered by server admin */
        MANUAL
    }
}
