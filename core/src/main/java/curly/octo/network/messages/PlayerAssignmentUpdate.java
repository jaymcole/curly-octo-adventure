package curly.octo.network.messages;

import curly.octo.network.NetworkMessage;

public class PlayerAssignmentUpdate extends NetworkMessage {
    public String playerId;

    public PlayerAssignmentUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerAssignmentUpdate(String playerId) {
        this.playerId = playerId;
    }

}
