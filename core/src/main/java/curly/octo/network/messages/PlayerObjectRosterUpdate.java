package curly.octo.network.messages;

import curly.octo.gameobjects.PlayerObject;

public class PlayerObjectRosterUpdate {
    public PlayerObject[] players;

    public PlayerObjectRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerObjectRosterUpdate(PlayerObject[] players) {
        this.players = players;
    }
}