package curly.octo.gameobjects;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.UUID;

public abstract class GameObject {

    public final UUID entityId;
    protected Vector3 position;
    protected Quaternion rotation;

    public GameObject(UUID id) {
        this.entityId = id;
    }

    public GameObject() {
        this.entityId = UUID.randomUUID();
    }

    public abstract void update(float delta);

    public Vector3 getPosition() {
        return position.cpy();
    }

    public Quaternion getRotation() {
        return rotation.cpy();
    }

}
