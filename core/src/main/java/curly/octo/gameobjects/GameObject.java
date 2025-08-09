package curly.octo.gameobjects;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;


public abstract class GameObject {

    public final String entityId;
    protected Vector3 position;
    protected Quaternion rotation;

    public GameObject(String id) {
        this.entityId = id;
    }

    public abstract void update(float delta);

    public Vector3 getPosition() {
        return position.cpy();
    }

    public Quaternion getRotation() {
        return rotation.cpy();
    }

}
