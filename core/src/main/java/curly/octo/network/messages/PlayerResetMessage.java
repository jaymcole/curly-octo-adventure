package curly.octo.network.messages;

import com.badlogic.gdx.math.Vector3;
import curly.octo.network.NetworkMessage;

/**
 * Sent by the server to reset player state for new map.
 * Contains new spawn position and any state resets needed.
 */
public class PlayerResetMessage extends NetworkMessage {
    
    /** The player ID being reset */
    public String playerId;
    
    /** New spawn position for the player */
    public float spawnX, spawnY, spawnZ;
    
    /** New orientation (yaw) for the player */
    public float spawnYaw;
    
    /** Whether to reset player inventory/stats (for future expansion) */
    public boolean resetPlayerState;
    
    // No-arg constructor for Kryo serialization
    public PlayerResetMessage() {
    }
    
    public PlayerResetMessage(String playerId, Vector3 spawnPosition, float spawnYaw) {
        this.playerId = playerId;
        this.spawnX = spawnPosition.x;
        this.spawnY = spawnPosition.y;
        this.spawnZ = spawnPosition.z;
        this.spawnYaw = spawnYaw;
        this.resetPlayerState = true;
    }
    
    public PlayerResetMessage(String playerId, float spawnX, float spawnY, float spawnZ, float spawnYaw) {
        this.playerId = playerId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.resetPlayerState = true;
    }
    
    public Vector3 getSpawnPosition() {
        return new Vector3(spawnX, spawnY, spawnZ);
    }
}