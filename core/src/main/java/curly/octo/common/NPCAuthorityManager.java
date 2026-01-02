package curly.octo.common;

import com.esotericsoftware.minlog.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Global static utility for managing NPC authority tracking.
 * Provides single source of truth for which NPCs this client has authority over.
 *
 * Pattern follows NetworkManager - centralized static state accessible from anywhere.
 */
public class NPCAuthorityManager {

    // NPCs this client has authority for (elected by server)
    private static final Set<String> myManagedNPCs = new HashSet<>();

    // Callbacks to invoke when NPCs complete their paths
    private static final Map<String, Runnable> pathCompleteCallbacks = new HashMap<>();

    /**
     * Check if this client has authority for the given NPC.
     */
    public static boolean isMyNPC(String npcId) {
        boolean hasAuthority = myManagedNPCs.contains(npcId);
        Log.debug("NPCAuthorityManager", "isMyNPC(" + npcId + ") = " + hasAuthority +
                  " (total managed: " + myManagedNPCs.size() + ")");
        return hasAuthority;
    }

    /**
     * Get all NPCs this client manages (defensive copy).
     */
    public static Set<String> getManagedNPCs() {
        return new HashSet<>(myManagedNPCs);
    }

    /**
     * Check if this client manages any NPCs.
     */
    public static boolean hasAnyManagedNPCs() {
        return !myManagedNPCs.isEmpty();
    }

    /**
     * Grant authority for the given NPC to this client.
     * Called when server sends NPCElectionMessage electing this client.
     */
    public static void addAuthority(String npcId) {
        boolean added = myManagedNPCs.add(npcId);
        if (added) {
            Log.info("NPCAuthorityManager", "*** AUTHORITY GRANTED FOR NPC: " + npcId + " ***");
        }
    }

    /**
     * Remove authority for the given NPC from this client.
     * Called when NPC is despawned or authority is reassigned.
     */
    public static void removeAuthority(String npcId) {
        boolean removed = myManagedNPCs.remove(npcId);
        pathCompleteCallbacks.remove(npcId);
        if (removed) {
            Log.info("NPCAuthorityManager", "Authority removed for NPC: " + npcId);
        }
    }

    /**
     * Set callback to invoke when the given NPC completes its path.
     * Only called for NPCs this client has authority over.
     */
    public static void setPathCompleteCallback(String npcId, Runnable callback) {
        if (callback != null) {
            pathCompleteCallbacks.put(npcId, callback);
            Log.debug("NPCAuthorityManager", "Path complete callback registered for NPC: " + npcId);
        } else {
            pathCompleteCallbacks.remove(npcId);
        }
    }

    /**
     * Invoke the path completion callback for the given NPC.
     * Called by NPCObject when it completes its path.
     *
     * @param npcId The NPC that completed its path
     * @return true if callback was invoked, false if no callback registered
     */
    public static boolean invokePathCompleteCallback(String npcId) {
        Runnable callback = pathCompleteCallbacks.get(npcId);
        if (callback != null) {
            Log.info("NPCAuthorityManager", "Invoking path complete callback for NPC: " + npcId);
            callback.run();
            return true;
        } else {
            Log.warn("NPCAuthorityManager", "No path complete callback for NPC: " + npcId +
                     " (registered callbacks: " + pathCompleteCallbacks.keySet() + ")");
            return false;
        }
    }

    /**
     * Clear all authority data.
     * Called when disconnecting or switching game modes.
     */
    public static void clear() {
        int count = myManagedNPCs.size();
        myManagedNPCs.clear();
        pathCompleteCallbacks.clear();
        Log.info("NPCAuthorityManager", "Cleared authority data (" + count + " NPCs)");
    }
}
