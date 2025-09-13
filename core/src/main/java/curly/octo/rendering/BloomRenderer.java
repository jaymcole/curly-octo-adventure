package curly.octo.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;

/**
 * Handles bloom post-processing effect with heat distortion for lava.
 */
public class BloomRenderer implements Disposable {

    private int width;
    private int height;

    // Framebuffers for multi-pass rendering
    private FrameBuffer sceneFrameBuffer;
    private FrameBuffer bloomExtractFrameBuffer;
    private FrameBuffer bloomBlurFrameBuffer1;
    private FrameBuffer bloomBlurFrameBuffer2;

    // Shaders for bloom pipeline
    private ShaderProgram bloomExtractShader;
    private ShaderProgram bloomBlurShader;
    private ShaderProgram bloomCompositeShader;
    private ShaderProgram debugPassthroughShader;
    private ShaderProgram debugSolidShader;

    // Fullscreen quad mesh for direct OpenGL rendering
    private Mesh fullscreenQuad;
    private Matrix4 identityMatrix;

    // Bloom parameters - make them more aggressive for testing
    private float bloomThreshold = 0.5f;  // Lower threshold to capture more pixels
    private float bloomIntensity = 2.0f;  // Higher intensity for visible effect

    // OpenGL state storage for restoration
    private boolean depthTestEnabled;
    private boolean blendEnabled;
    private int srcFactor, dstFactor;
    private boolean cullFaceEnabled;
    private boolean polygonOffsetEnabled;

    private boolean disposed = false;

    public BloomRenderer(int width, int height) {
        this.width = width;
        this.height = height;

        identityMatrix = new Matrix4();

        initializeFrameBuffers();
        loadShaders();
        createFullscreenQuad();
    }

    private void initializeFrameBuffers() {
        // Main scene buffer (full resolution) with depth buffer
        sceneFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);

        // Try to improve depth buffer precision by enabling depth clamping
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);  // Use LEQUAL instead of LESS for better precision

        // Bloom extraction buffer (half resolution for performance, no depth needed)
        int bloomWidth = width / 2;
        int bloomHeight = height / 2;
        bloomExtractFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
        bloomBlurFrameBuffer1 = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
        bloomBlurFrameBuffer2 = new FrameBuffer(Pixmap.Format.RGBA8888, bloomWidth, bloomHeight, false);
    }

    private void loadShaders() {
        // Load bloom extraction shader
        String extractVertexShader = Gdx.files.internal("shaders/bloom_extract.vertex.glsl").readString();
        String extractFragmentShader = Gdx.files.internal("shaders/bloom_extract.fragment.glsl").readString();
        bloomExtractShader = new ShaderProgram(extractVertexShader, extractFragmentShader);
        if (!bloomExtractShader.isCompiled()) {
            Log.error("BloomRenderer", "Bloom extract shader compilation failed: " + bloomExtractShader.getLog());
            throw new RuntimeException("Bloom extract shader compilation failed");
        }

        // Load bloom blur shader
        String blurVertexShader = Gdx.files.internal("shaders/bloom_blur.vertex.glsl").readString();
        String blurFragmentShader = Gdx.files.internal("shaders/bloom_blur.fragment.glsl").readString();
        bloomBlurShader = new ShaderProgram(blurVertexShader, blurFragmentShader);
        if (!bloomBlurShader.isCompiled()) {
            Log.error("BloomRenderer", "Bloom blur shader compilation failed: " + bloomBlurShader.getLog());
            throw new RuntimeException("Bloom blur shader compilation failed");
        }

        // Load bloom composite shader
        String compositeVertexShader = Gdx.files.internal("shaders/bloom_composite.vertex.glsl").readString();
        String compositeFragmentShader = Gdx.files.internal("shaders/bloom_composite.fragment.glsl").readString();
        bloomCompositeShader = new ShaderProgram(compositeVertexShader, compositeFragmentShader);
        if (!bloomCompositeShader.isCompiled()) {
            Log.error("BloomRenderer", "Bloom composite shader compilation failed: " + bloomCompositeShader.getLog());
            throw new RuntimeException("Bloom composite shader compilation failed");
        }

        // Load debug passthrough shader
        String debugVertexShader = Gdx.files.internal("shaders/debug_passthrough.vertex.glsl").readString();
        String debugFragmentShader = Gdx.files.internal("shaders/debug_passthrough.fragment.glsl").readString();
        debugPassthroughShader = new ShaderProgram(debugVertexShader, debugFragmentShader);
        if (!debugPassthroughShader.isCompiled()) {
            Log.error("BloomRenderer", "Debug passthrough shader compilation failed: " + debugPassthroughShader.getLog());
            throw new RuntimeException("Debug passthrough shader compilation failed");
        }

        // Load debug solid shader
        String debugSolidFragmentShader = Gdx.files.internal("shaders/debug_solid.fragment.glsl").readString();
        debugSolidShader = new ShaderProgram(debugVertexShader, debugSolidFragmentShader);
        if (!debugSolidShader.isCompiled()) {
            Log.error("BloomRenderer", "Debug solid shader compilation failed: " + debugSolidShader.getLog());
            throw new RuntimeException("Debug solid shader compilation failed");
        }
    }

    private void createFullscreenQuad() {
        // Create a fullscreen quad mesh with position and texture coordinates
        float[] vertices = {
            // Position (x, y, z) + TexCoord (u, v)
            -1f, -1f, 0f, 0f, 0f,  // Bottom left
             1f, -1f, 0f, 1f, 0f,  // Bottom right
             1f,  1f, 0f, 1f, 1f,  // Top right
            -1f,  1f, 0f, 0f, 1f   // Top left
        };

        short[] indices = {
            0, 1, 2,  // First triangle
            2, 3, 0   // Second triangle
        };

        fullscreenQuad = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        );

        fullscreenQuad.setVertices(vertices);
        fullscreenQuad.setIndices(indices);
    }

    /**
     * Begin rendering to the scene framebuffer.
     */
    public void beginSceneRender() {
        // Store current OpenGL state
        storeGLState();

        sceneFrameBuffer.begin();

        // CRITICAL: Set viewport to match framebuffer size
        // This ensures 3D rendering uses the correct coordinate system
        Gdx.gl.glViewport(0, 0, width, height);

        // Ensure depth testing is properly configured for framebuffer rendering
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        Gdx.gl.glDepthMask(true);

        // Disable multisampling if it was enabled - framebuffers don't support it the same way
        Gdx.gl.glDisable(GL20.GL_SAMPLE_COVERAGE);

        // Enable polygon offset fill to help with Z-fighting in framebuffer
        // Using moderate values to eliminate gaps without visual artifacts
        Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
        Gdx.gl.glPolygonOffset(0.5f, 0.5f);

        // Clear framebuffer for 3D scene rendering
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1.0f); // Dark blue sky color
        Gdx.gl.glClearDepthf(1.0f); // Ensure depth buffer is cleared to far plane
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * End scene rendering and apply bloom post-processing.
     */
    public void endSceneRenderAndApplyBloom() {
        sceneFrameBuffer.end();

        // Apply full bloom post-processing pipeline
        extractBrightAreas();
        blurBrightAreas();
        compositeBloom();

        // Restore OpenGL state for subsequent rendering
        restoreGLState();
    }

    /**
     * Debug method: Test scene framebuffer texture sampling
     */
    private void testPassthroughRender() {
        Log.info("BloomRenderer", "Testing scene framebuffer texture sampling");

        // Clear to blue background to distinguish from scene content
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Check for OpenGL errors before starting
        int error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            Log.error("BloomRenderer", "OpenGL error before passthrough: " + error);
        }

        // Disable depth testing for 2D post-processing
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Check if debug passthrough shader compiled successfully
        if (!debugPassthroughShader.isCompiled()) {
            Log.error("BloomRenderer", "Debug passthrough shader not compiled: " + debugPassthroughShader.getLog());
            return;
        }

        Log.info("BloomRenderer", "Scene framebuffer size: " + sceneFrameBuffer.getWidth() + "x" + sceneFrameBuffer.getHeight());
        Log.info("BloomRenderer", "Screen size: " + width + "x" + height);

        debugPassthroughShader.begin();

        // Set uniforms
        debugPassthroughShader.setUniformMatrix("u_projTrans", identityMatrix);

        // Ensure texture unit 0 is active before binding
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        // Bind scene texture to unit 0
        sceneFrameBuffer.getColorBufferTexture().bind(0);
        debugPassthroughShader.setUniformi("u_texture", 0);

        // Log texture binding info
        Log.info("BloomRenderer", "Texture ID: " + sceneFrameBuffer.getColorBufferTexture().getTextureObjectHandle());
        Log.info("BloomRenderer", "Texture bound to unit 0, uniform set to 0");

        Log.info("BloomRenderer", "Uniforms set, about to render quad with scene texture");

        // Check for OpenGL errors after uniform setup
        error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            Log.error("BloomRenderer", "OpenGL error after uniforms: " + error);
        }

        // Render fullscreen quad sampling from scene framebuffer
        fullscreenQuad.render(debugPassthroughShader, GL20.GL_TRIANGLES);

        // Check for OpenGL errors after rendering
        error = Gdx.gl.glGetError();
        if (error != GL20.GL_NO_ERROR) {
            Log.error("BloomRenderer", "OpenGL error after quad render: " + error);
        }

        debugPassthroughShader.end();

        Log.info("BloomRenderer", "Scene texture passthrough complete");
        Log.info("BloomRenderer", "Expected: 3D scene if framebuffer captured correctly");
        Log.info("BloomRenderer", "If you see blue: scene framebuffer is empty/black");
        Log.info("BloomRenderer", "If you see black: texture sampling failed");
    }

    private void extractBrightAreas() {
        bloomExtractFrameBuffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Set viewport for bloom buffer
        Gdx.gl.glViewport(0, 0, bloomExtractFrameBuffer.getWidth(), bloomExtractFrameBuffer.getHeight());

        // Disable depth testing for 2D post-processing
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        // Use bloom extract shader
        bloomExtractShader.begin();
        bloomExtractShader.setUniformMatrix("u_projTrans", identityMatrix);
        bloomExtractShader.setUniformf("u_bloomThreshold", bloomThreshold);

        // Bind scene texture
        sceneFrameBuffer.getColorBufferTexture().bind(0);
        bloomExtractShader.setUniformi("u_texture", 0);

        Log.debug("BloomRenderer", "Extracting bright areas with threshold: " + bloomThreshold);

        // Render fullscreen quad
        fullscreenQuad.render(bloomExtractShader, GL20.GL_TRIANGLES);

        bloomExtractShader.end();
        bloomExtractFrameBuffer.end();
    }

    private void blurBrightAreas() {
        int bloomWidth = bloomExtractFrameBuffer.getWidth();
        int bloomHeight = bloomExtractFrameBuffer.getHeight();

        // Disable depth testing for 2D post-processing
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        // Horizontal blur pass
        bloomBlurFrameBuffer1.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);

        bloomBlurShader.begin();
        bloomBlurShader.setUniformMatrix("u_projTrans", identityMatrix);
        bloomBlurShader.setUniformf("u_direction", 1.0f, 0.0f); // Horizontal
        bloomBlurShader.setUniformf("u_resolution", bloomWidth, bloomHeight);

        // Bind extracted bright texture
        bloomExtractFrameBuffer.getColorBufferTexture().bind(0);
        bloomBlurShader.setUniformi("u_texture", 0);

        // Render fullscreen quad
        fullscreenQuad.render(bloomBlurShader, GL20.GL_TRIANGLES);

        bloomBlurShader.end();
        bloomBlurFrameBuffer1.end();

        // Vertical blur pass
        bloomBlurFrameBuffer2.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glViewport(0, 0, bloomWidth, bloomHeight);

        bloomBlurShader.begin();
        bloomBlurShader.setUniformMatrix("u_projTrans", identityMatrix);
        bloomBlurShader.setUniformf("u_direction", 0.0f, 1.0f); // Vertical
        bloomBlurShader.setUniformf("u_resolution", bloomWidth, bloomHeight);

        // Bind horizontally blurred texture
        bloomBlurFrameBuffer1.getColorBufferTexture().bind(0);
        bloomBlurShader.setUniformi("u_texture", 0);

        // Render fullscreen quad
        fullscreenQuad.render(bloomBlurShader, GL20.GL_TRIANGLES);

        bloomBlurShader.end();
        bloomBlurFrameBuffer2.end();
    }

    private void compositeBloom() {
        // Render to screen (no framebuffer)
        Gdx.gl.glViewport(0, 0, width, height);

        // Disable depth testing for 2D post-processing
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        // Enable blending for proper compositing
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

        bloomCompositeShader.begin();

        // Set uniforms
        bloomCompositeShader.setUniformMatrix("u_projTrans", identityMatrix);
        bloomCompositeShader.setUniformf("u_bloomIntensity", bloomIntensity);
        bloomCompositeShader.setUniformf("u_time", (System.currentTimeMillis() % 60000) / 1000.0f);

        // Bind scene texture to unit 0
        sceneFrameBuffer.getColorBufferTexture().bind(0);
        bloomCompositeShader.setUniformi("u_texture", 0);

        // Bind bloom texture to unit 1
        bloomBlurFrameBuffer2.getColorBufferTexture().bind(1);
        bloomCompositeShader.setUniformi("u_bloomTexture", 1);

        Log.debug("BloomRenderer", "Compositing bloom with intensity: " + bloomIntensity);

        // Render fullscreen quad
        fullscreenQuad.render(bloomCompositeShader, GL20.GL_TRIANGLES);

        bloomCompositeShader.end();
    }

    /**
     * Set bloom parameters.
     */
    public void setBloomParameters(float threshold, float intensity) {
        this.bloomThreshold = threshold;
        this.bloomIntensity = intensity;
    }

    public float getBloomThreshold() {
        return bloomThreshold;
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    /**
     * Get the scene framebuffer for external rendering systems that need to restore it.
     */
    public FrameBuffer getSceneFrameBuffer() {
        return sceneFrameBuffer;
    }

    /**
     * Resize the bloom renderer framebuffers when the window size changes.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return; // No change needed
        }

        Log.info("BloomRenderer", "Resizing from " + width + "x" + height + " to " + newWidth + "x" + newHeight);

        // Dispose old framebuffers
        if (sceneFrameBuffer != null) sceneFrameBuffer.dispose();
        if (bloomExtractFrameBuffer != null) bloomExtractFrameBuffer.dispose();
        if (bloomBlurFrameBuffer1 != null) bloomBlurFrameBuffer1.dispose();
        if (bloomBlurFrameBuffer2 != null) bloomBlurFrameBuffer2.dispose();

        // Update dimensions
        this.width = newWidth;
        this.height = newHeight;

        // Recreate framebuffers with new size
        initializeFrameBuffers();

        Log.info("BloomRenderer", "Bloom renderer resized successfully");
    }

    @Override
    public void dispose() {
        if (disposed) return;

        if (sceneFrameBuffer != null) sceneFrameBuffer.dispose();
        if (bloomExtractFrameBuffer != null) bloomExtractFrameBuffer.dispose();
        if (bloomBlurFrameBuffer1 != null) bloomBlurFrameBuffer1.dispose();
        if (bloomBlurFrameBuffer2 != null) bloomBlurFrameBuffer2.dispose();

        if (bloomExtractShader != null) bloomExtractShader.dispose();
        if (bloomBlurShader != null) bloomBlurShader.dispose();
        if (bloomCompositeShader != null) bloomCompositeShader.dispose();
        if (debugPassthroughShader != null) debugPassthroughShader.dispose();

        if (fullscreenQuad != null) fullscreenQuad.dispose();

        disposed = true;
        Log.info("BloomRenderer", "Disposed bloom renderer");
    }

    /**
     * Store current OpenGL state for later restoration.
     */
    private void storeGLState() {
        // Store blend state
        blendEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        if (blendEnabled) {
            // Note: LibGDX doesn't provide direct access to blend func state
            // We'll assume standard alpha blending and restore it
        }

        // Store depth test state
        depthTestEnabled = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);

        // Store cull face state
        cullFaceEnabled = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);

        // Store polygon offset state
        polygonOffsetEnabled = Gdx.gl.glIsEnabled(GL20.GL_POLYGON_OFFSET_FILL);

        // Note: We don't need to store viewport as we'll reset it to screen size
    }

    /**
     * Restore OpenGL state after bloom processing.
     */
    private void restoreGLState() {
        // Restore viewport to full screen
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Restore depth test
        if (depthTestEnabled) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        } else {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }

        // Restore cull face
        if (cullFaceEnabled) {
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        } else {
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        }

        // Restore blend state
        if (blendEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            // Restore standard alpha blending
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // Restore polygon offset state
        if (polygonOffsetEnabled) {
            Gdx.gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
        } else {
            Gdx.gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
        }

        // Ensure no shader is bound
        Gdx.gl.glUseProgram(0);

        // Ensure texture unit 0 is active
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }
}
