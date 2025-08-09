package curly.octo.network.messages;

public class PlayerDisconnectUpdate {
    public String playerId;

    public PlayerDisconnectUpdate() {
    }

    public PlayerDisconnectUpdate(String playerId) {
        this.playerId = playerId;
    }
}
