package curly.octo.player;

import curly.octo.gameobjects.PlayerObject;
import java.util.Random;
import java.util.UUID;

public class PlayerUtilities {

    public static PlayerObject createPlayerObject() {
        String playerId = UUID.randomUUID().toString();
        return new PlayerObject(playerId);
    }

    /**
     * Creates a server-only player object that skips graphics initialization.
     * Used by GameServer for tracking player state without rendering overhead.
     */
    public static PlayerObject createServerPlayerObject() {
        String playerId = UUID.randomUUID().toString();
        return new PlayerObject(playerId, true); // true = server-only
    }
}
