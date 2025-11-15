package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.WorldObject;
import curly.octo.server.GameServer;
import curly.octo.server.ServerGameObjectManager;

public class PlayerCollisionAgent extends BaseAgent {

    private final GameServer gameServer;
    private static final float COLLISION_DISTANCE = 4.0f; // Distance threshold for collision
    private static final float MAX_IMPULSE_STRENGTH = 0.005f; // Maximum impulse force
    private final Vector3 tempVector = new Vector3();

    public PlayerCollisionAgent(ServerGameObjectManager objectManager, GameServer gameServer) {
        super(objectManager);
        this.gameServer = gameServer;
    }

    @Override
    public void update(float deltaTime) {
        if (objectManager == null || gameServer == null) {
            return;
        }

        // Check all pairs of players for collisions
        for(int i = 0; i < objectManager.activePlayers.size(); i++) {
            for(int j = i+1; j < objectManager.activePlayers.size(); j++) {
                WorldObject player1 = objectManager.activePlayers.get(i);
                WorldObject player2 = objectManager.activePlayers.get(j);

                Vector3 pos1 = player1.getPosition();
                Vector3 pos2 = player2.getPosition();

                if (pos1 == null || pos2 == null) {
                    continue;
                }

                float distance = distance(pos1, pos2);

                // If players are too close, push them apart
                if (distance < COLLISION_DISTANCE && distance > 0.01f) {
                    // Calculate collision normal (direction from player1 to player2)
                    tempVector.set(pos2).sub(pos1).nor();

                    // Calculate impulse strength based on overlap
                    // Stronger push when players are closer together
                    float overlap = COLLISION_DISTANCE - distance;
                    float impulseStrength = Math.min(overlap * 2.0f, MAX_IMPULSE_STRENGTH);

                    // Create impulse vectors (equal and opposite)
                    Vector3 impulse1 = new Vector3(tempVector).scl(-impulseStrength); // Push player1 away
                    Vector3 impulse2 = new Vector3(tempVector).scl(impulseStrength);  // Push player2 away

                    // Send impulses to both players
                    gameServer.sendImpulseToPlayer(player1.entityId, impulse1);
                    gameServer.sendImpulseToPlayer(player2.entityId, impulse2);

                    Log.info("PlayerCollisionAgent", "Collision detected! Player1: " + player1.entityId +
                             ", Player2: " + player2.entityId + ", distance: " + String.format("%.2f", distance) +
                             ", impulseStrength: " + String.format("%.2f", impulseStrength) +
                             ", impulse1: " + impulse1 + ", impulse2: " + impulse2);
                }
            }
        }
    }

    private float distance(Vector3 p1, Vector3 p2) {
        return Vector3.dst(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
    }
}
