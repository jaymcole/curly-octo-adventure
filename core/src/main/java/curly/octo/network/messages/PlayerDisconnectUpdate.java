package curly.octo.network.messages;

public class PlayerDisconnectUpdate {
    public long playerId;

    public PlayerDisconnectUpdate() {
    }

    public PlayerDisconnectUpdate(long playerId) {
        this.playerId = playerId;
    }
}