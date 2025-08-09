package curly.octo.player;

import java.util.Random;
import java.util.UUID;

public class PlayerUtilities {

    public static PlayerController createPlayerController() {
        PlayerController newPlayer = new PlayerController();
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        return newPlayer;
    }

    /**
     * Creates a server-only player controller that skips graphics initialization.
     * Used by GameServer for tracking player state without rendering overhead.
     */
    public static PlayerController createServerPlayerController() {
        PlayerController newPlayer = new PlayerController(true); // true = server-only
        newPlayer.setPlayerId(UUID.randomUUID().toString());
        return newPlayer;
    }
}
