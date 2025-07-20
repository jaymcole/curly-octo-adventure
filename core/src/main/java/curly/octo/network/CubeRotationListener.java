package curly.octo.network;

import com.badlogic.gdx.math.Quaternion; /**
 * Callback interface for cube rotation updates
 */
public interface CubeRotationListener {
    void onCubeRotationUpdate(Quaternion rotation);
}
