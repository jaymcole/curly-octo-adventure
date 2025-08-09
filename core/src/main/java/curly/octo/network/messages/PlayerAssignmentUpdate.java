package curly.octo.network.messages;

public class PlayerAssignmentUpdate {
    public String playerId;

    public PlayerAssignmentUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerAssignmentUpdate(String playerId) {
        this.playerId = playerId;
    }

}
