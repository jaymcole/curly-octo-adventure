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

    private final int SHADOW_MAP_SIZE;
    private final int MAX_LIGHTS;

    // Quality presets
    public static final int QUALITY_LOW = 256;
    public static final int QUALITY_MEDIUM = 512;
    public static final int QUALITY_HIGH = 1024;
    public static final int QUALITY_ULTRA = 2048;

    // Multiple sets of 6 framebuffers (one set per light, 6 faces per cube)
    private FrameBuffer[][] shadowFrameBuffers; // [lightIndex][faceIndex]
    private ShaderProgram depthShader;
    private ShaderProgram shadowShader;
    private PerspectiveCamera[] lightCameras; // Reused for each light
    private Matrix4[] lightViewProjections;   // Reused for each light
    private int currentLightIndex = 0;        // Track current light being processed

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
        this(QUALITY_HIGH, 1); // Default to high quality, 1 light
    }

    public CubeShadowMapRenderer(int quality) {
        this(quality, 1); // Default to 1 light
    }

    public CubeShadowMapRenderer(int quality, int maxLights) {
        SHADOW_MAP_SIZE = quality;
        MAX_LIGHTS = Math.max(1, Math.min(8, maxLights)); // Clamp between 1-8
        initializeFrameBuffers();
        loadShaders();
        setupCameras();
    }

    private void initializeFrameBuffers() {
        shadowFrameBuffers = new FrameBuffer[MAX_LIGHTS][6];
        for (int lightIndex = 0; lightIndex < MAX_LIGHTS; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                shadowFrameBuffers[lightIndex][face] = new FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, true);
            }
        }
        Log.info("CubeShadowMapRenderer", "Created " + (MAX_LIGHTS * 6) + " cube shadow framebuffers for " + MAX_LIGHTS + " lights: " + SHADOW_MAP_SIZE + "x" + SHADOW_MAP_SIZE);
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
        // Find which light index this is (for framebuffer selection)
        // This assumes lights are processed in order
        if (currentLightIndex >= MAX_LIGHTS) {
            Log.warn("CubeShadowMapRenderer", "Too many lights for shadow casting, skipping light");
            return;
        }
        
        Log.debug("CubeShadowMapRenderer", "Generating shadow map " + currentLightIndex + " for light at (" + 
            light.position.x + "," + light.position.y + "," + light.position.z + ") intensity=" + light.intensity);

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
            renderShadowMapFace(instances, face, light, currentLightIndex);
        }
        
        currentLightIndex++;
    }
    
    public void resetLightIndex() {
        currentLightIndex = 0;
    }

    public void renderWithMultipleCubeShadows(Array<ModelInstance> instances, Camera camera, Array<PointLight> shadowLights, Array<PointLight> allLights, Vector3 ambientLight) {
        if (shadowLights.size == 0) {
            Log.warn("CubeShadowMapRenderer", "No shadow-casting lights provided");
            return;
        }

        shadowShader.bind();

        // Bind shadow maps from all shadow-casting lights
        int textureUnit = 1;
        int numShadowLights = Math.min(shadowLights.size, MAX_LIGHTS);
        for (int lightIndex = 0; lightIndex < numShadowLights; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                shadowFrameBuffers[lightIndex][face].getColorBufferTexture().bind(textureUnit);
                shadowShader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                textureUnit++;
            }
        }

        // Set shadow rendering parameters
        shadowShader.setUniformf("u_farPlane", lightCameras[0].far);
        shadowShader.setUniformi("u_numShadowLights", numShadowLights);

        // Pass shadow lights first, then remaining lights for proper shader indexing
        Array<PointLight> orderedLights = new Array<>();
        
        // Add shadow-casting lights first (these must match the shadow map order)
        for (PointLight shadowLight : shadowLights) {
            orderedLights.add(shadowLight);
        }
        
        // Add remaining non-shadow lights
        for (PointLight light : allLights) {
            if (!shadowLights.contains(light, true)) {
                orderedLights.add(light);
            }
        }
        
        // Send ordered lights to shader
        int totalLights = Math.min(orderedLights.size, 8);
        shadowShader.setUniformi("u_numLights", totalLights);
        
        Log.debug("CubeShadowMapRenderer", "Sending " + totalLights + " lights to shader (" + numShadowLights + " with shadows):");
        for (int i = 0; i < totalLights; i++) {
            PointLight light = orderedLights.get(i);
            boolean hasShadow = i < numShadowLights;
            Log.debug("CubeShadowMapRenderer", "  Light[" + i + "]: pos(" + light.position.x + "," + light.position.y + "," + light.position.z + 
                ") intensity=" + light.intensity + " shadow=" + hasShadow);
                
            shadowShader.setUniformf("u_lightPositions[" + i + "]", light.position);
            shadowShader.setUniformf("u_lightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
            shadowShader.setUniformf("u_lightIntensities[" + i + "]", light.intensity);
        }

        // Set ambient light
        shadowShader.setUniformf("u_ambientLight", ambientLight);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        // Render instances with combined lighting from all lights
        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;

            shadowShader.setUniformMatrix("u_worldTrans", worldTransform);
            shadowShader.setUniformMatrix("u_projViewTrans", camera.combined);

            renderInstance(instance, shadowShader);
        }
    }

    private Vector3 calculateCombinedLighting(ModelInstance instance, Array<PointLight> allLights, Camera camera, PointLight primaryLight) {
        Vector3 instancePos = instance.transform.getTranslation(new Vector3());
        Vector3 baseMaterial = new Vector3(0.7f, 0.7f, 0.7f); // Base material color
        Vector3 additionalLighting = new Vector3(0, 0, 0);

        // Add contribution from all lights EXCEPT the primary light (shader handles primary)
        for (PointLight light : allLights) {
            if (light != primaryLight) { // Skip primary light
                float distance = instancePos.dst(light.position);
                if (distance <= light.intensity * 1.5f) { // Wider range for additional lights
                    float attenuation = light.intensity / (1.0f + 0.03f * distance + 0.008f * distance * distance);
                    // Boost additional lights for better visibility
                    additionalLighting.add(light.color.r * attenuation * 1.5f, 
                                         light.color.g * attenuation * 1.5f, 
                                         light.color.b * attenuation * 1.5f);
                }
            }
        }

        // Material with additional lighting contribution
        baseMaterial.add(additionalLighting.x * 0.5f, additionalLighting.y * 0.5f, additionalLighting.z * 0.5f);
        
        // Clamp to reasonable values  
        baseMaterial.x = Math.min(2.0f, baseMaterial.x);
        baseMaterial.y = Math.min(2.0f, baseMaterial.y);
        baseMaterial.z = Math.min(2.0f, baseMaterial.z);
        
        return baseMaterial;
    }

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

            renderInstance(instance, depthShader);
        }

        frameBuffer.end();
    }

    public void renderWithCubeShadows(Array<ModelInstance> instances, Camera camera, PointLight light, Vector3 ambientLight) {
        shadowShader.bind();

        // Bind all 6 cube shadow map faces from the first light (lightIndex 0)
        for (int i = 0; i < 6; i++) {
            shadowFrameBuffers[0][i].getColorBufferTexture().bind(i + 1);
            shadowShader.setUniformi("u_cubeShadowMap[" + i + "]", i + 1);
        }

        // Set light uniforms (single light version - convert to array format)
        shadowShader.setUniformi("u_numLights", 1);
        shadowShader.setUniformf("u_lightPositions[0]", light.position);
        shadowShader.setUniformf("u_lightColors[0]", light.color.r, light.color.g, light.color.b);
        shadowShader.setUniformf("u_lightIntensities[0]", light.intensity);
        shadowShader.setUniformf("u_ambientLight", ambientLight);
        shadowShader.setUniformf("u_farPlane", lightCameras[0].far);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;

            shadowShader.setUniformMatrix("u_worldTrans", worldTransform);
            shadowShader.setUniformMatrix("u_projViewTrans", camera.combined);

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
        return getShadowMapTexture(0, face); // Default to first light
    }
    
    public Texture getShadowMapTexture(int lightIndex, int face) {
        if (lightIndex >= 0 && lightIndex < MAX_LIGHTS && face >= 0 && face < 6) {
            return shadowFrameBuffers[lightIndex][face].getColorBufferTexture();
        }
        return null;
    }

    @Override
    public void dispose() {
        if (disposed) return;

        if (shadowFrameBuffers != null) {
            for (int lightIndex = 0; lightIndex < MAX_LIGHTS; lightIndex++) {
                for (int face = 0; face < 6; face++) {
                    if (shadowFrameBuffers[lightIndex][face] != null) {
                        shadowFrameBuffers[lightIndex][face].dispose();
                    }
                }
            }
            Log.info("CubeShadowMapRenderer", "Cube shadow framebuffers disposed (" + (MAX_LIGHTS * 6) + " total)");
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
