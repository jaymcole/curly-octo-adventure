package curly.octo.network.messages;

import com.badlogic.gdx.math.Vector3;

import java.util.UUID;

public class PlayerUpdate {
    public UUID playerId;
    public float x, y, z;

    public PlayerUpdate() {
    }

    public PlayerUpdate(UUID playerId, Vector3 position) {
        this.playerId = playerId;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
