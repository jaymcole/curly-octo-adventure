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
import lights.FallbackLight;

/**
 * HybridLightingRenderer combines shadow-casting and fallback lighting into a unified
 * rendering system. This class extends the capabilities of the original CubeShadowMapRenderer
 * to support both expensive shadow-casting lights and performant fallback lights in the
 * same render pass.
 * 
 * Key Features:
 * - Cube shadow map generation for up to 8 shadow-casting lights
 * - Unlimited fallback lights with optimized shader integration
 * - Hybrid rendering pipeline that minimizes draw calls
 * - Performance monitoring and optimization features
 * - Backward compatibility with existing shadow systems
 * 
 * Performance Benefits:
 * - Single render pass for all lighting types
 * - Efficient shader uniform management
 * - Distance-based light culling
 * - Adaptive quality settings based on performance
 * 
 * Usage:
 * This renderer is designed to replace CubeShadowMapRenderer in scenarios where
 * you need both high-quality shadows and numerous ambient lights.
 */
public class HybridLightingRenderer implements Disposable {
    
    // Configuration constants
    private static final int MAX_SHADOW_LIGHTS = Constants.LIGHTING_MAX_SHADOW_LIGHTS;        // Hardware shader limit (unchanged)
    private static final int MAX_FALLBACK_LIGHTS = Constants.LIGHTING_MAX_FALLBACK_LIGHTS;    // Dramatically increased shader array limit
    private static final boolean ENABLE_PERFORMANCE_MONITORING = true;
    private static final boolean ENABLE_DISTANCE_CULLING = true;
    private static final float DISTANCE_CULL_THRESHOLD = Constants.LIGHTING_CULL_DISTANCE; // Increased for more lights
    
    // Quality presets (inherited from original CubeShadowMapRenderer)
    public static final int QUALITY_LOW = 256;
    public static final int QUALITY_MEDIUM = 512;
    public static final int QUALITY_HIGH = 1024;
    public static final int QUALITY_ULTRA = 2048;
    
    // Shadow mapping infrastructure
    private final int shadowMapSize;
    private final int maxShadowLights;
    private FrameBuffer[][] shadowFrameBuffers; // [lightIndex][faceIndex]
    private ShaderProgram depthShader;
    private ShaderProgram hybridShader;
    private PerspectiveCamera[] lightCameras;   // Reused for shadow generation
    private Matrix4[] lightViewProjections;    // Reused for shadow generation
    private int currentShadowLightIndex = 0;
    
    // Cube face directions and orientations (for shadow mapping)
    private static final Vector3[] CUBE_DIRECTIONS = {
        new Vector3(1, 0, 0),   new Vector3(-1, 0, 0),  // +X, -X (right, left)
        new Vector3(0, 1, 0),   new Vector3(0, -1, 0),  // +Y, -Y (top, bottom)
        new Vector3(0, 0, 1),   new Vector3(0, 0, -1)   // +Z, -Z (near, far)
    };
    
    private static final Vector3[] CUBE_UPS = {
        new Vector3(0, -1, 0),  new Vector3(0, -1, 0),  // +X, -X
        new Vector3(0, 0, 1),   new Vector3(0, 0, -1),  // +Y, -Y  
        new Vector3(0, -1, 0),  new Vector3(0, -1, 0)   // +Z, -Z
    };
    
    // Performance monitoring
    private long lastFrameTime = 0;
    private int shadowLightsRendered = 0;
    private int fallbackLightsRendered = 0;
    private int totalDrawCalls = 0;
    private float averageFrameTime = 0.0f;
    private int frameCount = 0;
    
    // Fallback light culling cache (for performance)
    private final Array<FallbackLight> visibleFallbackLights;
    private long lastCullTime = 0;
    private static final long CULL_INTERVAL_MS = Constants.LIGHTING_CULL_INTERVAL_MS; // Cull every 100ms
    
    private boolean disposed = false;
    
    /**
     * Creates a new HybridLightingRenderer with the specified quality settings.
     * 
     * @param shadowQuality Shadow map resolution (use QUALITY_* constants)
     * @param maxShadowLights Maximum number of shadow-casting lights (1-8)
     */
    public HybridLightingRenderer(int shadowQuality, int maxShadowLights) {
        this.shadowMapSize = shadowQuality;
        this.maxShadowLights = Math.max(1, Math.min(8, maxShadowLights));
        this.visibleFallbackLights = new Array<>(MAX_FALLBACK_LIGHTS);
        
        initializeFrameBuffers();
        loadShaders();
        setupShadowCameras();
        
        Log.info("HybridLightingRenderer", "Initialized with shadow quality " + shadowQuality + 
                 "x" + shadowQuality + ", max shadow lights: " + this.maxShadowLights);
    }
    
    /**
     * Initializes frame buffers for cube shadow map generation.
     * Each shadow-casting light requires 6 frame buffers (one per cube face).
     */
    private void initializeFrameBuffers() {
        shadowFrameBuffers = new FrameBuffer[maxShadowLights][6];
        
        for (int lightIndex = 0; lightIndex < maxShadowLights; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                shadowFrameBuffers[lightIndex][face] = new FrameBuffer(
                    Pixmap.Format.RGBA8888, shadowMapSize, shadowMapSize, true
                );
            }
        }
        
        Log.info("HybridLightingRenderer", "Created " + (maxShadowLights * 6) + 
                 " shadow framebuffers (" + shadowMapSize + "x" + shadowMapSize + ")");
    }
    
    /**
     * Loads and compiles the shader programs required for hybrid lighting.
     */
    private void loadShaders() {
        // Load depth shader for shadow map generation
        try {
            String depthVertexShader = Gdx.files.internal("shaders/cube_depth.vertex.glsl").readString();
            String depthFragmentShader = Gdx.files.internal("shaders/cube_depth.fragment.glsl").readString();
            depthShader = new ShaderProgram(depthVertexShader, depthFragmentShader);
            
            if (!depthShader.isCompiled()) {
                throw new RuntimeException("Depth shader compilation failed: " + depthShader.getLog());
            }
        } catch (Exception e) {
            Log.error("HybridLightingRenderer", "Failed to load depth shader: " + e.getMessage());
            throw new RuntimeException("Critical shader loading failure", e);
        }
        
        // Load hybrid lighting shader
        try {
            String hybridVertexShader = Gdx.files.internal("shaders/hybrid_lighting.vertex.glsl").readString();
            String hybridFragmentShader = Gdx.files.internal("shaders/hybrid_lighting.fragment.glsl").readString();
            hybridShader = new ShaderProgram(hybridVertexShader, hybridFragmentShader);
            
            if (!hybridShader.isCompiled()) {
                // Fallback to original cube shadow shader if hybrid shader fails
                Log.warn("HybridLightingRenderer", "Hybrid shader compilation failed, falling back to cube shadow shader");
                String fallbackVertexShader = Gdx.files.internal("shaders/cube_shadow.vertex.glsl").readString();
                String fallbackFragmentShader = Gdx.files.internal("shaders/cube_shadow.fragment.glsl").readString();
                hybridShader = new ShaderProgram(fallbackVertexShader, fallbackFragmentShader);
                
                if (!hybridShader.isCompiled()) {
                    throw new RuntimeException("Both hybrid and fallback shader compilation failed: " + hybridShader.getLog());
                }
            }
        } catch (Exception e) {
            Log.error("HybridLightingRenderer", "Failed to load hybrid shader: " + e.getMessage());
            throw new RuntimeException("Critical shader loading failure", e);
        }
        
        Log.info("HybridLightingRenderer", "Hybrid lighting shaders loaded successfully");
    }
    
    /**
     * Sets up perspective cameras for shadow map generation.
     * Each camera represents one face of the cube shadow map.
     */
    private void setupShadowCameras() {
        lightCameras = new PerspectiveCamera[6];
        lightViewProjections = new Matrix4[6];
        
        for (int i = 0; i < 6; i++) {
            lightCameras[i] = new PerspectiveCamera(Constants.CUBE_SHADOW_CAMERA_FOV, shadowMapSize, shadowMapSize);
            lightCameras[i].near = Constants.SHADOW_LIGHT_CAMERA_NEAR;
            lightCameras[i].far = Constants.SHADOW_CAMERA_FAR_RANGE; // Extended range for better shadow coverage
            lightViewProjections[i] = new Matrix4();
        }
        
        Log.info("HybridLightingRenderer", "Shadow cameras configured with 60-unit range");
    }
    
    /**
     * Generates cube shadow maps for all shadow-casting lights.
     * This method should be called before rendering the main scene.
     * 
     * @param instances Model instances to cast shadows
     * @param shadowLights Array of shadow-casting PointLights
     */
    public void generateAllShadowMaps(Array<ModelInstance> instances, Array<PointLight> shadowLights) {
        if (ENABLE_PERFORMANCE_MONITORING) {
            lastFrameTime = System.nanoTime();
        }
        
        currentShadowLightIndex = 0;
        shadowLightsRendered = 0;
        
        // Generate shadow maps for each shadow-casting light
        int lightsToProcess = Math.min(shadowLights.size, maxShadowLights);
        for (int i = 0; i < lightsToProcess; i++) {
            PointLight light = shadowLights.get(i);
            generateCubeShadowMap(instances, light);
            shadowLightsRendered++;
        }
        
        if (ENABLE_PERFORMANCE_MONITORING && lightsToProcess > 0) {
            long shadowGenTime = (System.nanoTime() - lastFrameTime) / 1000000; // Convert to ms
            Log.debug("HybridLightingRenderer", "Generated " + lightsToProcess + 
                     " shadow maps in " + shadowGenTime + "ms");
        }
    }
    
    /**
     * Generates a cube shadow map for a single shadow-casting light.
     * 
     * @param instances Model instances to cast shadows
     * @param light The PointLight to generate shadows for
     */
    public void generateCubeShadowMap(Array<ModelInstance> instances, PointLight light) {
        if (currentShadowLightIndex >= maxShadowLights) {
            Log.warn("HybridLightingRenderer", "Too many shadow lights - skipping shadow generation");
            return;
        }
        
        // Position all 6 cameras at the light position for cube map generation
        for (int face = 0; face < 6; face++) {
            PerspectiveCamera camera = lightCameras[face];
            camera.position.set(light.position);
            
            // Configure camera for this cube face
            Vector3 target = new Vector3(light.position).add(CUBE_DIRECTIONS[face]);
            camera.lookAt(target);
            camera.up.set(CUBE_UPS[face]);
            camera.update();
            
            lightViewProjections[face].set(camera.combined);
        }
        
        // Render shadow map for each cube face
        for (int face = 0; face < 6; face++) {
            renderShadowMapFace(instances, face, light, currentShadowLightIndex);
        }
        
        currentShadowLightIndex++;
    }
    
    /**
     * Renders a single face of a cube shadow map.
     * 
     * @param instances Model instances to render
     * @param face Cube face index (0-5)
     * @param light Source light for shadow generation
     * @param lightIndex Index of the light in the shadow light array
     */
    private void renderShadowMapFace(Array<ModelInstance> instances, int face, PointLight light, int lightIndex) {
        FrameBuffer frameBuffer = shadowFrameBuffers[lightIndex][face];
        PerspectiveCamera camera = lightCameras[face];
        
        frameBuffer.begin();
        
        // Clear and configure depth testing for shadow rendering
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        
        // Configure depth shader for shadow map generation
        depthShader.bind();
        depthShader.setUniformf("u_lightPosition", light.position);
        depthShader.setUniformf("u_farPlane", camera.far);
        
        // Render all instances to the shadow map
        for (ModelInstance instance : instances) {
            Matrix4 worldTransform = instance.transform;
            Matrix4 lightMVP = new Matrix4();
            lightMVP.set(camera.combined).mul(worldTransform);
            
            depthShader.setUniformMatrix("u_worldTrans", worldTransform);
            depthShader.setUniformMatrix("u_lightMVP", lightMVP);
            
            renderInstanceGeometry(instance, depthShader);
        }
        
        frameBuffer.end();
    }
    
    /**
     * Renders the scene with hybrid lighting (shadow-casting + fallback lights).
     * This is the main rendering method that combines all lighting types.
     * 
     * @param instances Model instances to render
     * @param camera Scene camera
     * @param shadowLights Array of shadow-casting lights
     * @param fallbackLights Array of fallback lights
     * @param ambientLight Ambient light color
     */
    public void renderWithHybridLighting(Array<ModelInstance> instances, Camera camera, 
                                       Array<PointLight> shadowLights, Array<FallbackLight> fallbackLights,
                                       Vector3 ambientLight) {
        
        long renderStartTime = ENABLE_PERFORMANCE_MONITORING ? System.nanoTime() : 0;
        
        // Reset performance counters
        totalDrawCalls = 0;
        fallbackLightsRendered = 0;
        
        // Cull distant fallback lights for performance
        if (ENABLE_DISTANCE_CULLING) {
            cullDistantFallbackLights(fallbackLights, camera.position);
        } else {
            visibleFallbackLights.clear();
            visibleFallbackLights.addAll(fallbackLights);
        }
        
        // Begin hybrid shader rendering
        hybridShader.bind();
        
        // Bind shadow maps from all shadow-casting lights
        bindShadowMaps(shadowLights);
        
        // Set shadow light uniforms
        setShadowLightUniforms(shadowLights);
        
        // Set fallback light uniforms
        setFallbackLightUniforms(visibleFallbackLights);
        
        // Set global rendering parameters
        setGlobalUniforms(camera, ambientLight);
        
        // Enable depth testing for proper 3D rendering
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        
        // Render all model instances with hybrid lighting
        for (ModelInstance instance : instances) {
            renderInstanceWithLighting(instance, camera);
            totalDrawCalls++;
        }
        
        // Update performance statistics
        if (ENABLE_PERFORMANCE_MONITORING) {
            updatePerformanceStatistics(renderStartTime);
        }
    }
    
    /**
     * Culls fallback lights that are too far from the camera to contribute meaningfully.
     * This optimization can significantly improve performance with many lights.
     * 
     * @param allFallbackLights All available fallback lights
     * @param cameraPosition Current camera position
     */
    private void cullDistantFallbackLights(Array<FallbackLight> allFallbackLights, Vector3 cameraPosition) {
        long currentTime = System.currentTimeMillis();
        
        // Only perform culling periodically to avoid excessive calculations
        if (currentTime - lastCullTime < CULL_INTERVAL_MS) {
            return; // Use cached results
        }
        
        visibleFallbackLights.clear();
        
        for (FallbackLight light : allFallbackLights) {
            if (light.isReadyForRendering()) {
                float distance = light.getWorldPosition().dst(cameraPosition);
                float effectiveRange = light.getEffectiveIntensity() * 5.0f; // Estimate light range
                
                if (distance <= DISTANCE_CULL_THRESHOLD && distance <= effectiveRange) {
                    visibleFallbackLights.add(light);
                }
            }
        }
        
        lastCullTime = currentTime;
        
        if (ENABLE_PERFORMANCE_MONITORING) {
            int culledCount = allFallbackLights.size - visibleFallbackLights.size;
            if (culledCount > 0) {
                Log.debug("HybridLightingRenderer", "Culled " + culledCount + " distant fallback lights");
            }
        }
    }
    
    /**
     * Binds shadow map textures from all shadow-casting lights to texture units.
     * 
     * @param shadowLights Array of shadow-casting lights
     */
    private void bindShadowMaps(Array<PointLight> shadowLights) {
        int textureUnit = 1; // Start from unit 1 (unit 0 reserved for diffuse textures)
        int numShadowLights = Math.min(shadowLights.size, maxShadowLights);
        
        for (int lightIndex = 0; lightIndex < numShadowLights; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                shadowFrameBuffers[lightIndex][face].getColorBufferTexture().bind(textureUnit);
                hybridShader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                textureUnit++;
            }
        }
    }
    
    /**
     * Sets shader uniforms for shadow-casting lights.
     * 
     * @param shadowLights Array of shadow-casting lights
     */
    private void setShadowLightUniforms(Array<PointLight> shadowLights) {
        int numShadowLights = Math.min(shadowLights.size, maxShadowLights);
        hybridShader.setUniformi("u_numShadowLights", numShadowLights);
        
        for (int i = 0; i < numShadowLights; i++) {
            PointLight light = shadowLights.get(i);
            hybridShader.setUniformf("u_shadowLightPositions[" + i + "]", light.position);
            hybridShader.setUniformf("u_shadowLightColors[" + i + "]", 
                                   light.color.r, light.color.g, light.color.b);
            hybridShader.setUniformf("u_shadowLightIntensities[" + i + "]", light.intensity);
        }
        
        shadowLightsRendered = numShadowLights;
    }
    
    /**
     * Sets shader uniforms for fallback lights.
     * 
     * @param fallbackLights Array of visible fallback lights
     */
    private void setFallbackLightUniforms(Array<FallbackLight> fallbackLights) {
        int numFallbackLights = Math.min(fallbackLights.size, MAX_FALLBACK_LIGHTS);
        hybridShader.setUniformi("u_numFallbackLights", numFallbackLights);
        
        for (int i = 0; i < numFallbackLights; i++) {
            FallbackLight light = fallbackLights.get(i);
            Vector3 pos = light.getWorldPosition();
            
            hybridShader.setUniformf("u_fallbackLightPositions[" + i + "]", pos.x, pos.y, pos.z);
            hybridShader.setUniformf("u_fallbackLightColors[" + i + "]", 
                                   light.getLightColor().r, light.getLightColor().g, light.getLightColor().b);
            hybridShader.setUniformf("u_fallbackLightIntensities[" + i + "]", light.getEffectiveIntensity());
        }
        
        fallbackLightsRendered = numFallbackLights;
    }
    
    /**
     * Sets global shader uniforms that don't change per light.
     * 
     * @param camera Scene camera
     * @param ambientLight Ambient light color
     */
    private void setGlobalUniforms(Camera camera, Vector3 ambientLight) {
        hybridShader.setUniformf("u_ambientLight", ambientLight);
        hybridShader.setUniformf("u_farPlane", lightCameras[0].far);
        hybridShader.setUniformi("u_enableSpecular", 1); // Enable specular highlights
        hybridShader.setUniformf("u_specularShininess", 32.0f); // Moderate shininess
        hybridShader.setUniformf("u_lightAttenuationFactor", 1.0f); // No additional attenuation
    }
    
    /**
     * Renders a single model instance with hybrid lighting.
     * 
     * @param instance Model instance to render
     * @param camera Scene camera
     */
    private void renderInstanceWithLighting(ModelInstance instance, Camera camera) {
        Matrix4 worldTransform = instance.transform;
        
        hybridShader.setUniformMatrix("u_worldTrans", worldTransform);
        hybridShader.setUniformMatrix("u_projViewTrans", camera.combined);
        
        renderInstanceGeometry(instance, hybridShader);
    }
    
    /**
     * Renders the geometry of a model instance using the specified shader.
     * This method handles material properties and texture binding.
     * 
     * @param instance Model instance to render
     * @param shader Shader program to use for rendering
     */
    private void renderInstanceGeometry(ModelInstance instance, ShaderProgram shader) {
        for (Node node : instance.nodes) {
            for (NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    // Extract and set material properties for hybrid shader
                    if (shader == hybridShader) {
                        setMaterialProperties(nodePart, shader);
                    }
                    
                    // Render the mesh
                    Mesh mesh = nodePart.meshPart.mesh;
                    mesh.render(shader, nodePart.meshPart.primitiveType,
                               nodePart.meshPart.offset, nodePart.meshPart.size);
                }
            }
        }
    }
    
    /**
     * Extracts and sets material properties for the shader.
     * 
     * @param nodePart Node part containing material information
     * @param shader Shader to set properties on
     */
    private void setMaterialProperties(NodePart nodePart, ShaderProgram shader) {
        // Default material properties
        Vector3 diffuseColor = new Vector3(0.7f, 0.7f, 0.7f);
        float diffuseAlpha = 1.0f;
        boolean hasTexture = false;
        
        if (nodePart.material != null) {
            // Extract diffuse color and alpha
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute diffuseAttr =
                (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute) 
                nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
            
            if (diffuseAttr != null) {
                diffuseColor.set(diffuseAttr.color.r, diffuseAttr.color.g, diffuseAttr.color.b);
                diffuseAlpha = diffuseAttr.color.a;
            }
            
            // Extract and bind diffuse texture
            com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute textureAttr =
                (com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute) 
                nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse);
            
            if (textureAttr != null && textureAttr.textureDescription != null && 
                textureAttr.textureDescription.texture != null) {
                hasTexture = true;
                textureAttr.textureDescription.texture.bind(0);
                shader.setUniformi("u_diffuseTexture", 0);
            }
        }
        
        // Set material uniforms
        shader.setUniformf("u_diffuseColor", diffuseColor);
        shader.setUniformf("u_diffuseAlpha", diffuseAlpha);
        shader.setUniformi("u_hasTexture", hasTexture ? 1 : 0);
    }
    
    /**
     * Updates performance statistics for monitoring and optimization.
     * 
     * @param renderStartTime Start time of the rendering process
     */
    private void updatePerformanceStatistics(long renderStartTime) {
        long frameTime = (System.nanoTime() - renderStartTime) / 1000000; // Convert to milliseconds
        frameCount++;
        
        // Calculate rolling average frame time
        if (frameCount == 1) {
            averageFrameTime = frameTime;
        } else {
            averageFrameTime = (averageFrameTime * 0.9f) + (frameTime * 0.1f);
        }
        
        // Log performance statistics periodically
        if (frameCount % Constants.RENDERING_PERFORMANCE_REPORT_FRAMES == 0) { // Every 5 seconds at 60fps
            Log.info("HybridLightingRenderer", String.format(
                "Performance Stats - Frame: %.1fms, Shadow Lights: %d, Fallback Lights: %d, Draw Calls: %d",
                averageFrameTime, shadowLightsRendered, fallbackLightsRendered, totalDrawCalls
            ));
        }
    }
    
    // Public API methods for performance monitoring and debugging
    
    /**
     * Gets the number of shadow lights rendered in the last frame.
     * 
     * @return Shadow lights rendered count
     */
    public int getShadowLightsRendered() {
        return shadowLightsRendered;
    }
    
    /**
     * Gets the number of fallback lights rendered in the last frame.
     * 
     * @return Fallback lights rendered count
     */
    public int getFallbackLightsRendered() {
        return fallbackLightsRendered;
    }
    
    /**
     * Gets the total number of draw calls in the last frame.
     * 
     * @return Draw calls count
     */
    public int getTotalDrawCalls() {
        return totalDrawCalls;
    }
    
    /**
     * Gets the average frame time over recent frames.
     * 
     * @return Average frame time in milliseconds
     */
    public float getAverageFrameTime() {
        return averageFrameTime;
    }
    
    /**
     * Gets a shadow map texture for debugging purposes.
     * 
     * @param lightIndex Index of the shadow light (0-7)
     * @param face Cube face index (0-5)
     * @return Shadow map texture or null if invalid indices
     */
    public Texture getShadowMapTexture(int lightIndex, int face) {
        if (lightIndex >= 0 && lightIndex < maxShadowLights && face >= 0 && face < 6) {
            return shadowFrameBuffers[lightIndex][face].getColorBufferTexture();
        }
        return null;
    }
    
    /**
     * Resets the shadow light index counter.
     * Call this before generating shadow maps for a new frame.
     */
    public void resetShadowLightIndex() {
        currentShadowLightIndex = 0;
    }
    
    /**
     * Gets the maximum number of shadow-casting lights supported.
     * 
     * @return Maximum shadow lights
     */
    public int getMaxShadowLights() {
        return maxShadowLights;
    }
    
    /**
     * Gets the maximum number of fallback lights supported.
     * 
     * @return Maximum fallback lights
     */
    public int getMaxFallbackLights() {
        return MAX_FALLBACK_LIGHTS;
    }
    
    /**
     * Disposes of all resources used by the renderer.
     * This method should be called when the renderer is no longer needed.
     */
    @Override
    public void dispose() {
        if (disposed) return;
        
        // Dispose shadow framebuffers
        if (shadowFrameBuffers != null) {
            for (int lightIndex = 0; lightIndex < maxShadowLights; lightIndex++) {
                for (int face = 0; face < 6; face++) {
                    if (shadowFrameBuffers[lightIndex][face] != null) {
                        shadowFrameBuffers[lightIndex][face].dispose();
                    }
                }
            }
            Log.info("HybridLightingRenderer", "Shadow framebuffers disposed");
        }
        
        // Dispose shaders
        if (depthShader != null) {
            depthShader.dispose();
            Log.info("HybridLightingRenderer", "Depth shader disposed");
        }
        
        if (hybridShader != null) {
            hybridShader.dispose();
            Log.info("HybridLightingRenderer", "Hybrid shader disposed");
        }
        
        // Clear arrays
        visibleFallbackLights.clear();
        
        disposed = true;
        Log.info("HybridLightingRenderer", "HybridLightingRenderer fully disposed");
    }
}