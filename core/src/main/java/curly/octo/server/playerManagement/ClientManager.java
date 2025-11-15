package curly.octo.server.playerManagement;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientManager {
    // Active players
    private HashMap<ClientConnectionKey, ClientProfile> clientProfiles;

    // Non-active players. They disconnected for some reason. We'll keep their profile safe in case they come back.
    private HashMap<ClientUniqueId, ClientProfile> inactiveProfiles;

    // Mapping from player UUID to client profile for quick lookup
    private HashMap<String, ClientProfile> playerUUIDToProfile;

    public ClientManager() {
        clientProfiles = new HashMap<>();
        inactiveProfiles = new HashMap<>();
        playerUUIDToProfile = new HashMap<>();

    }

    public boolean clientProfileExists(ClientConnectionKey key) {
        return (clientProfiles.containsKey(key) || inactiveProfiles.containsKey(key));
    }

    public ArrayList<ClientProfile> getAllClientProfiles() {
        return new ArrayList<>(clientProfiles.values());
    }

    public ArrayList<ClientProfile> getAllInactiveProfiles() {
        return new ArrayList<>(inactiveProfiles.values());
    }

    public ClientProfile getClientProfile(ClientConnectionKey key) {
        if (clientProfiles.containsKey(key)) {
            return clientProfiles.get(key);
        }
        return null;
    }


    public void createNewProfile(ClientConnectionKey clientKey) {
        clientProfiles.put(clientKey, new ClientProfile());
    }

    public void deactivateProfile(ClientConnectionKey clientKey) {
        if (clientProfiles.containsKey(clientKey)) {
            ClientProfile deactivatedProfile = clientProfiles.remove(clientKey);
            inactiveProfiles.put(deactivatedProfile.clientUniqueId, deactivatedProfile);
        }
    }

    public void activateProfile(ClientConnectionKey clientKey, ClientUniqueId uniqueId) {
        if (inactiveProfiles.containsKey(uniqueId)) {
            ClientProfile activatedProfile = inactiveProfiles.remove(uniqueId);
            clientProfiles.put(clientKey, activatedProfile);
        }
    }

    public void clearProfiles() {
        clientProfiles.clear();
        inactiveProfiles.clear();
        playerUUIDToProfile.clear();
    }

    /**
     * Associates a player UUID with a client profile for quick lookup
     * @param playerUUID The UUID of the player object
     * @param key The connection key of the client
     */
    public void addPlayerMapping(String playerUUID, ClientConnectionKey key) {
        ClientProfile profile = getClientProfile(key);
        if (profile != null) {
            playerUUIDToProfile.put(playerUUID, profile);
        }
    }

    /**
     * Removes the player UUID mapping when a player is removed
     * @param playerUUID The UUID of the player object
     */
    public void removePlayerMapping(String playerUUID) {
        playerUUIDToProfile.remove(playerUUID);
    }

    /**
     * Gets a client profile by player UUID
     * @param playerUUID The UUID of the player object
     * @return The client profile, or null if not found
     */
    public ClientProfile getClientProfileByPlayerUUID(String playerUUID) {
        return playerUUIDToProfile.get(playerUUID);
    }

}
