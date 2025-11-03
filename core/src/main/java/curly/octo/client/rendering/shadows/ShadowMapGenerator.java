package curly.octo.client.rendering.shadows;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.client.rendering.util.FramebufferFactory;

/**
 * Generates cube shadow maps for point lights.
 * Handles only shadow map generation, not scene rendering.
 * Separated from scene rendering for better organization and testability.
 */
public class ShadowMapGenerator implements Disposable {

    private final int shadowMapSize;
    private final int maxLights;

    // Quality presets
    public static final int QUALITY_LOW = 256;
    public static final int QUALITY_MEDIUM = 512;
    public static final int QUALITY_HIGH = 1024;
    public static final int QUALITY_ULTRA = 2048;

    // Shadow framebuffers: [lightIndex][faceIndex]
    private final FrameBuffer[][] shadowFrameBuffers;
    private final ShaderProgram depthShader;
    private final PerspectiveCamera[] lightCameras;
    private final Matrix4[] lightViewProjections;

    // Cube face directions (right, left, top, bottom, near, far)
    private static final Vector3[] CUBE_DIRECTIONS = {
        new Vector3(1, 0, 0),   // +X (right)
        new Vector3(-1, 0, 0),  // -X (left)
        new Vector3(0, 1, 0),   // +Y (top)
        new Vector3(0, -1, 0),  // -Y (bottom)
        new Vector3(0, 0, 1),   // +Z (near)
        new Vector3(0, 0, -1)   // -Z (far)
    };

    private static final Vector3[] CUBE_UPS = {
        new Vector3(0, -1, 0),  // +X (right)
        new Vector3(0, -1, 0),  // -X (left)
        new Vector3(0, 0, 1),   // +Y (top)
        new Vector3(0, 0, -1),  // -Y (bottom)
        new Vector3(0, -1, 0),  // +Z (near)
        new Vector3(0, -1, 0)   // -Z (far)
    };

    private boolean disposed = false;

    /**
     * Creates a new shadow map generator.
     *
     * @param quality Shadow map resolution (use QUALITY_* constants)
     * @param maxLights Maximum number of shadow-casting lights
     */
    public ShadowMapGenerator(int quality, int maxLights) {
        this.shadowMapSize = quality;
        this.maxLights = Math.max(1, Math.min(8, maxLights)); // Clamp between 1-8

        this.shadowFrameBuffers = FramebufferFactory.createCubeShadowMapArray(this.maxLights, 6, shadowMapSize);
        this.depthShader = loadDepthShader();
        this.lightCameras = setupCameras();
        this.lightViewProjections = new Matrix4[6];
        for (int i = 0; i < 6; i++) {
            this.lightViewProjections[i] = new Matrix4();
        }

        Log.info("ShadowMapGenerator", "Initialized with " + this.maxLights + " lights, " + shadowMapSize + "x" + shadowMapSize + " per face");
    }

    /**
     * Loads the depth shader for shadow map generation.
     */
    private ShaderProgram loadDepthShader() {
        String depthVertexShader = Gdx.files.internal("shaders/cube_depth.vertex.glsl").readString();
        String depthFragmentShader = Gdx.files.internal("shaders/cube_depth.fragment.glsl").readString();
        ShaderProgram shader = new ShaderProgram(depthVertexShader, depthFragmentShader);

        if (!shader.isCompiled()) {
            Log.error("ShadowMapGenerator", "Depth shader compilation failed: " + shader.getLog());
            throw new RuntimeException("Cube depth shader compilation failed");
        }

        Log.info("ShadowMapGenerator", "Depth shader loaded successfully");
        return shader;
    }

    /**
     * Sets up cameras for each cube face.
     */
    private PerspectiveCamera[] setupCameras() {
        PerspectiveCamera[] cameras = new PerspectiveCamera[6];

        for (int i = 0; i < 6; i++) {
            cameras[i] = new PerspectiveCamera(Constants.CUBE_SHADOW_CAMERA_FOV, shadowMapSize, shadowMapSize);
            cameras[i].near = 0.1f;
            cameras[i].far = 50f; // Light range
        }

        return cameras;
    }

    /**
     * Generates shadow maps for multiple lights.
     *
     * @param instances Model instances to cast shadows
     * @param lights Array of lights to generate shadows for
     */
    public void generateAllShadowMaps(Array<ModelInstance> instances, Array<PointLight> lights) {
        int numLights = Math.min(lights.size, maxLights);

        for (int i = 0; i < numLights; i++) {
            generateCubeShadowMap(instances, lights.get(i), i);
        }

        Log.debug("ShadowMapGenerator", "Generated shadow maps for " + numLights + " lights");
    }

    /**
     * Generates a cube shadow map for a single light.
     *
     * @param instances Model instances to cast shadows
     * @param light The light to generate shadows for
     * @param lightIndex Index of the light (0 to maxLights-1)
     */
    public void generateCubeShadowMap(Array<ModelInstance> instances, PointLight light, int lightIndex) {
        if (lightIndex >= maxLights) {
            Log.warn("ShadowMapGenerator", "Light index " + lightIndex + " exceeds max lights " + maxLights);
            return;
        }

        Log.debug("ShadowMapGenerator", "Generating shadow map " + lightIndex + " for light at " + light.position);

        // Position all 6 cameras at the light position
        for (int face = 0; face < 6; face++) {
            PerspectiveCamera camera = lightCameras[face];
            camera.position.set(light.position);

            // Set camera direction and up vector for this cube face
            Vector3 target = new Vector3(light.position).add(CUBE_DIRECTIONS[face]);
            camera.lookAt(target);
            camera.up.set(CUBE_UPS[face]);
            camera.update();

            lightViewProjections[face].set(camera.combined);
        }

        // Render to each face of the cube
        for (int face = 0; face < 6; face++) {
            renderShadowMapFace(instances, face, light, lightIndex);
        }
    }

    /**
     * Renders a single face of a cube shadow map.
     */
    private void renderShadowMapFace(Array<ModelInstance> instances, int face, PointLight light, int lightIndex) {
        FrameBuffer frameBuffer = shadowFrameBuffers[lightIndex][face];
        PerspectiveCamera camera = lightCameras[face];

        frameBuffer.begin();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);

        depthShader.bind();
        depthShader.setUniformf("u_lightPosition", light.position);
        depthShader.setUniformf("u_farPlane", camera.far);

        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;
            Matrix4 lightMVP = new Matrix4();
            lightMVP.set(camera.combined).mul(worldTransform);

            depthShader.setUniformMatrix("u_worldTrans", worldTransform);
            depthShader.setUniformMatrix("u_lightMVP", lightMVP);

            // Render all geometry (both opaque and transparent cast shadows)
            renderInstanceForShadow(instance);
        }

        frameBuffer.end();
    }

    /**
     * Renders a single instance for shadow map generation.
     * Renders all mesh parts regardless of transparency.
     */
    private void renderInstanceForShadow(ModelInstance instance) {
        for (Node node : instance.nodes) {
            for (NodePart part : node.parts) {
                if (part.enabled) {
                    part.meshPart.mesh.render(depthShader, part.meshPart.primitiveType,
                        part.meshPart.offset, part.meshPart.size);
                }
            }
        }
    }

    /**
     * Gets the shadow framebuffer for a specific light and face.
     *
     * @param lightIndex Light index (0 to maxLights-1)
     * @param faceIndex Face index (0 to 5)
     * @return The framebuffer
     */
    public FrameBuffer getShadowFrameBuffer(int lightIndex, int faceIndex) {
        if (lightIndex < 0 || lightIndex >= maxLights || faceIndex < 0 || faceIndex >= 6) {
            throw new IllegalArgumentException("Invalid light or face index");
        }
        return shadowFrameBuffers[lightIndex][faceIndex];
    }

    /**
     * Gets all shadow framebuffers.
     */
    public FrameBuffer[][] getShadowFrameBuffers() {
        return shadowFrameBuffers;
    }

    /**
     * Gets the far plane distance used for shadow cameras.
     */
    public float getFarPlane() {
        return lightCameras[0].far;
    }

    public int getMaxLights() {
        return maxLights;
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        FramebufferFactory.dispose2DArray(shadowFrameBuffers);

        if (depthShader != null) {
            depthShader.dispose();
        }

        disposed = true;
        Log.info("ShadowMapGenerator", "Disposed shadow map generator");
    }

    public boolean isDisposed() {
        return disposed;
    }
}
