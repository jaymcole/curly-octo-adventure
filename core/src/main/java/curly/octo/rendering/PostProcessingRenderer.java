package curly.octo.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.MapTileFillType;

public class PostProcessingRenderer implements Disposable {

    private int width;
    private int height;

    private FrameBuffer sceneFrameBuffer;
    private FrameBuffer tempFrameBuffer;

    private ShaderProgram underwaterShader;
    private ShaderProgram lavaShader;
    private ShaderProgram fogShader;
    private ShaderProgram passthroughShader;

    private Mesh fullscreenQuad;
    private Matrix4 identityMatrix;

    private MapTileFillType currentEffect = MapTileFillType.AIR;

    private boolean disposed = false;

    public PostProcessingRenderer(int width, int height) {
        this.width = width;
        this.height = height;

        identityMatrix = new Matrix4();

        initializeFrameBuffers();
        loadShaders();
        createFullscreenQuad();

        Log.info("PostProcessingRenderer", "Initialized post-processing renderer: " + width + "x" + height);
    }

    private void initializeFrameBuffers() {
        sceneFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        tempFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);

        Log.info("PostProcessingRenderer", "Created framebuffers: " + width + "x" + height);
    }

    private void loadShaders() {
        // Load underwater post-processing shader
        String underwaterVertexShader = Gdx.files.internal("shaders/underwater_postprocess.vertex.glsl").readString();
        String underwaterFragmentShader = Gdx.files.internal("shaders/underwater_postprocess.fragment.glsl").readString();
        underwaterShader = new ShaderProgram(underwaterVertexShader, underwaterFragmentShader);
        if (!underwaterShader.isCompiled()) {
            Log.error("PostProcessingRenderer", "Underwater shader compilation failed: " + underwaterShader.getLog());
            throw new RuntimeException("Underwater shader compilation failed");
        }

        // Load lava post-processing shader
        String lavaVertexShader = Gdx.files.internal("shaders/lava_postprocess.vertex.glsl").readString();
        String lavaFragmentShader = Gdx.files.internal("shaders/lava_postprocess.fragment.glsl").readString();
        lavaShader = new ShaderProgram(lavaVertexShader, lavaFragmentShader);
        if (!lavaShader.isCompiled()) {
            Log.error("PostProcessingRenderer", "Lava shader compilation failed: " + lavaShader.getLog());
            throw new RuntimeException("Lava shader compilation failed");
        }

        // Load fog post-processing shader
        String fogVertexShader = Gdx.files.internal("shaders/fog_postprocess.vertex.glsl").readString();
        String fogFragmentShader = Gdx.files.internal("shaders/fog_postprocess.fragment.glsl").readString();
        fogShader = new ShaderProgram(fogVertexShader, fogFragmentShader);
        if (!fogShader.isCompiled()) {
            Log.error("PostProcessingRenderer", "Fog shader compilation failed: " + fogShader.getLog());
            throw new RuntimeException("Fog shader compilation failed");
        }

        // Load passthrough shader for no effect
        String passthroughVertexShader = Gdx.files.internal("shaders/debug_passthrough.vertex.glsl").readString();
        String passthroughFragmentShader = Gdx.files.internal("shaders/debug_passthrough.fragment.glsl").readString();
        passthroughShader = new ShaderProgram(passthroughVertexShader, passthroughFragmentShader);
        if (!passthroughShader.isCompiled()) {
            Log.error("PostProcessingRenderer", "Passthrough shader compilation failed: " + passthroughShader.getLog());
            throw new RuntimeException("Passthrough shader compilation failed");
        }

        Log.info("PostProcessingRenderer", "Loaded all post-processing shaders successfully");
    }

    private void createFullscreenQuad() {
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
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));

        fullscreenQuad.setVertices(vertices);
        fullscreenQuad.setIndices(indices);
    }

    public void setCurrentEffect(MapTileFillType effect) {
        if (this.currentEffect != effect) {
            this.currentEffect = effect;
            Log.info("PostProcessingRenderer", "Switched to effect: " + effect);
        }
    }

    public void beginSceneRender() {
        if (disposed) return;
        sceneFrameBuffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    public void endSceneRenderAndApplyEffects() {
        if (disposed) return;

        sceneFrameBuffer.end();

        // Apply post-processing effect based on current tile type
        applyPostProcessingEffect();
    }

    public void captureScreenAndApplyEffects() {
        if (disposed) return;
        // LibGDX approach: Use glCopyTexImage2D to copy screen to texture
        sceneFrameBuffer.getColorBufferTexture().bind();
        Gdx.gl.glCopyTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 0, 0, width, height, 0);

        // Now apply post-processing effect to the captured screen
        applyPostProcessingEffect();
    }


    private void applyPostProcessingEffect() {
        // Save OpenGL state
        boolean depthTestEnabled = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
        boolean blendEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        boolean cullFaceEnabled = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);

        // Setup for post-processing
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Clear screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Set viewport to full screen
        Gdx.gl.glViewport(0, 0, width, height);

        // Select appropriate shader
        ShaderProgram shader = getShaderForEffect(currentEffect);

        // Bind scene texture
        sceneFrameBuffer.getColorBufferTexture().bind(0);

        // Apply shader
        shader.bind();
        shader.setUniformi("u_texture", 0);

        // Only set time and resolution uniforms for effects that need them
        if (currentEffect != MapTileFillType.AIR) {
            if (shader.hasUniform("u_time")) {
                shader.setUniformf("u_time", System.currentTimeMillis() / 1000.0f);
            }
            if (shader.hasUniform("u_resolution")) {
                shader.setUniformf("u_resolution", width, height);
            }
        }

        if (shader.hasUniform("u_projTrans")) {
            shader.setUniformMatrix("u_projTrans", identityMatrix);
        }

        // Render fullscreen quad to screen
        fullscreenQuad.render(shader, GL20.GL_TRIANGLES);

        // Restore OpenGL state
        if (depthTestEnabled) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        if (blendEnabled) Gdx.gl.glEnable(GL20.GL_BLEND);
        if (cullFaceEnabled) Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    private ShaderProgram getShaderForEffect(MapTileFillType effect) {
        switch (effect) {
            case WATER:
                return underwaterShader;
            case LAVA:
                return lavaShader;
            case FOG:
                return fogShader;
            case AIR:
            default:
                return passthroughShader;
        }
    }

    public FrameBuffer getSceneFrameBuffer() {
        return sceneFrameBuffer;
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;

        // Dispose old framebuffers
        if (sceneFrameBuffer != null) sceneFrameBuffer.dispose();
        if (tempFrameBuffer != null) tempFrameBuffer.dispose();

        // Create new framebuffers
        initializeFrameBuffers();

        Log.info("PostProcessingRenderer", "Resized to: " + width + "x" + height);
    }

    @Override
    public void dispose() {
        if (disposed) return;

        if (sceneFrameBuffer != null) sceneFrameBuffer.dispose();
        if (tempFrameBuffer != null) tempFrameBuffer.dispose();
        if (fullscreenQuad != null) fullscreenQuad.dispose();
        if (underwaterShader != null) underwaterShader.dispose();
        if (lavaShader != null) lavaShader.dispose();
        if (fogShader != null) fogShader.dispose();
        if (passthroughShader != null) passthroughShader.dispose();

        disposed = true;
        Log.info("PostProcessingRenderer", "Disposed");
    }
}
