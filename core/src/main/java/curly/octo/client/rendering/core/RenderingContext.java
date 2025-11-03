package curly.octo.client.rendering.core;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Encapsulates all the state and resources needed for rendering.
 * Reduces parameter passing and provides a central place for rendering configuration.
 */
public class RenderingContext implements Disposable {

    // Core rendering inputs
    private PerspectiveCamera camera;
    private Environment environment;
    private Array<ModelInstance> instances;
    private Array<ModelInstance> additionalInstances;

    // Rendering targets
    private FrameBuffer targetFrameBuffer;

    // Rendering state
    private boolean disposed = false;

    /**
     * Creates a new rendering context with the minimum required components.
     *
     * @param camera The perspective camera for rendering
     * @param environment The lighting environment
     * @param instances The primary model instances to render
     */
    public RenderingContext(PerspectiveCamera camera, Environment environment, Array<ModelInstance> instances) {
        this.camera = camera;
        this.environment = environment;
        this.instances = instances;
        this.additionalInstances = new Array<>();
        this.targetFrameBuffer = null;
    }

    // Getters
    public PerspectiveCamera getCamera() {
        return camera;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Array<ModelInstance> getInstances() {
        return instances;
    }

    public Array<ModelInstance> getAdditionalInstances() {
        return additionalInstances;
    }

    /**
     * Gets all instances to render (primary + additional).
     *
     * @return Combined array of all instances
     */
    public Array<ModelInstance> getAllInstances() {
        Array<ModelInstance> allInstances = new Array<>(instances);
        if (additionalInstances != null && additionalInstances.size > 0) {
            allInstances.addAll(additionalInstances);
        }
        return allInstances;
    }

    public FrameBuffer getTargetFrameBuffer() {
        return targetFrameBuffer;
    }

    // Setters
    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public void setInstances(Array<ModelInstance> instances) {
        this.instances = instances;
    }

    public void setAdditionalInstances(Array<ModelInstance> additionalInstances) {
        this.additionalInstances = additionalInstances;
    }

    public void setTargetFrameBuffer(FrameBuffer targetFrameBuffer) {
        this.targetFrameBuffer = targetFrameBuffer;
    }

    /**
     * Updates the context with new rendering parameters.
     * Useful for reusing the same context across multiple frames.
     */
    public void update(PerspectiveCamera camera, Environment environment,
                      Array<ModelInstance> instances, Array<ModelInstance> additionalInstances) {
        this.camera = camera;
        this.environment = environment;
        this.instances = instances;
        this.additionalInstances = additionalInstances;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        // Note: We don't dispose camera, environment, or instances here
        // as they are managed externally. This is just for future resource cleanup.

        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
