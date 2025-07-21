package curly.octo.network.messages;

import com.badlogic.gdx.math.Vector3;

public class PlayerUpdate {
    public long playerId;
    public float x, y, z;

    public PlayerUpdate() {
    }

    public PlayerUpdate(long playerId, Vector3 position) {
        this.playerId = playerId;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }
}
