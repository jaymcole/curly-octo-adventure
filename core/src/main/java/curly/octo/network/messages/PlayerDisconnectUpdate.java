package curly.octo.network.messages;

import curly.octo.network.NetworkMessage;

public class PlayerDisconnectUpdate extends NetworkMessage {
    public String playerId;

    public PlayerDisconnectUpdate() {
    }

    public PlayerDisconnectUpdate(String playerId) {
        this.playerId = playerId;
    }
}
