package curly.octo.rendering;

import curly.octo.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;

/**
 * Handles shadow map generation and depth-mapped shadow rendering
 */
public class ShadowMapRenderer implements Disposable {

    private final int SHADOW_MAP_SIZE = Constants.SHADOW_MAP_SIZE;

    private FrameBuffer shadowFrameBuffer;
    private ShaderProgram depthShader;
    private ShaderProgram shadowShader;
    private Camera lightCamera;
    private Matrix4 lightViewProjection;
    private Matrix4 biasMatrix;

    private boolean disposed = false;

    public ShadowMapRenderer() {
        initializeFrameBuffer();
        loadShaders();
        setupMatrices();
    }

    private void initializeFrameBuffer() {
        shadowFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true);
        Log.info("ShadowMapRenderer", "Shadow framebuffer created: " + SHADOW_MAP_SIZE + "x" + SHADOW_MAP_SIZE);
    }

    private void loadShaders() {
        // Load depth shader for shadow map generation
        String depthVertexShader = Gdx.files.internal("shaders/depth.vertex.glsl").readString();
        String depthFragmentShader = Gdx.files.internal("shaders/depth.fragment.glsl").readString();
        depthShader = new ShaderProgram(depthVertexShader, depthFragmentShader);

        if (!depthShader.isCompiled()) {
            Log.error("ShadowMapRenderer", "Depth shader compilation failed: " + depthShader.getLog());
            throw new RuntimeException("Depth shader compilation failed");
        }

        // Load main shadow shader for final rendering
        String shadowVertexShader = Gdx.files.internal("shaders/shadow.vertex.glsl").readString();
        String shadowFragmentShader = Gdx.files.internal("shaders/shadow.fragment.glsl").readString();
        shadowShader = new ShaderProgram(shadowVertexShader, shadowFragmentShader);

        if (!shadowShader.isCompiled()) {
            Log.error("ShadowMapRenderer", "Shadow shader compilation failed: " + shadowShader.getLog());
            throw new RuntimeException("Shadow shader compilation failed");
        }

        Log.info("ShadowMapRenderer", "Shaders loaded successfully");
    }

    private void setupMatrices() {
        // Use wider perspective projection to capture more of the scene
        lightCamera = new PerspectiveCamera(Constants.SHADOW_LIGHT_CAMERA_FOV, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        lightCamera.near = Constants.SHADOW_LIGHT_CAMERA_NEAR;
        lightCamera.far = Constants.SHADOW_LIGHT_CAMERA_FAR;

        lightViewProjection = new Matrix4();

        // Bias matrix to transform from [-1,1] to [0,1]
        biasMatrix = new Matrix4();
        biasMatrix.set(new float[] {
            0.5f, 0.0f, 0.0f, 0.5f,
            0.0f, 0.5f, 0.0f, 0.5f,
            0.0f, 0.0f, 0.5f, 0.5f,
            0.0f, 0.0f, 0.0f, 1.0f
        });
    }

    public void generateShadowMap(Array<ModelInstance> instances, PointLight light, Vector3 playerPosition) {
        // Position shadow camera directly above the light looking down
        // This gives more predictable shadows that radiate outward from the light
        lightCamera.position.set(light.position.x, light.position.y + 10f, light.position.z);
        lightCamera.lookAt(light.position.x, light.position.y - 5f, light.position.z);
        lightCamera.up.set(0, 0, 1); // Use Z as up to avoid camera flipping
        lightCamera.update();

        lightViewProjection.set(lightCamera.combined);

        // Begin shadow map rendering
        shadowFrameBuffer.begin();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);

        // Render depth from light's perspective
        depthShader.bind();

        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;
            Matrix4 lightMVP = new Matrix4();
            lightMVP.set(lightViewProjection).mul(worldTransform);

            depthShader.setUniformMatrix("u_worldTrans", worldTransform);
            depthShader.setUniformMatrix("u_lightMVP", lightMVP);

            renderInstance(instance, depthShader);
        }

        shadowFrameBuffer.end();
    }

    public void renderWithShadows(Array<ModelInstance> instances, Camera camera, PointLight light, Vector3 ambientLight) {
        shadowShader.bind();

        // Bind shadow map texture
        shadowFrameBuffer.getColorBufferTexture().bind(1);
        shadowShader.setUniformi("u_shadowMap", 1);

        // Set light uniforms
        shadowShader.setUniformf("u_lightPosition", light.position);
        shadowShader.setUniformf("u_lightColor", light.color.r, light.color.g, light.color.b);
        shadowShader.setUniformf("u_lightIntensity", light.intensity);
        shadowShader.setUniformf("u_ambientLight", ambientLight);

        // Calculate light space matrix
        Matrix4 biasedLightMatrix = new Matrix4();
        biasedLightMatrix.set(biasMatrix).mul(lightViewProjection);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;
            Matrix4 lightMVP = new Matrix4();
            lightMVP.set(biasedLightMatrix).mul(worldTransform);

            shadowShader.setUniformMatrix("u_worldTrans", worldTransform);
            shadowShader.setUniformMatrix("u_projViewTrans", camera.combined);
            shadowShader.setUniformMatrix("u_lightMVP", lightMVP);

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

    public Texture getShadowMapTexture() {
        return shadowFrameBuffer.getColorBufferTexture();
    }

    @Override
    public void dispose() {
        if (disposed) return;

        if (shadowFrameBuffer != null) {
            shadowFrameBuffer.dispose();
            Log.info("ShadowMapRenderer", "Shadow framebuffer disposed");
        }

        if (depthShader != null) {
            depthShader.dispose();
            Log.info("ShadowMapRenderer", "Depth shader disposed");
        }

        if (shadowShader != null) {
            shadowShader.dispose();
            Log.info("ShadowMapRenderer", "Shadow shader disposed");
        }

        disposed = true;
        Log.info("ShadowMapRenderer", "ShadowMapRenderer disposed");
    }
}
