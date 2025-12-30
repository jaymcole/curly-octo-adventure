package curly.octo.server;

import curly.octo.common.network.messages.NPCElectionMessage;
import curly.octo.server.playerManagement.ClientManager;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.playerManagement.ClientUniqueId;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Manages the election of a sync authority for NPC synchronization.
 * Uses deterministic election based on ClientUniqueId sorting.
 */
public class NPCElectionManager {

    private final ClientManager clientManager;
    private ClientUniqueId currentAuthority;
    private long lastElectionTimestamp;

    public NPCElectionManager(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.currentAuthority = null;
        this.lastElectionTimestamp = 0;
    }

    /**
     * Run an election to determine the NPC sync authority.
     * Uses deterministic sorting: first client alphabetically by UUID wins.
     *
     * @param reason The reason for this election
     * @return Election message to broadcast, or null if no clients available
     */
    public NPCElectionMessage runElection(NPCElectionMessage.ElectionReason reason) {
        ArrayList<ClientProfile> activeProfiles = clientManager.getAllClientProfiles();

        // No clients connected - no authority needed
        if (activeProfiles.isEmpty()) {
            currentAuthority = null;
            return null;
        }

        // Sort clients by ClientUniqueId (deterministic)
        activeProfiles.sort(Comparator.comparing(profile -> profile.clientUniqueId.uniqueId));

        // First client in sorted order becomes authority
        ClientUniqueId elected = activeProfiles.get(0).clientUniqueId;

        // Update internal state
        currentAuthority = elected;
        lastElectionTimestamp = System.currentTimeMillis();

        // Create election message
        return new NPCElectionMessage(
                elected.uniqueId,
                lastElectionTimestamp,
                reason
        );
    }

    /**
     * Check if a re-election is needed due to client disconnect.
     *
     * @param disconnectedClientId The client that disconnected
     * @return True if the disconnected client was the current authority
     */
    public boolean isReElectionNeeded(ClientUniqueId disconnectedClientId) {
        if (currentAuthority == null) {
            return false;
        }
        return currentAuthority.equals(disconnectedClientId);
    }

    /**
     * Get the currently elected sync authority.
     *
     * @return ClientUniqueId of the current authority, or null if none elected
     */
    public ClientUniqueId getCurrentAuthority() {
        return currentAuthority;
    }

    /**
     * Check if a specific client is the current authority.
     *
     * @param clientId The ClientUniqueId to check
     * @return True if this client is the current authority
     */
    public boolean isAuthority(ClientUniqueId clientId) {
        if (currentAuthority == null || clientId == null) {
            return false;
        }
        return currentAuthority.equals(clientId);
    }

    /**
     * Get the timestamp of the last election.
     *
     * @return Milliseconds since epoch of last election
     */
    public long getLastElectionTimestamp() {
        return lastElectionTimestamp;
    }

    /**
     * Check if an election has ever been run.
     *
     * @return True if an authority has been elected
     */
    public boolean hasAuthority() {
        return currentAuthority != null;
    }

    /**
     * Force a re-election (for manual admin commands or failover).
     *
     * @return Election message to broadcast
     */
    public NPCElectionMessage forceReElection() {
        return runElection(NPCElectionMessage.ElectionReason.MANUAL);
    }
}
