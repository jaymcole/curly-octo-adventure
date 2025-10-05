package curly.octo.game.serverObjects;

public class ClientProfile {

    public ConnectionStatus connectionStatus;
    public String currentState;
    public String clientUniqueId;
    public String userName;

    public ClientProfile() {
        connectionStatus = ConnectionStatus.CONNECTED;
    }

}
