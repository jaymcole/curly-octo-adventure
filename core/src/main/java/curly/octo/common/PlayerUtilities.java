package curly.octo.common;

import curly.octo.common.character.PlayerBrain;
import curly.octo.common.character.WalkingCharacter;

import java.util.UUID;

public class PlayerUtilities {

    public static WalkingCharacter createPlayerObject() {
        String playerId = UUID.randomUUID().toString();
        WalkingCharacter player = new WalkingCharacter(playerId, Constants.PLAYER_HEIGHT, 1.0f);
        player.setBrain(new PlayerBrain());
        return player;
    }

    /**
     * Creates a server-only player object that skips graphics initialization.
     * Used by GameServer for tracking player state without rendering overhead.
     */
    public static WalkingCharacter createServerPlayerObject() {
        String playerId = UUID.randomUUID().toString();
        WalkingCharacter player = new WalkingCharacter(playerId, Constants.PLAYER_HEIGHT, 1.0f);
        player.setBrain(new PlayerBrain());
        return player;
    }
}
