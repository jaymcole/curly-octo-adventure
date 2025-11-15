package curly.octo.common.network.messages;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.network.NetworkMessage;

public class PlayerImpulseMessage extends NetworkMessage {
    public String playerId;
    public float impulseX, impulseY, impulseZ;

    public PlayerImpulseMessage() {
    }

    public PlayerImpulseMessage(String playerId, Vector3 impulse) {
        this.playerId = playerId;
        this.impulseX = impulse.x;
        this.impulseY = impulse.y;
        this.impulseZ = impulse.z;
    }

    public PlayerImpulseMessage(String playerId, float x, float y, float z) {
        this.playerId = playerId;
        this.impulseX = x;
        this.impulseY = y;
        this.impulseZ = z;
    }
}
