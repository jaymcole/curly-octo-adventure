package curly.octo.game.serverObjects;

public class ClientProfile {

    public ConnectionStatus connectionStatus;
    public String currentState;

    public ClientProfile() {
        connectionStatus = ConnectionStatus.CONNECTED;
    }

}
