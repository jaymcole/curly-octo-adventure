package lights;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import curly.octo.game.GameObjectManager;
import curly.octo.gameobjects.GameObject;

public abstract class BaseLight extends GameObject{
    public static final float FLICKER_TICK_TIME = 0.01f;

    private float r,g,b,i;
    private float calculatedIntensity;
    private boolean enabled;
    private LightType type;

    private GameObject parent;
    private String parentId;
    private float offsetDistance;
    private Vector3 offsetDirection;

    private final Environment environment;
    private PointLight pointLight;
    private GameObjectManager gameObjectManager;

    private float[] flickerValues;
    private float flickerTime;
    private int flickerIndex = 0;

    public BaseLight(Environment environment, GameObjectManager gameObjectManager, String lightId, float r, float g, float b, float i, LightType type, String parentId) {
        super(lightId);
        this.parentId = parentId;
        this.r = r;
        this.g = g;
        this.b = b;
        this.i = i;
        this.calculatedIntensity = i;
        this.type = type;
        enabled = false;
        flickerValues = new float[]{1f};
        this.environment = environment;
        this.gameObjectManager = gameObjectManager;
    }

    public void destroy() {
        if (environment != null) {
            environment.remove(pointLight);
        }
    }

    @Override
    public void update(float delta) {
        while(parent == null && parentId != null && gameObjectManager != null) {
            parent = gameObjectManager.getObjectById(parentId);
        }
        updateFlicker(delta);
    }

    protected void updatePosition() {
        if (parent != null && pointLight != null && offsetDirection != null) {
            Vector3 worldPosition = parent.getPosition();
            Vector3 rotatedOffset = offsetDirection.cpy().scl(offsetDistance);
            parent.getRotation().transform(rotatedOffset);
            worldPosition.add(rotatedOffset);
            pointLight.position.set(worldPosition);
            this.position = pointLight.position.cpy();
        }
    }

    protected void updateFlicker(float delta) {
        flickerTime+= delta;
        if (flickerTime > FLICKER_TICK_TIME) {
            flickerTime -= FLICKER_TICK_TIME;
            flickerIndex++;
            flickerTime %= flickerValues.length;
            calculatedIntensity = i * flickerValues[flickerIndex];
        }
    }
}
