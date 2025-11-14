package curly.octo.server.serverAgents;
import curly.octo.server.ServerGameObjectManager;

public abstract class BaseAgent {

    protected ServerGameObjectManager objectManager;

    public BaseAgent(ServerGameObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public abstract void update(float deltaTime);
}
