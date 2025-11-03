package curly.octo.common.network.messages;


public interface PlayerRosterListener {
    void onPlayerRosterReceived(PlayerObjectRosterUpdate playerRosterUpdate);
}
