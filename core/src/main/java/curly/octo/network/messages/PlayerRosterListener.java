package curly.octo.network.messages;

import curly.octo.player.PlayerController;

public interface PlayerRosterListener {
    public void onPlayerRosterReceived(PlayerController newPlayer);
}
