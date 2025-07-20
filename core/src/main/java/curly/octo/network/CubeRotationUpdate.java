package curly.octo.network;

import com.badlogic.gdx.math.Quaternion;

/**
 * Network message for updating cube rotation across clients
 */
public class CubeRotationUpdate {
    public float x, y, z, w;
    
    // Default constructor required for KryoNet
    public CubeRotationUpdate() {
    }
    
    public CubeRotationUpdate(Quaternion rotation) {
        this.x = rotation.x;
        this.y = rotation.y;
        this.z = rotation.z;
        this.w = rotation.w;
    }
    
    public Quaternion getRotation() {
        return new Quaternion(x, y, z, w);
    }
}
