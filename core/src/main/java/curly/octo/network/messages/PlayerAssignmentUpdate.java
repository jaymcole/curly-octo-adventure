package curly.octo.network.messages;

import curly.octo.map.VoxelMap;

public class PlayerAssignmentUpdate {
    public long playerId;

    public PlayerAssignmentUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerAssignmentUpdate(long playerId) {
        this.playerId = playerId;
    }

}
