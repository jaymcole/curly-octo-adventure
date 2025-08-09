package curly.octo.player;

import java.util.Random;
import java.util.UUID;

public class PlayerUtilities {

    public static PlayerController createPlayerController() {
        PlayerController newPlayer = new PlayerController();
        newPlayer.setPlayerId(UUID.randomUUID());
        return newPlayer;
    }
}
