package curly.octo.rendering.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.rendering.core.BaseRenderer;
import curly.octo.rendering.core.MaterialUtils;
import curly.octo.rendering.core.RenderingContext;
import curly.octo.rendering.debug.DebugRenderer;

/**
 * Renders the scene with shadow mapping and lighting.
 * Handles two-pass rendering (opaque + transparent) and shadow map binding.
 */
public class SceneRenderer extends BaseRenderer {

    private final ShaderProgram shadowShader;
    private final ShaderProgram waterShader;
    private final float farPlane;
    private final int maxShadowLights;

    // Optional debug renderer
    private DebugRenderer debugRenderer;

    // Debug counters
    private int opaquePartsRendered = 0;
    private int transparentPartsRendered = 0;

    // Time tracking for animated shaders
    private float elapsedTime = 0f;

    // Cached rendering state for shader switching
    private Array<PointLight> currentLights;
    private int currentNumShadowLights;
    private Vector3 currentAmbientLight;
    private RenderingContext currentContext;

    /**
     * Creates a new scene renderer.
     *
     * @param maxShadowLights Maximum number of shadow-casting lights
     * @param farPlane Far plane distance for shadow calculations
     */
    public SceneRenderer(int maxShadowLights, float farPlane) {
        this.maxShadowLights = maxShadowLights;
        this.farPlane = farPlane;
        this.shadowShader = loadShadowShader();
        this.waterShader = loadWaterShader();

        Log.info("SceneRenderer", "Initialized with max " + maxShadowLights + " shadow lights, far plane=" + farPlane);
    }

    /**
     * Loads the cube shadow shader for scene rendering.
     */
    private ShaderProgram loadShadowShader() {
        String shadowVertexShader = Gdx.files.internal("shaders/cube_shadow.vertex.glsl").readString();
        String shadowFragmentShader = Gdx.files.internal("shaders/cube_shadow.fragment.glsl").readString();
        ShaderProgram shader = new ShaderProgram(shadowVertexShader, shadowFragmentShader);

        if (!shader.isCompiled()) {
            Log.error("SceneRenderer", "Shadow shader compilation failed: " + shader.getLog());
            Log.info("SceneRenderer", "Attempting fallback shader...");
            shader.dispose();
            shader = createFallbackShader();

            if (!shader.isCompiled()) {
                Log.error("SceneRenderer", "Fallback shader also failed: " + shader.getLog());
                throw new RuntimeException("Both enhanced and fallback shaders failed to compile");
            }
        }

        Log.info("SceneRenderer", "Shadow shader loaded successfully");
        return shader;
    }

    /**
     * Creates a fallback shader with 8-light limit for GPU compatibility.
     */
    private ShaderProgram createFallbackShader() {
        String fallbackFragmentShader =
            "#ifdef GL_ES\\n" +
            "precision mediump float;\\n" +
            "#endif\\n\\n" +
            "uniform vec3 u_ambientLight;\\n" +
            "uniform vec3 u_diffuseColor;\\n" +
            "uniform float u_diffuseAlpha;\\n" +
            "uniform sampler2D u_diffuseTexture;\\n" +
            "uniform int u_hasTexture;\\n" +
            "uniform float u_farPlane;\\n\\n" +
            "uniform int u_numLights;\\n" +
            "uniform vec3 u_lightPositions[8];\\n" +
            "uniform vec3 u_lightColors[8];\\n" +
            "uniform float u_lightIntensities[8];\\n\\n" +
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
            Log.info("SceneRenderer", "Fallback shader compiled successfully (8-light limit)");
        }

        return fallbackShader;
    }

    /**
     * Loads the water shader with Perlin noise animation.
     */
    private ShaderProgram loadWaterShader() {
        String vertexShader = Gdx.files.internal("shaders/water.vertex.glsl").readString();
        String fragmentShader = Gdx.files.internal("shaders/water.fragment.glsl").readString();

        Log.info("SceneRenderer", "Loading water shader...");
        Log.info("SceneRenderer", "Vertex shader size: " + vertexShader.length() + " chars");
        Log.info("SceneRenderer", "Fragment shader size: " + fragmentShader.length() + " chars");
        Log.info("SceneRenderer", "Fragment contains 'BRIGHT RED': " + fragmentShader.contains("BRIGHT RED"));

        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);

        if (!shader.isCompiled()) {
            Log.error("SceneRenderer", "Water shader compilation failed: " + shader.getLog());
            throw new RuntimeException("Water shader compilation failed: " + shader.getLog());
        }

        Log.info("SceneRenderer", "Water shader loaded successfully");
        Log.info("SceneRenderer", "Water shader log: " + shader.getLog());
        return shader;
    }

    /**
     * Sets the debug renderer for water wireframe visualization.
     */
    public void setDebugRenderer(DebugRenderer debugRenderer) {
        this.debugRenderer = debugRenderer;
    }

    /**
     * Renders the scene with shadow mapping and lighting.
     *
     * @param context Rendering context
     * @param shadowFrameBuffers Shadow map framebuffers [lightIndex][faceIndex]
     * @param shadowLights Lights that cast shadows
     * @param allLights All lights in the scene
     * @param ambientLight Ambient light color
     * @param deltaTime Time since last frame in seconds (for animated shaders)
     */
    public void renderWithShadows(RenderingContext context,
                                  FrameBuffer[][] shadowFrameBuffers,
                                  Array<PointLight> shadowLights,
                                  Array<PointLight> allLights,
                                  Vector3 ambientLight,
                                  float deltaTime) {
        // Update elapsed time for animated shaders
        elapsedTime += deltaTime;
        if (shadowLights.size == 0) {
            Log.warn("SceneRenderer", "No shadow-casting lights provided");
            return;
        }

        shadowShader.bind();

        // Bind shadow maps
        bindShadowMaps(shadowFrameBuffers, shadowLights.size);

        // Set shadow parameters
        configureShadowParameters(shadowLights.size);

        // Order lights (shadow-casting first, then others sorted by distance)
        Array<PointLight> orderedLights = orderLights(shadowLights, allLights, context.getCamera());

        // Cache rendering state for shader switching
        currentLights = orderedLights;
        currentNumShadowLights = shadowLights.size;
        currentAmbientLight = ambientLight;
        currentContext = context;

        // Set light uniforms
        configureLightUniforms(orderedLights, shadowLights.size);

        // Set ambient light
        shadowShader.setUniformf("u_ambientLight", ambientLight);

        // Configure GL state
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);

        // Reset debug counters
        resetDebugCounters();

        // Two-pass rendering using base renderer
        renderTwoPass(context, shadowShader, this::renderInstancePass);

        // Log stats
        logRenderingStats();
    }

    /**
     * Binds shadow map textures to texture units.
     */
    private void bindShadowMaps(FrameBuffer[][] shadowFrameBuffers, int numShadowLights) {
        int textureUnit = 1; // Unit 0 reserved for diffuse texture
        int actualShadowLights = Math.min(numShadowLights, maxShadowLights);

        for (int lightIndex = 0; lightIndex < actualShadowLights; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                shadowFrameBuffers[lightIndex][face].getColorBufferTexture().bind(textureUnit);
                shadowShader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                textureUnit++;
            }
        }
    }

    /**
     * Configures shadow rendering parameters.
     */
    private void configureShadowParameters(int numShadowLights) {
        shadowShader.setUniformf("u_farPlane", farPlane);
        shadowShader.setUniformi("u_numShadowLights", Math.min(numShadowLights, maxShadowLights));
    }

    /**
     * Orders lights for rendering: shadow-casting first, then others by distance.
     */
    private Array<PointLight> orderLights(Array<PointLight> shadowLights,
                                         Array<PointLight> allLights,
                                         Camera camera) {
        Array<PointLight> orderedLights = new Array<>();

        // Add shadow-casting lights first (must match shadow map order)
        orderedLights.addAll(shadowLights);

        // Add remaining non-shadow lights, sorted by distance from camera
        Array<PointLight> nonShadowLights = new Array<>();
        for (PointLight light : allLights) {
            if (!shadowLights.contains(light, true)) {
                nonShadowLights.add(light);
            }
        }

        // Sort non-shadow lights by distance (closest first)
        nonShadowLights.sort((light1, light2) -> {
            float distance1 = light1.position.dst(camera.position);
            float distance2 = light2.position.dst(camera.position);
            return Float.compare(distance1, distance2);
        });

        orderedLights.addAll(nonShadowLights);
        return orderedLights;
    }

    /**
     * Configures light uniforms for the shadow shader.
     */
    private void configureLightUniforms(Array<PointLight> orderedLights, int numShadowLights) {
        configureLightUniforms(shadowShader, orderedLights, numShadowLights);
    }

    /**
     * Configures light uniforms for a specific shader.
     */
    private void configureLightUniforms(ShaderProgram shader, Array<PointLight> orderedLights, int numShadowLights) {
        int totalLights = Math.min(orderedLights.size, Constants.LIGHTING_ENHANCED_SHADER_LIGHTS);
        shader.setUniformi("u_numLights", totalLights);

        for (int i = 0; i < totalLights; i++) {
            PointLight light = orderedLights.get(i);
            shader.setUniformf("u_lightPositions[" + i + "]", light.position);
            shader.setUniformf("u_lightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
            shader.setUniformf("u_lightIntensities[" + i + "]", light.intensity);
        }
    }

    /**
     * Resets debug counters for a new frame.
     */
    private void resetDebugCounters() {
        opaquePartsRendered = 0;
        transparentPartsRendered = 0;

        if (debugRenderer != null) {
            debugRenderer.reset();
        }
    }

    /**
     * Logs rendering statistics.
     */
    private void logRenderingStats() {
        Log.debug("SceneRenderer", "Rendered " + opaquePartsRendered + " opaque + " +
            transparentPartsRendered + " transparent parts");
    }

    /**
     * Renders a single instance for the current pass.
     * Called by the two-pass rendering system.
     */
    private void renderInstancePass(ModelInstance instance, ShaderProgram shader, boolean transparentPass) {
        for (Node node : instance.nodes) {
            for (NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    // Check if this part matches the current pass
                    boolean isTransparent = isPartTransparent(nodePart);

                    if (isTransparent != transparentPass) {
                        continue; // Skip parts that don't match current pass
                    }

                    // Update counters (this will log)
                    updateRenderCounters(transparentPass, nodePart);

                    // Collect debug data if needed
                    if (transparentPass && debugRenderer != null && Constants.DEBUG_WATER_WIREFRAME) {
                        if (isWaterPart(nodePart)) {
                            debugRenderer.collectWaterTriangles(nodePart, instance.transform);
                        }
                    }

                    // Select appropriate shader for this part
                    ShaderProgram activeShader = shader;
                    boolean isWater = isWaterPart(nodePart);

                    if (isWater) {
                        // Switch to water shader for water parts
                        activeShader = waterShader;
                        activeShader.bind();

                        // CRITICAL: Set transform uniforms for water shader
                        activeShader.setUniformMatrix("u_worldTrans", instance.transform);
                        activeShader.setUniformMatrix("u_projViewTrans", currentContext.getCamera().combined);

                        // Set time uniform for animation
                        activeShader.setUniformf("u_time", elapsedTime);

                        // Set lighting uniforms
                        configureLightUniforms(waterShader, currentLights, currentNumShadowLights);
                        activeShader.setUniformf("u_ambientLight", currentAmbientLight);

                        Log.debug("SceneRenderer", "Rendering water with shader - time=" + elapsedTime +
                                 ", part=" + nodePart.meshPart.id + ", transparent=" + transparentPass);
                    }

                    // Set material properties
                    setMaterialUniforms(nodePart, activeShader);

                    // Render the mesh
                    renderMeshPart(nodePart, activeShader);

                    // Switch back to main shader if we used water shader
                    if (isWater) {
                        shader.bind();
                        // Re-set transform uniforms for main shader
                        shader.setUniformMatrix("u_worldTrans", instance.transform);
                        shader.setUniformMatrix("u_projViewTrans", currentContext.getCamera().combined);
                    }
                }
            }
        }
    }

    /**
     * Determines if a node part is transparent.
     */
    private boolean isPartTransparent(NodePart nodePart) {
        if (nodePart.material != null) {
            BlendingAttribute blendAttr = (BlendingAttribute) nodePart.material.get(BlendingAttribute.Type);
            if (blendAttr != null && blendAttr.blended) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a node part represents water.
     */
    private boolean isWaterPart(NodePart nodePart) {
        return nodePart.meshPart.id != null && nodePart.meshPart.id.toLowerCase().contains("water");
    }

    /**
     * Updates rendering counters for debug output.
     */
    private void updateRenderCounters(boolean transparentPass, NodePart nodePart) {
        if (transparentPass) {
            transparentPartsRendered++;
            String partId = nodePart.meshPart.id != null ? nodePart.meshPart.id : "UNNAMED";
            Log.debug("SceneRenderer", "TRANSPARENT PART: '" + partId + "' - vertices: " + nodePart.meshPart.size);
        } else {
            opaquePartsRendered++;
        }
    }

    /**
     * Sets material uniforms for the shader.
     */
    private void setMaterialUniforms(NodePart nodePart, ShaderProgram shader) {
        // Water shader doesn't use material uniforms - it calculates color procedurally
        if (shader == waterShader) {
            // Water shader doesn't need diffuse color/alpha/texture uniforms
            return;
        }

        // Extract diffuse color, alpha, and texture from material
        // Using Vector3 for diffuseColor to match legacy implementation
        Vector3 diffuseColor = new Vector3(0.7f, 0.7f, 0.7f); // Default gray
        float diffuseAlpha = 1.0f; // Default opaque
        boolean hasTexture = false;

        if (nodePart.material != null) {
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute diffuseAttr =
                (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute) nodePart.material.get(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
            if (diffuseAttr != null) {
                diffuseColor.set(diffuseAttr.color.r, diffuseAttr.color.g, diffuseAttr.color.b);
                diffuseAlpha = diffuseAttr.color.a; // Extract alpha from diffuse color
            }

            // Check for diffuse texture
            TextureAttribute textureAttr = (TextureAttribute) nodePart.material.get(TextureAttribute.Diffuse);
            if (textureAttr != null && textureAttr.textureDescription != null && textureAttr.textureDescription.texture != null) {
                hasTexture = true;
                textureAttr.textureDescription.texture.bind(0);
                shader.setUniformi("u_diffuseTexture", 0);
            }
        }

        // Set uniforms - exactly as legacy renderer does
        shader.setUniformf("u_diffuseColor", diffuseColor);
        shader.setUniformf("u_diffuseAlpha", diffuseAlpha);
        shader.setUniformi("u_hasTexture", hasTexture ? 1 : 0);
    }

    /**
     * Renders a mesh part with the given shader.
     */
    private void renderMeshPart(NodePart nodePart, ShaderProgram shader) {
        Mesh mesh = nodePart.meshPart.mesh;
        mesh.render(shader, nodePart.meshPart.primitiveType,
            nodePart.meshPart.offset, nodePart.meshPart.size);
    }

    @Override
    public void render(RenderingContext context) {
        // This is a placeholder - actual rendering uses renderWithShadows
        Log.warn("SceneRenderer", "render() called without shadow parameters - use renderWithShadows() instead");
    }

    @Override
    protected void disposeResources() {
        if (shadowShader != null) {
            shadowShader.dispose();
        }
        if (waterShader != null) {
            waterShader.dispose();
        }
        Log.info("SceneRenderer", "Disposed scene renderer");
    }

    public int getOpaquePartsRendered() {
        return opaquePartsRendered;
    }

    public int getTransparentPartsRendered() {
        return transparentPartsRendered;
    }
}
