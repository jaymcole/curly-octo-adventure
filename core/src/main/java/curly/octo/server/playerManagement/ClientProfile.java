package curly.octo.server.playerManagement;

public class ClientProfile {

    public ConnectionStatus connectionStatus;
    public String currentState;
    public String clientUniqueId;
    public String userName;

    // Connection tracking
    public int gameplayConnectionId = -1;      // The gameplay connection ID (always set)
    public Integer bulkConnectionId = null;    // The bulk connection ID (null until bulk connected)

    public ClientProfile() {
        connectionStatus = ConnectionStatus.CONNECTED;
    }

}
