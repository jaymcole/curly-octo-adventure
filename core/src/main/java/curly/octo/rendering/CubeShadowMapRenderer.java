package curly.octo.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
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

/**
 * Handles cube shadow map generation for omnidirectional point light shadows
 */
public class CubeShadowMapRenderer implements Disposable {

    private final int SHADOW_MAP_SIZE = 512; // Smaller size for 6 faces

    // 6 framebuffers for each face of the cube
    private FrameBuffer[] shadowFrameBuffers;
    private ShaderProgram depthShader;
    private ShaderProgram shadowShader;
    private PerspectiveCamera[] lightCameras;
    private Matrix4[] lightViewProjections;

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

    public CubeShadowMapRenderer() {
        initializeFrameBuffers();
        loadShaders();
        setupCameras();
    }

    private void initializeFrameBuffers() {
        shadowFrameBuffers = new FrameBuffer[6];
        for (int i = 0; i < 6; i++) {
            shadowFrameBuffers[i] = new FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true);
        }
        Log.info("CubeShadowMapRenderer", "Created 6 cube shadow framebuffers: " + SHADOW_MAP_SIZE + "x" + SHADOW_MAP_SIZE);
    }

    private void loadShaders() {
        // Load depth shader for shadow map generation
        String depthVertexShader = Gdx.files.internal("shaders/cube_depth.vertex.glsl").readString();
        String depthFragmentShader = Gdx.files.internal("shaders/cube_depth.fragment.glsl").readString();
        depthShader = new ShaderProgram(depthVertexShader, depthFragmentShader);

        if (!depthShader.isCompiled()) {
            Log.error("CubeShadowMapRenderer", "Depth shader compilation failed: " + depthShader.getLog());
            throw new RuntimeException("Cube depth shader compilation failed");
        }

        // Load main shadow shader for final rendering
        String shadowVertexShader = Gdx.files.internal("shaders/cube_shadow.vertex.glsl").readString();
        String shadowFragmentShader = Gdx.files.internal("shaders/cube_shadow.fragment.glsl").readString();
        shadowShader = new ShaderProgram(shadowVertexShader, shadowFragmentShader);

        if (!shadowShader.isCompiled()) {
            Log.error("CubeShadowMapRenderer", "Shadow shader compilation failed: " + shadowShader.getLog());
            throw new RuntimeException("Cube shadow shader compilation failed");
        }

        Log.info("CubeShadowMapRenderer", "Cube shadow shaders loaded successfully");
    }

    private void setupCameras() {
        lightCameras = new PerspectiveCamera[6];
        lightViewProjections = new Matrix4[6];

        for (int i = 0; i < 6; i++) {
            lightCameras[i] = new PerspectiveCamera(90f, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
            lightCameras[i].near = 0.1f;
            lightCameras[i].far = 25f; // Match point light range
            lightViewProjections[i] = new Matrix4();
        }
    }

    public void generateCubeShadowMap(Array<ModelInstance> instances, PointLight light) {
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
            renderShadowMapFace(instances, face, light);
        }
    }

    private void renderShadowMapFace(Array<ModelInstance> instances, int face, PointLight light) {
        FrameBuffer frameBuffer = shadowFrameBuffers[face];
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

            renderInstance(instance, depthShader);
        }

        frameBuffer.end();
    }

    public void renderWithCubeShadows(Array<ModelInstance> instances, Camera camera, PointLight light, Vector3 ambientLight) {
        shadowShader.bind();

        // Bind all 6 cube shadow map faces
        for (int i = 0; i < 6; i++) {
            shadowFrameBuffers[i].getColorBufferTexture().bind(i + 1);
            shadowShader.setUniformi("u_cubeShadowMap[" + i + "]", i + 1);
        }

        // Set light uniforms
        shadowShader.setUniformf("u_lightPosition", light.position);
        shadowShader.setUniformf("u_lightColor", light.color.r, light.color.g, light.color.b);
        shadowShader.setUniformf("u_lightIntensity", light.intensity);
        shadowShader.setUniformf("u_ambientLight", ambientLight);
        shadowShader.setUniformf("u_farPlane", lightCameras[0].far);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;

            shadowShader.setUniformMatrix("u_worldTrans", worldTransform);
            shadowShader.setUniformMatrix("u_projViewTrans", camera.combined);

            // Set material color - this would normally come from the material
            shadowShader.setUniformf("u_diffuseColor", 0.7f, 0.7f, 0.7f);

            renderInstance(instance, shadowShader);
        }
    }

    private void renderInstance(ModelInstance instance, ShaderProgram shader) {
        for (Node node : instance.nodes) {
            for (NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    Mesh mesh = nodePart.meshPart.mesh;
                    mesh.render(shader, nodePart.meshPart.primitiveType,
                               nodePart.meshPart.offset, nodePart.meshPart.size);
                }
            }
        }
    }

    public Texture getShadowMapTexture(int face) {
        if (face >= 0 && face < 6) {
            return shadowFrameBuffers[face].getColorBufferTexture();
        }
        return null;
    }

    @Override
    public void dispose() {
        if (disposed) return;

        if (shadowFrameBuffers != null) {
            for (int i = 0; i < 6; i++) {
                if (shadowFrameBuffers[i] != null) {
                    shadowFrameBuffers[i].dispose();
                }
            }
            Log.info("CubeShadowMapRenderer", "Cube shadow framebuffers disposed");
        }

        if (depthShader != null) {
            depthShader.dispose();
            Log.info("CubeShadowMapRenderer", "Cube depth shader disposed");
        }

        if (shadowShader != null) {
            shadowShader.dispose();
            Log.info("CubeShadowMapRenderer", "Cube shadow shader disposed");
        }

        disposed = true;
        Log.info("CubeShadowMapRenderer", "CubeShadowMapRenderer disposed");
    }
}
