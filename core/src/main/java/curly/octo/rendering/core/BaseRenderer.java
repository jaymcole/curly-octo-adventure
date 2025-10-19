package curly.octo.rendering.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Abstract base class for all renderers in the system.
 * Provides common rendering patterns like two-pass rendering for transparency,
 * instance rendering, and GL state management.
 */
public abstract class BaseRenderer implements Disposable {

    protected boolean disposed = false;

    /**
     * Callback interface for rendering individual instances.
     * Allows subclasses to customize how instances are rendered.
     */
    @FunctionalInterface
    public interface InstanceRenderer {
        void render(ModelInstance instance, ShaderProgram shader, boolean transparentPass);
    }

    /**
     * Performs two-pass rendering for proper transparency handling.
     * Pass 1: Opaque geometry with depth writes
     * Pass 2: Transparent geometry with blending, no depth writes
     *
     * @param context The rendering context
     * @param shader The shader program to use
     * @param instanceRenderer Callback for rendering individual instances
     */
    protected void renderTwoPass(RenderingContext context,
                                 ShaderProgram shader,
                                 InstanceRenderer instanceRenderer) {
        Array<ModelInstance> allInstances = context.getAllInstances();

        // Pass 1: Render all OPAQUE geometry (writes depth, no blending)
        configureOpaquePass();

        for (ModelInstance instance : allInstances) {
            setInstanceTransforms(shader, instance, context);
            instanceRenderer.render(instance, shader, false);  // false = opaque only
        }

        // Pass 2: Render all TRANSPARENT geometry (no depth writes, blending enabled)
        configureTransparentPass();

        for (ModelInstance instance : allInstances) {
            setInstanceTransforms(shader, instance, context);
            instanceRenderer.render(instance, shader, true);  // true = transparent only
        }

        // Restore GL state
        restoreDefaultGLState();
    }

    /**
     * Configures OpenGL state for opaque rendering pass.
     */
    protected void configureOpaquePass() {
        Gdx.gl.glDepthMask(true);  // Enable depth writes
        Gdx.gl.glDisable(GL20.GL_BLEND);  // Disable blending
    }

    /**
     * Configures OpenGL state for transparent rendering pass.
     */
    protected void configureTransparentPass() {
        Gdx.gl.glDepthMask(false);  // Disable depth writes for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND);  // Enable blending
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Restores default OpenGL state after rendering.
     */
    protected void restoreDefaultGLState() {
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Sets standard transform uniforms for an instance.
     *
     * @param shader The shader program
     * @param instance The model instance
     * @param context The rendering context
     */
    protected void setInstanceTransforms(ShaderProgram shader, ModelInstance instance, RenderingContext context) {
        Matrix4 worldTransform = instance.transform;
        shader.setUniformMatrix("u_worldTrans", worldTransform);
        shader.setUniformMatrix("u_projViewTrans", context.getCamera().combined);
    }

    /**
     * Main render method that subclasses must implement.
     *
     * @param context The rendering context containing camera, environment, and instances
     */
    public abstract void render(RenderingContext context);

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposeResources();
        disposed = true;
    }

    /**
     * Subclasses override this to dispose their specific resources.
     */
    protected abstract void disposeResources();

    public boolean isDisposed() {
        return disposed;
    }
}
