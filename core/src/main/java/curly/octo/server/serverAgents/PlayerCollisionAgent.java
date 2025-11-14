package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.WorldObject;
import curly.octo.server.ServerGameObjectManager;

public class PlayerCollisionAgent extends BaseAgent {

    public PlayerCollisionAgent(ServerGameObjectManager objectManager) {
        super(objectManager);
    }

    @Override
    public void update(float deltaTime) {
        if (objectManager == null) {
            return;
        }
        for(int i = 0; i < objectManager.activePlayers.size(); i++) {
            for(int j = i+1; j < objectManager.activePlayers.size(); j++) {
                WorldObject player1 = objectManager.activePlayers.get(i);
                WorldObject player2 = objectManager.activePlayers.get(j);
                float distance = distance(player1.getPosition(), player2.getPosition());
                if (distance < 10) {
                    Log.info("PlayerCollisionAgent", player1.entityId + " is near " + player2.entityId + "(" + distance + ")");
                }
            }
        }
    }

    private float distance(Vector3 p1, Vector3 p2) {
        return Vector3.dst(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z);
    }
}
