package curly.octo.gameobjects;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;


public abstract class GameObject {

    public String entityId;
    protected Vector3 position;
    protected Quaternion rotation;

    // No-arg constructor for Kryo serialization
    protected GameObject() {
        this.entityId = "unknown";
        this.position = new Vector3();
        this.rotation = new Quaternion();
    }

    public GameObject(String id) {
        this.entityId = id;
        this.position = new Vector3();
        this.rotation = new Quaternion();
    }

    public abstract void update(float delta);

    public void setPosition(Vector3 newPosition) {
        this.position = newPosition.cpy();
    }

    public Vector3 getPosition() {
        if (position == null) {
            position = new Vector3();
        }
        return position.cpy();
    }

    public Quaternion getRotation() {
        if (rotation == null) {
            rotation = new Quaternion();
        }
        return rotation.cpy();
    }

}
