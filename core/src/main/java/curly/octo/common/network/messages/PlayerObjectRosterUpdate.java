package curly.octo.common.network.messages;

import curly.octo.common.character.WalkingCharacter;
import curly.octo.common.network.NetworkMessage;

public class PlayerObjectRosterUpdate extends NetworkMessage {
    public WalkingCharacter[] players;

    public PlayerObjectRosterUpdate() {
        // Default constructor required for Kryo
    }

    public PlayerObjectRosterUpdate(WalkingCharacter[] players) {
        this.players = players;
    }
}
