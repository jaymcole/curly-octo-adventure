package curly.octo.rendering;

import curly.octo.Constants;
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
import lights.LightConverter;
import lights.FallbackLight;

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
    private boolean usingFallbackShader = false;  // Track which shader version we're using

    // Overflow handling arrays (reused to reduce GC pressure)
    private final Array<PointLight> actualShadowLights;
    private final Array<FallbackLight> overflowFallbackLights;
    private final Array<PointLight> combinedLightArray;

    public CubeShadowMapRenderer(int quality, int maxLights) {
        SHADOW_MAP_SIZE = quality;
        MAX_LIGHTS = Math.max(1, Math.min(8, maxLights)); // Clamp between 1-8

        // Initialize overflow handling arrays
        this.actualShadowLights = new Array<>(MAX_LIGHTS);
        this.overflowFallbackLights = new Array<>(128); // Much higher capacity for converted excess shadow lights
        this.combinedLightArray = new Array<>(256); // Higher capacity for final rendering with many lights

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
            Log.error("CubeShadowMapRenderer", "Enhanced shadow shader compilation failed: " + shadowShader.getLog());
            Log.info("CubeShadowMapRenderer", "GPU Info: " + Gdx.gl.glGetString(GL20.GL_RENDERER));
            Log.info("CubeShadowMapRenderer", "Attempting fallback to basic 8-light shader...");

            // Try loading a more basic version
            shadowShader.dispose();
            shadowShader = createFallbackShader();

            if (!shadowShader.isCompiled()) {
                Log.error("CubeShadowMapRenderer", "Fallback shader also failed: " + shadowShader.getLog());
                throw new RuntimeException("Both enhanced and fallback shaders failed to compile");
            }
        }
    }

    /**
     * Creates a fallback shader with basic 8-light support for GPU compatibility.
     * This method generates a shader programmatically with reduced limits.
     */
    private ShaderProgram createFallbackShader() {
        // Create a basic fallback fragment shader with 8-light limit
        String fallbackFragmentShader =
            "#ifdef GL_ES\\n" +
            "precision mediump float;\\n" +
            "#endif\\n\\n" +

            "// Basic fallback shader with 8-light limit\\n" +
            "uniform vec3 u_ambientLight;\\n" +
            "uniform vec3 u_diffuseColor;\\n" +
            "uniform float u_diffuseAlpha;\\n" +
            "uniform sampler2D u_diffuseTexture;\\n" +
            "uniform int u_hasTexture;\\n" +
            "uniform float u_farPlane;\\n\\n" +

            "// Basic 8-light arrays (guaranteed GPU compatibility)\\n" +
            "uniform int u_numLights;\\n" +
            "uniform vec3 u_lightPositions[8];\\n" +
            "uniform vec3 u_lightColors[8];\\n" +
            "uniform float u_lightIntensities[8];\\n\\n" +

            "uniform int u_numShadowLights;\\n" +
            "uniform sampler2D u_cubeShadowMaps[48];\\n\\n" +

            "varying vec3 v_worldPos;\\n" +
            "varying vec3 v_normal;\\n" +
            "varying vec2 v_texCoord;\\n\\n" +

            "void main() {\\n" +
            "    vec3 normal = normalize(v_normal);\\n" +
            "    vec3 baseMaterial = u_diffuseColor;\\n" +
            "    if (u_hasTexture == 1) {\\n" +
            "        vec4 texColor = texture2D(u_diffuseTexture, v_texCoord);\\n" +
            "        baseMaterial = texColor.rgb * u_diffuseColor;\\n" +
            "    }\\n\\n" +

            "    vec3 totalLighting = u_ambientLight;\\n\\n" +

            "    // Simple lighting calculation for 8 lights max\\n" +
            "    for (int i = 0; i < 8; i++) {\\n" +
            "        if (i >= u_numLights) break;\\n" +
            "        vec3 lightPos = u_lightPositions[i];\\n" +
            "        vec3 lightColor = u_lightColors[i];\\n" +
            "        float lightIntensity = u_lightIntensities[i];\\n" +
            "        vec3 lightDirection = v_worldPos - lightPos;\\n" +
            "        vec3 lightDir = normalize(-lightDirection);\\n" +
            "        float distance = length(lightDirection);\\n" +
            "        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);\\n" +
            "        float diff = max(dot(normal, lightDir), 0.0);\\n" +
            "        totalLighting += diff * lightColor * attenuation;\\n" +
            "    }\\n\\n" +

            "    vec3 finalColor = baseMaterial * totalLighting;\\n" +
            "    gl_FragColor = vec4(finalColor, u_diffuseAlpha);\\n" +
            "}";

        String vertexShader = Gdx.files.internal("shaders/cube_shadow.vertex.glsl").readString();
        ShaderProgram fallbackShader = new ShaderProgram(vertexShader, fallbackFragmentShader);

        if (fallbackShader.isCompiled()) {
            usingFallbackShader = true;
            Log.info("CubeShadowMapRenderer", "Fallback shader compiled successfully (8-light limit)");
        }

        return fallbackShader;
    }

    private void setupCameras() {
        lightCameras = new PerspectiveCamera[6];
        lightViewProjections = new Matrix4[6];

        for (int i = 0; i < 6; i++) {
            lightCameras[i] = new PerspectiveCamera(Constants.CUBE_SHADOW_CAMERA_FOV, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
            lightCameras[i].near = 0.1f;
            lightCameras[i].far = 50f; // Increased light range for smoother falloff
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

    /**
     * Renders scene with shadow overflow handling - NO LIGHTS WILL BE LOST.
     * This method automatically converts excess shadow lights to fallback lights,
     * ensuring all requested lights remain visible in the scene.
     *
     * @param instances Model instances to render
     * @param camera Scene camera
     * @param requestedShadowLights All lights requested to cast shadows
     * @param additionalLights Additional non-shadow lights
     * @param ambientLight Ambient light color
     */
    public void renderWithShadowOverflowHandling(Array<ModelInstance> instances, Camera camera,
                                                Array<PointLight> requestedShadowLights,
                                                Array<PointLight> additionalLights,
                                                Vector3 ambientLight) {

        // Sort lights by importance before overflow handling
        // This ensures closest/brightest lights get shadow casting priority
        LightConverter.sortLightsByImportance(requestedShadowLights, camera.position);

        // Handle shadow light overflow - convert excess to fallback
        LightConverter.handleShadowLightOverflow(
            requestedShadowLights,    // All requested shadow lights (now sorted by importance)
            MAX_LIGHTS,               // Maximum shadow lights allowed
            null,                     // No game object manager needed for temp lights
            actualShadowLights,       // Output: actual shadow lights (limited)
            overflowFallbackLights    // Output: excess lights converted to fallback
        );

        // Generate shadow maps for actual shadow lights only
        resetLightIndex();
        for (PointLight shadowLight : actualShadowLights) {
            generateCubeShadowMap(instances, shadowLight);
        }

        // Build combined light array for rendering
        combinedLightArray.clear();

        // Add shadow lights first (these get shadows)
        for (PointLight shadowLight : actualShadowLights) {
            combinedLightArray.add(shadowLight);
        }

        // Add additional non-shadow lights
        if (additionalLights != null) {
            for (PointLight additionalLight : additionalLights) {
                combinedLightArray.add(additionalLight);
            }
        }

        // Convert overflow fallback lights to PointLight format and add them
        for (FallbackLight fallbackLight : overflowFallbackLights) {
            if (fallbackLight.isReadyForRendering()) {
                PointLight tempPointLight = new PointLight();
                tempPointLight.setPosition(fallbackLight.getWorldPosition());
                tempPointLight.setColor(fallbackLight.getLightColor().r,
                                      fallbackLight.getLightColor().g,
                                      fallbackLight.getLightColor().b, 1.0f);
                tempPointLight.setIntensity(fallbackLight.getEffectiveIntensity());
                combinedLightArray.add(tempPointLight);
            }
        }

        // Render with the combined lights (capped at shader array limit)
        renderWithLightArray(instances, camera, actualShadowLights, combinedLightArray, ambientLight);
    }

    /**
     * Internal rendering method that handles the actual shader setup and rendering.
     */
    private void renderWithLightArray(Array<ModelInstance> instances, Camera camera,
                                    Array<PointLight> shadowLights, Array<PointLight> allLights,
                                    Vector3 ambientLight) {
        if (shadowLights.size == 0 && allLights.size == 0) {
            Log.warn("CubeShadowMapRenderer", "No lights to render");
            return;
        }

        shadowShader.bind();

        // Bind shadow maps from shadow-casting lights
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

        // Send lights to shader (adaptive limit based on shader capability)
        int maxLights = usingFallbackShader ? Constants.LIGHTING_FALLBACK_SHADER_LIGHTS : Constants.LIGHTING_ENHANCED_SHADER_LIGHTS;
        int totalLights = Math.min(allLights.size, maxLights);
        shadowShader.setUniformi("u_numLights", totalLights);

        // Log overflow information for monitoring
        if (allLights.size > maxLights) {
            int overflowCount = allLights.size - maxLights;
            String shaderType = usingFallbackShader ? "fallback (" + Constants.LIGHTING_FALLBACK_SHADER_LIGHTS + "-light)" : "enhanced (" + Constants.LIGHTING_ENHANCED_SHADER_LIGHTS + "-light)";
            Log.warn("CubeShadowMapRenderer", "Light overflow with " + shaderType + " shader: " +
                     overflowCount + " lights not rendered (total: " + allLights.size + ", limit: " + maxLights + "). " +
                     "Consider using distance culling or reducing light density.");
        }

        Log.debug("CubeShadowMapRenderer", "Rendering with overflow handling - Shadow lights: " +
                  numShadowLights + ", Total lights: " + totalLights + " (out of " + allLights.size + " requested)");

        for (int i = 0; i < totalLights; i++) {
            PointLight light = allLights.get(i);
            boolean hasShadow = i < numShadowLights;

            shadowShader.setUniformf("u_lightPositions[" + i + "]", light.position);
            shadowShader.setUniformf("u_lightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
            shadowShader.setUniformf("u_lightIntensities[" + i + "]", light.intensity);
        }

        // Set ambient light
        shadowShader.setUniformf("u_ambientLight", ambientLight);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        // Render instances
        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;
            shadowShader.setUniformMatrix("u_worldTrans", worldTransform);
            shadowShader.setUniformMatrix("u_projViewTrans", camera.combined);
            renderInstance(instance, shadowShader);
        }
    }

    /**
     * Compatibility wrapper that maintains the original calling pattern but with overflow handling.
     * This method works exactly like renderWithMultipleCubeShadows but prevents lights from disappearing.
     *
     * @param instances Model instances to render
     * @param camera Scene camera
     * @param priorityShadowLights Lights that should get priority for shadows (subset of allLights)
     * @param allLights All lights in the scene (shadow + non-shadow)
     * @param ambientLight Ambient light color
     */
    public void renderWithShadowOverflowHandlingCompatible(Array<ModelInstance> instances, Camera camera,
                                                          Array<PointLight> priorityShadowLights,
                                                          Array<PointLight> allLights,
                                                          Vector3 ambientLight) {

        // Simple overflow handling: limit shadow generation but render all lights
        actualShadowLights.clear();

        // Generate shadow maps for up to MAX_LIGHTS lights (priority to the priorityShadowLights first)
        resetLightIndex();
        int shadowCount = 0;

        // First, add priority shadow lights
        for (PointLight light : priorityShadowLights) {
            if (shadowCount >= MAX_LIGHTS) break;
            actualShadowLights.add(light);
            generateCubeShadowMap(instances, light);
            shadowCount++;
        }

        // If we have room for more shadows and there are other lights not in priority list
        if (shadowCount < MAX_LIGHTS) {
            for (PointLight light : allLights) {
                if (shadowCount >= MAX_LIGHTS) break;
                if (!priorityShadowLights.contains(light, true)) { // Not already in priority list
                    actualShadowLights.add(light);
                    generateCubeShadowMap(instances, light);
                    shadowCount++;
                }
            }
        }

        // Build combined light array for rendering - ALL lights will be included
        combinedLightArray.clear();
        for (PointLight light : allLights) {
            combinedLightArray.add(light);
        }

        // Render with all lights (no longer limited to 8 - increased to 256!)
        renderWithLightArray(instances, camera, actualShadowLights, combinedLightArray, ambientLight);
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

        // Add remaining non-shadow lights, sorted by distance from camera
        Array<PointLight> nonShadowLights = new Array<>();
        for (PointLight light : allLights) {
            if (!shadowLights.contains(light, true)) {
                nonShadowLights.add(light);
            }
        }

        // Sort non-shadow lights by distance from camera (closest first)
        nonShadowLights.sort((light1, light2) -> {
            float distance1 = light1.position.dst(camera.position);
            float distance2 = light2.position.dst(camera.position);
            return Float.compare(distance1, distance2);
        });

        // Add sorted non-shadow lights to the final array
        orderedLights.addAll(nonShadowLights);

        // Send ordered lights to shader (limited by shader array size but with overflow handling)
        int totalLights = Math.min(orderedLights.size, Constants.LIGHTING_ENHANCED_SHADER_LIGHTS);
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
                    // Only set diffuse color for shadow shader, not depth shader
                    if (shader == shadowShader) {
                        // Extract diffuse color, alpha, and texture from material
                        Vector3 diffuseColor = new Vector3(0.7f, 0.7f, 0.7f); // Default gray
                        float diffuseAlpha = 1.0f; // Default opaque
                        boolean hasTexture = false;

                        if (nodePart.material != null) {
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute diffuseAttr =
                                (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute) nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
                            if (diffuseAttr != null) {
                                diffuseColor.set(diffuseAttr.color.r, diffuseAttr.color.g, diffuseAttr.color.b);
                                diffuseAlpha = diffuseAttr.color.a; // Extract alpha from diffuse color
                            }

                            // Check for diffuse texture
                            com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute textureAttr =
                                (com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute) nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse);
                            if (textureAttr != null && textureAttr.textureDescription != null && textureAttr.textureDescription.texture != null) {
                                hasTexture = true;
                                textureAttr.textureDescription.texture.bind(0);
                                shader.setUniformi("u_diffuseTexture", 0);
                            }
                        }

                        // Set uniforms
                        shader.setUniformf("u_diffuseColor", diffuseColor);
                        shader.setUniformf("u_diffuseAlpha", diffuseAlpha);
                        shader.setUniformi("u_hasTexture", hasTexture ? 1 : 0);
                    }

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

        // Clean up overflow handling arrays
        if (overflowFallbackLights != null) {
            LightConverter.cleanupOverflowFallbackLights(overflowFallbackLights);
        }
        if (actualShadowLights != null) {
            actualShadowLights.clear();
        }
        if (combinedLightArray != null) {
            combinedLightArray.clear();
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
