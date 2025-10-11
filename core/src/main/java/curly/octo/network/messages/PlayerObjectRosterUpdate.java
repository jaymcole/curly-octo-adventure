package curly.octo.network.messages;

import curly.octo.gameobjects.PlayerObject;
import curly.octo.network.NetworkMessage;

public class PlayerObjectRosterUpdate extends NetworkMessage {
    public PlayerObject[] players;

    public PlayerObjectRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerObjectRosterUpdate(PlayerObject[] players) {
        this.players = players;
    }
}