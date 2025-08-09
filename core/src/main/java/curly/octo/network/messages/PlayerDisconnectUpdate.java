package curly.octo.network.messages;

import java.util.UUID;

public class PlayerDisconnectUpdate {
    public UUID playerId;

    public PlayerDisconnectUpdate() {
    }

    public PlayerDisconnectUpdate(UUID playerId) {
        this.playerId = playerId;
    }
}
