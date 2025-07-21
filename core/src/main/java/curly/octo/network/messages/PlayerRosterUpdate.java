package curly.octo.network.messages;

import curly.octo.player.PlayerController;

public class PlayerRosterUpdate {
    public PlayerController newPlayer;

    public PlayerRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerRosterUpdate(PlayerController newPlayer) {
        this.newPlayer = newPlayer;
    }
}
