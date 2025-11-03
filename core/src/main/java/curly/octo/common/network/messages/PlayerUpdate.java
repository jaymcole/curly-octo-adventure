package curly.octo.common.network.messages;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.network.NetworkMessage;

public class PlayerUpdate extends NetworkMessage {
    public String playerId;
    public float x, y, z;
    public float yaw, pitch;

    public PlayerUpdate() {
    }

    public PlayerUpdate(String playerId, Vector3 position) {
        this.playerId = playerId;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.yaw = 0f;
        this.pitch = 0f;
    }

    public PlayerUpdate(String playerId, Vector3 position, float yaw, float pitch) {
        this.playerId = playerId;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
