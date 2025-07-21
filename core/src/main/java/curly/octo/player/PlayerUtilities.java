package curly.octo.player;

import java.util.Random;

public class PlayerUtilities {

    public static PlayerController createPlayerController(Random random) {
        PlayerController newPlayer = new PlayerController();
        newPlayer.setPlayerId(random.nextLong());
        return newPlayer;
    }
}
