package curly.octo.common.lights;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import curly.octo.Main;
import curly.octo.client.GameObjectManager;
import curly.octo.common.GameObject;

import static curly.octo.common.lights.LightPresets.getRandomFlicker;

public class BaseLight extends GameObject{
    public static final float FLICKER_TICK_TIME = 0.1f;

    private final float intensity;

    private GameObject parent;
    private final String parentId;
    private float offsetDistance;
    private Vector3 offsetDirection;

    private transient final Environment environment;
    private final transient PointLight pointLight;
    private final transient GameObjectManager gameObjectManager;

    private final float[] flickerValues;
    private float flickerTime;
    private int flickerIndex = 0;

    public BaseLight(Environment environment, GameObjectManager gameObjectManager, String lightId, float r, float g, float b, float i, String parentId, float[] flicker) {
        super(lightId);
        this.parentId = parentId;
        this.intensity = i;

        if (flicker == null) {
            flickerValues = getRandomFlicker();
        } else {
            this.flickerValues = flicker;
        }
        this.environment = environment;
        this.gameObjectManager = gameObjectManager;
        pointLight = new PointLight();
        pointLight.set(r,g,b,Vector3.Zero, i);
        environment.add(pointLight);
        flickerTime = Main.random.nextFloat();
        flickerIndex = Main.random.nextInt(flickerValues.length);
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

    @Override
    public void setPosition(Vector3 newPosition) {
        pointLight.setPosition(newPosition.cpy());
        this.position = newPosition.cpy();
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
            while (flickerTime > FLICKER_TICK_TIME) {
                flickerTime -= FLICKER_TICK_TIME;
            }
            flickerIndex++;
            flickerIndex %= flickerValues.length;
            pointLight.intensity = intensity * flickerValues[flickerIndex];
        }
    }
}
