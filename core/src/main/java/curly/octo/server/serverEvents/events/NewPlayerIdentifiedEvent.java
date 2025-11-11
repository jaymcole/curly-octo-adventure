package curly.octo.server.serverEvents.events;

import curly.octo.server.playerManagement.ClientProfile;

public class NewPlayerIdentifiedEvent extends GameEvent {
    private final ClientProfile clientProfile;

    public NewPlayerIdentifiedEvent(ClientProfile clientProfile) {
        this.clientProfile = clientProfile;
    }

    public ClientProfile getClientProfile() {
        return clientProfile;
    }
}
