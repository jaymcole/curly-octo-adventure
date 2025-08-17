package curly.octo.network.messages;


public interface PlayerRosterListener {
    void onPlayerRosterReceived(PlayerObjectRosterUpdate playerRosterUpdate);
}
