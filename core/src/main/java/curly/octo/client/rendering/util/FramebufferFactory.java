package curly.octo.client.rendering.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.esotericsoftware.minlog.Log;

/**
 * Factory for creating and managing framebuffers with consistent configuration.
 * Centralizes framebuffer creation to ensure proper format and error handling.
 */
public class FramebufferFactory {

    /**
     * Creates a standard framebuffer with color and depth attachments.
     *
     * @param width Framebuffer width
     * @param height Framebuffer height
     * @param hasDepth Whether to include depth attachment
     * @return The created framebuffer
     */
    public static FrameBuffer createColorBuffer(int width, int height, boolean hasDepth) {
        try {
            FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, hasDepth);
            Log.info("FramebufferFactory", "Created color framebuffer: " + width + "x" + height + " (depth=" + hasDepth + ")");
            return fbo;
        } catch (Exception e) {
            Log.error("FramebufferFactory", "Failed to create color framebuffer: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a depth-only framebuffer for shadow mapping.
     *
     * @param size Square size of the shadow map (width and height)
     * @return The created depth framebuffer
     */
    public static FrameBuffer createDepthBuffer(int size) {
        try {
            FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, true);
            return fbo;
        } catch (Exception e) {
            Log.error("FramebufferFactory", "Failed to create depth framebuffer: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Creates an array of framebuffers (useful for cube shadow maps).
     *
     * @param count Number of framebuffers to create
     * @param size Size of each framebuffer
     * @param hasDepth Whether framebuffers should have depth
     * @return Array of framebuffers
     */
    public static FrameBuffer[] createFramebufferArray(int count, int size, boolean hasDepth) {
        FrameBuffer[] buffers = new FrameBuffer[count];
        for (int i = 0; i < count; i++) {
            buffers[i] = createColorBuffer(size, size, hasDepth);
        }
        Log.info("FramebufferFactory", "Created " + count + " framebuffers of size " + size + "x" + size);
        return buffers;
    }

    /**
     * Creates a 2D array of framebuffers (useful for cube shadow maps with multiple lights).
     *
     * @param numLights Number of lights (first dimension)
     * @param numFaces Number of faces per light (second dimension, typically 6 for cube maps)
     * @param size Size of each framebuffer
     * @return 2D array of framebuffers
     */
    public static FrameBuffer[][] createCubeShadowMapArray(int numLights, int numFaces, int size) {
        FrameBuffer[][] buffers = new FrameBuffer[numLights][numFaces];
        for (int light = 0; light < numLights; light++) {
            for (int face = 0; face < numFaces; face++) {
                buffers[light][face] = createDepthBuffer(size);
            }
        }
        Log.info("FramebufferFactory", "Created cube shadow map array: " + numLights + " lights x " + numFaces + " faces, " + size + "x" + size + " each");
        return buffers;
    }

    /**
     * Safely disposes a framebuffer if it exists.
     *
     * @param fbo The framebuffer to dispose (can be null)
     */
    public static void dispose(FrameBuffer fbo) {
        if (fbo != null) {
            fbo.dispose();
        }
    }

    /**
     * Disposes an array of framebuffers.
     *
     * @param fbos The framebuffer array to dispose
     */
    public static void disposeArray(FrameBuffer[] fbos) {
        if (fbos != null) {
            for (FrameBuffer fbo : fbos) {
                dispose(fbo);
            }
        }
    }

    /**
     * Disposes a 2D array of framebuffers.
     *
     * @param fbos The 2D framebuffer array to dispose
     */
    public static void dispose2DArray(FrameBuffer[][] fbos) {
        if (fbos != null) {
            for (FrameBuffer[] fboArray : fbos) {
                disposeArray(fboArray);
            }
        }
    }

    /**
     * Validates that a framebuffer was created successfully.
     *
     * @param fbo The framebuffer to validate
     * @return true if valid, false otherwise
     */
    public static boolean validate(FrameBuffer fbo) {
        if (fbo == null) {
            return false;
        }

        fbo.begin();
        int status = Gdx.gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
        fbo.end();

        if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
            Log.error("FramebufferFactory", "Framebuffer incomplete! Status: " + status);
            return false;
        }

        return true;
    }
}
