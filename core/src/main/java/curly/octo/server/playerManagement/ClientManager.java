package curly.octo.server.playerManagement;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientManager {
    // Active players
    private HashMap<ClientConnectionKey, ClientProfile> clientProfiles;

    // Connecting players that haven't yet identified themselves.
//    private HashMap<ClientConnectionKey, ClientProfile> unidentifiedProfiles;

    // Non-active players. They disconnected for some reason. We'll keep their profile safe in case they come back.
    private HashMap<ClientUniqueId, ClientProfile> inactiveProfiles;

    public ClientManager() {
        clientProfiles = new HashMap<>();
        inactiveProfiles = new HashMap<>();

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
    }

}
