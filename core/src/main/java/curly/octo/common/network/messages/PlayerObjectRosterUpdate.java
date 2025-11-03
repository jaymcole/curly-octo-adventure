package curly.octo.common.network.messages;

import curly.octo.common.PlayerObject;
import curly.octo.common.network.NetworkMessage;

public class PlayerObjectRosterUpdate extends NetworkMessage {
    public PlayerObject[] players;

    public PlayerObjectRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerObjectRosterUpdate(PlayerObject[] players) {
        this.players = players;
    }
}
