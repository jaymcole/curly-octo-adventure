package curly.octo.network.messages;

import curly.octo.player.PlayerController;

public class PlayerRosterUpdate {
    public PlayerController[] players;

    public PlayerRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerRosterUpdate(PlayerController[] players) {
        this.players = players;
    }
}
