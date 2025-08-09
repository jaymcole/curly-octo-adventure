package curly.octo.network.messages;

import java.util.UUID;

public class PlayerAssignmentUpdate {
    public UUID playerId;

    public PlayerAssignmentUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerAssignmentUpdate(UUID playerId) {
        this.playerId = playerId;
    }

}
