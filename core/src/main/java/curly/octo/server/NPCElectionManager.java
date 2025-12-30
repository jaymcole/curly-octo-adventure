package curly.octo.server;

import curly.octo.common.network.messages.NPCElectionMessage;
import curly.octo.server.playerManagement.ClientManager;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.playerManagement.ClientUniqueId;
import com.esotericsoftware.minlog.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages per-NPC sync authority elections.
 * Each NPC is assigned to exactly one client authority using deterministic sorting.
 */
public class NPCElectionManager {

    private final ClientManager clientManager;

    /** Map of NPC ID to its elected sync authority */
    private final Map<String, ClientUniqueId> npcAuthorities;

    /** Timestamp of last election per NPC */
    private final Map<String, Long> npcElectionTimestamps;

    public NPCElectionManager(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.npcAuthorities = new HashMap<>();
        this.npcElectionTimestamps = new HashMap<>();
    }

    /**
     * Run an election to determine sync authority for a specific NPC.
     * Uses deterministic sorting: first client alphabetically by UUID wins.
     *
     * @param npcId The NPC entity ID to elect authority for
     * @param reason The reason for this election
     * @return Election message to broadcast, or null if no clients available
     */
    public NPCElectionMessage runElection(String npcId, NPCElectionMessage.ElectionReason reason) {
        ArrayList<ClientProfile> activeProfiles = clientManager.getAllClientProfiles();

        // No clients connected - no authority needed
        if (activeProfiles.isEmpty()) {
            npcAuthorities.remove(npcId);
            npcElectionTimestamps.remove(npcId);
            Log.warn("NPCElectionManager", "No clients available for NPC election: " + npcId);
            return null;
        }

        // Sort clients by ClientUniqueId (deterministic)
        activeProfiles.sort(Comparator.comparing(profile -> profile.clientUniqueId.uniqueId));

        // First client in sorted order becomes authority
        ClientUniqueId elected = activeProfiles.get(0).clientUniqueId;

        // Update internal state
        npcAuthorities.put(npcId, elected);
        long timestamp = System.currentTimeMillis();
        npcElectionTimestamps.put(npcId, timestamp);

        Log.info("NPCElectionManager",
                "Elected authority for NPC " + npcId + ": " + elected.uniqueId +
                " (reason: " + reason + ")");

        // Create election message
        return new NPCElectionMessage(
                npcId,
                elected.uniqueId,
                timestamp,
                reason
        );
    }

    /**
     * Reassign all NPCs owned by a disconnected client to a new authority.
     * Returns list of election messages to broadcast.
     *
     * @param disconnectedClientId The client that disconnected
     * @return List of election messages for reassigned NPCs
     */
    public ArrayList<NPCElectionMessage> reassignOrphanedNPCs(ClientUniqueId disconnectedClientId) {
        ArrayList<NPCElectionMessage> elections = new ArrayList<>();

        // Find all NPCs owned by the disconnected client
        ArrayList<String> orphanedNPCs = new ArrayList<>();
        for (Map.Entry<String, ClientUniqueId> entry : npcAuthorities.entrySet()) {
            if (entry.getValue().equals(disconnectedClientId)) {
                orphanedNPCs.add(entry.getKey());
            }
        }

        if (orphanedNPCs.isEmpty()) {
            Log.info("NPCElectionManager",
                    "Client " + disconnectedClientId.uniqueId +
                    " disconnected but owned no NPCs");
            return elections;
        }

        Log.info("NPCElectionManager",
                "Reassigning " + orphanedNPCs.size() + " NPCs from disconnected client " +
                disconnectedClientId.uniqueId);

        // Run election for each orphaned NPC
        for (String npcId : orphanedNPCs) {
            NPCElectionMessage election = runElection(npcId, NPCElectionMessage.ElectionReason.CLIENT_DISCONNECT);
            if (election != null) {
                elections.add(election);
            }
        }

        return elections;
    }

    /**
     * Get the current sync authority for a specific NPC.
     *
     * @param npcId The NPC entity ID
     * @return ClientUniqueId of the authority, or null if none assigned
     */
    public ClientUniqueId getAuthorityForNPC(String npcId) {
        return npcAuthorities.get(npcId);
    }

    /**
     * Check if a specific client is the authority for a given NPC.
     *
     * @param npcId The NPC entity ID
     * @param clientId The ClientUniqueId to check
     * @return True if this client is the authority for this NPC
     */
    public boolean isAuthorityForNPC(String npcId, ClientUniqueId clientId) {
        if (clientId == null) {
            return false;
        }
        ClientUniqueId authority = npcAuthorities.get(npcId);
        return authority != null && authority.equals(clientId);
    }

    /**
     * Get all NPCs managed by a specific client.
     *
     * @param clientId The ClientUniqueId to check
     * @return List of NPC IDs managed by this client
     */
    public ArrayList<String> getNPCsManagedByClient(ClientUniqueId clientId) {
        ArrayList<String> managedNPCs = new ArrayList<>();
        for (Map.Entry<String, ClientUniqueId> entry : npcAuthorities.entrySet()) {
            if (entry.getValue().equals(clientId)) {
                managedNPCs.add(entry.getKey());
            }
        }
        return managedNPCs;
    }

    /**
     * Get the timestamp of the last election for a specific NPC.
     *
     * @param npcId The NPC entity ID
     * @return Milliseconds since epoch of last election, or 0 if never elected
     */
    public long getElectionTimestamp(String npcId) {
        return npcElectionTimestamps.getOrDefault(npcId, 0L);
    }

    /**
     * Check if a specific NPC has an assigned authority.
     *
     * @param npcId The NPC entity ID
     * @return True if an authority has been assigned
     */
    public boolean hasAuthority(String npcId) {
        return npcAuthorities.containsKey(npcId);
    }

    /**
     * Remove election data for a specific NPC (e.g., when NPC is despawned).
     *
     * @param npcId The NPC entity ID to clear
     */
    public void clearNPCElection(String npcId) {
        npcAuthorities.remove(npcId);
        npcElectionTimestamps.remove(npcId);
        Log.info("NPCElectionManager", "Cleared election data for NPC: " + npcId);
    }

    /**
     * Get total number of NPCs with assigned authorities.
     *
     * @return Count of NPCs with authorities
     */
    public int getManagedNPCCount() {
        return npcAuthorities.size();
    }
}
