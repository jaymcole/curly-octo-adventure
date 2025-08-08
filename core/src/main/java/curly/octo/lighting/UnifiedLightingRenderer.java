package curly.octo.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Unified lighting renderer that combines dynamic shadows, dynamic lights, and baked lightmaps
 * for optimal performance and visual quality.
 */
public class UnifiedLightingRenderer implements Disposable {
    
    private final LightManager lightManager;
    private final LightmapBaker lightmapBaker; // Keep for backward compatibility, but won't be used
    private final CubeShadowMapRenderer shadowRenderer;
    private final ShadowMapDebugRenderer debugRenderer;
    private final CachedShadowMapManager cachedShadowMapManager;
    
    // Unified lighting shader
    private ShaderProgram unifiedShader;
    
    // Rendering configuration
    private float shadowBias = 0.005f;
    private float lightmapBlendFactor = 0.1f; // How much to favor baked lighting over dynamic - REDUCED FOR DEBUG
    private float lightmapIntensity = 1.0f;
    
    // Debug configuration
    private boolean debugShadowMaps = false;
    private boolean debugShowDepth = true;
    
    // Statistics
    private int lastFrameDrawCalls = 0;
    private int lastFrameShadowedLights = 0;
    private int lastFrameUnshadowedLights = 0;
    private long lastFrameRenderTime = 0;
    
    private boolean disposed = false;
    
    public UnifiedLightingRenderer() {
        this(4, 16); // Default limits
    }
    
    public UnifiedLightingRenderer(int maxShadowedLights, int maxUnshadowedLights) {
        lightManager = new LightManager();
        lightManager.setMaxShadowedLights(maxShadowedLights);
        lightManager.setMaxUnshadowedLights(maxUnshadowedLights);
        
        lightmapBaker = new LightmapBaker(); // Keep for backward compatibility
        shadowRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowedLights);
        debugRenderer = new ShadowMapDebugRenderer();
        cachedShadowMapManager = new CachedShadowMapManager(shadowRenderer);
        
        loadUnifiedShader();
        
        Log.info("UnifiedLightingRenderer", "Initialized with " + maxShadowedLights + 
            " shadowed and " + maxUnshadowedLights + " unshadowed lights");
    }
    
    private void loadUnifiedShader() {
        try {
            String vertexShader = Gdx.files.internal("shaders/unified_lighting.vertex.glsl").readString();
            String fragmentShader = Gdx.files.internal("shaders/unified_lighting.fragment.glsl").readString();
            
            unifiedShader = new ShaderProgram(vertexShader, fragmentShader);
            
            if (!unifiedShader.isCompiled()) {
                Log.error("UnifiedLightingRenderer", "Unified shader compilation failed: " + unifiedShader.getLog());
                throw new RuntimeException("Unified lighting shader compilation failed");
            }
            
            Log.info("UnifiedLightingRenderer", "Unified lighting shader loaded successfully");
        } catch (Exception e) {
            Log.error("UnifiedLightingRenderer", "Failed to load unified shader: " + e.getMessage());
            throw new RuntimeException("Failed to load unified lighting shader", e);
        }
    }
    
    /**
     * Render geometry with unified lighting (cached static shadows + dynamic)
     */
    public void render(Array<ModelInstance> instances, Camera camera, Vector3 viewerPosition, 
                      ObjectMap<String, Texture> lightmaps, Vector3 ambientLight) {
        
        Log.info("UnifiedLightingRenderer", "RENDER START: " + instances.size + " instances, using cached shadow maps");
        
        long startTime = System.currentTimeMillis();
        lastFrameDrawCalls = 0;
        
        // Get dynamic lights from manager
        Array<PointLight> dynamicShadowedLights = lightManager.getShadowCastingLights(viewerPosition);
        Array<PointLight> allDynamicLights = lightManager.getAllDynamicLights(viewerPosition);
        
        // Update cached shadow maps for static lights
        Array<Light> staticLights = lightManager.getBakedLights();
        Log.info("UnifiedLightingRenderer", "Updating cached shadow maps for " + staticLights.size + " static lights");
        cachedShadowMapManager.updateCachedShadowMaps(instances, staticLights);
        
        // Get static lights as PointLights (they'll use cached shadow maps)
        Array<PointLight> cachedStaticLights = cachedShadowMapManager.getCachedLightsAsPointLights(staticLights);
        
        Log.info("UnifiedLightingRenderer", "Lights: " + dynamicShadowedLights.size + " dynamic shadowed, " + 
            allDynamicLights.size + " dynamic total, " + cachedStaticLights.size + " cached static");
        
        // Separate unshadowed dynamic lights
        Array<PointLight> unshadowedDynamicLights = new Array<>();
        for (PointLight light : allDynamicLights) {
            if (!dynamicShadowedLights.contains(light, true)) {
                unshadowedDynamicLights.add(light);
            }
        }
        
        // Generate shadow maps for dynamic shadowed lights only
        shadowRenderer.resetLightIndex();
        for (PointLight light : dynamicShadowedLights) {
            shadowRenderer.generateCubeShadowMap(instances, light);
        }
        
        // Combine all lights for rendering
        Array<PointLight> allShadowedLights = new Array<>();
        Array<PointLight> allUnshadowedLights = new Array<>();
        
        // Add dynamic lights
        allShadowedLights.addAll(dynamicShadowedLights);
        allUnshadowedLights.addAll(unshadowedDynamicLights);
        
        // Add cached static lights (they all have shadow maps)
        allShadowedLights.addAll(cachedStaticLights);
        
        Log.info("UnifiedLightingRenderer", "Final lighting: " + allShadowedLights.size + 
            " shadowed (" + dynamicShadowedLights.size + " dynamic + " + cachedStaticLights.size + " cached), " + 
            allUnshadowedLights.size + " unshadowed");
        
        // Render with unified lighting
        Log.info("UnifiedLightingRenderer", "About to render with unified lighting...");
        renderWithUnifiedLighting(instances, camera, allShadowedLights, allUnshadowedLights, lightmaps, ambientLight, staticLights);
        Log.info("UnifiedLightingRenderer", "Unified lighting render completed");
        
        // Debug render shadow maps if enabled
        if (debugShadowMaps) {
            int totalLightsWithShadows = allShadowedLights.size;
            if (totalLightsWithShadows > 0) {
                debugRenderer.renderShadowMaps(shadowRenderer, dynamicShadowedLights.size, cachedStaticLights.size, debugShowDepth);
            }
        }
        
        // Update statistics
        lastFrameShadowedLights = allShadowedLights.size;
        lastFrameUnshadowedLights = allUnshadowedLights.size;
        lastFrameRenderTime = System.currentTimeMillis() - startTime;
    }
    
    private void renderWithUnifiedLighting(Array<ModelInstance> instances, Camera camera,
                                         Array<PointLight> shadowedLights, Array<PointLight> unshadowedLights,
                                         ObjectMap<String, Texture> lightmaps, Vector3 ambientLight, 
                                         Array<Light> staticLights) {
        
        Log.info("UnifiedLightingRenderer", "renderWithUnifiedLighting: binding shader...");
        unifiedShader.bind();
        Log.info("UnifiedLightingRenderer", "Shader bound successfully");
        
        // Set common uniforms
        unifiedShader.setUniformMatrix("u_projViewTrans", camera.combined);
        unifiedShader.setUniformf("u_ambientLight", ambientLight);
        unifiedShader.setUniformf("u_shadowBias", shadowBias);
        unifiedShader.setUniformf("u_lightmapBlendFactor", lightmapBlendFactor);
        unifiedShader.setUniformf("u_lightmapIntensity", lightmapIntensity);
        unifiedShader.setUniformf("u_farPlane", 50.0f); // Should match shadow camera far plane
        
        // Bind shadow maps (both dynamic and cached)
        bindShadowMaps(shadowedLights, staticLights);
        
        // Set shadowed light uniforms
        setShadowedLightUniforms(shadowedLights);
        
        // Set unshadowed light uniforms
        setUnshadowedLightUniforms(unshadowedLights);
        
        // Enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        
        // Render each instance
        Log.info("UnifiedLightingRenderer", "Rendering " + instances.size + " instances...");
        for (ModelInstance instance : instances) {
            Log.info("UnifiedLightingRenderer", "Rendering instance with userData: " + instance.userData);
            renderInstance(instance, lightmaps);
            lastFrameDrawCalls++;
        }
        Log.info("UnifiedLightingRenderer", "Completed rendering all instances");
    }
    
    private void bindShadowMaps(Array<PointLight> shadowedLights, Array<Light> staticLights) {
        int textureUnit = 1; // Start from texture unit 1 (0 is reserved for diffuse)
        int lightIndex = 0;
        
        // Bind dynamic shadow maps first
        int numDynamicShadowLights = Math.min(shadowedLights.size, 4);
        Log.info("UnifiedLightingRenderer", "Binding " + numDynamicShadowLights + " dynamic shadow maps:");
        for (int i = 0; i < numDynamicShadowLights; i++) {
            for (int face = 0; face < 6; face++) {
                Texture shadowMap = shadowRenderer.getShadowMapTexture(i, face);
                if (shadowMap != null) {
                    shadowMap.bind(textureUnit);
                    unifiedShader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                    Log.debug("UnifiedLightingRenderer", "  Bound dynamic light[" + lightIndex + "] face[" + face + "] to texture unit " + textureUnit);
                    textureUnit++;
                } else {
                    Log.warn("UnifiedLightingRenderer", "  Missing dynamic shadow map for light[" + lightIndex + "] face[" + face + "]");
                }
            }
            lightIndex++;
        }
        
        // Bind cached shadow maps for remaining slots
        Log.info("UnifiedLightingRenderer", "Binding cached shadow maps starting at light index " + lightIndex + ":");
        textureUnit = cachedShadowMapManager.bindCachedShadowMaps(staticLights, textureUnit, unifiedShader);
        
        Log.info("UnifiedLightingRenderer", "Total shadow maps bound, next texture unit: " + textureUnit);
    }
    
    private void setShadowedLightUniforms(Array<PointLight> shadowedLights) {
        int numLights = Math.min(shadowedLights.size, 4);
        unifiedShader.setUniformi("u_numShadowLights", numLights);
        
        Log.info("UnifiedLightingRenderer", "Setting " + numLights + " shadowed light uniforms:");
        for (int i = 0; i < numLights; i++) {
            PointLight light = shadowedLights.get(i);
            Log.info("UnifiedLightingRenderer", "  Light[" + i + "]: pos(" + light.position.x + "," + light.position.y + "," + light.position.z + 
                ") color(" + light.color.r + "," + light.color.g + "," + light.color.b + ") intensity=" + light.intensity);
            unifiedShader.setUniformf("u_shadowLightPositions[" + i + "]", light.position);
            unifiedShader.setUniformf("u_shadowLightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
            unifiedShader.setUniformf("u_shadowLightIntensities[" + i + "]", light.intensity);
        }
    }
    
    private void setUnshadowedLightUniforms(Array<PointLight> unshadowedLights) {
        int numLights = Math.min(unshadowedLights.size, 12);
        unifiedShader.setUniformi("u_numUnshadowedLights", numLights);
        
        for (int i = 0; i < numLights; i++) {
            PointLight light = unshadowedLights.get(i);
            unifiedShader.setUniformf("u_unshadowedLightPositions[" + i + "]", light.position);
            unifiedShader.setUniformf("u_unshadowedLightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
            unifiedShader.setUniformf("u_unshadowedLightIntensities[" + i + "]", light.intensity);
        }
    }
    
    private void renderInstance(ModelInstance instance, ObjectMap<String, Texture> lightmaps) {
        unifiedShader.setUniformMatrix("u_worldTrans", instance.transform);
        
        // DISABLED: Using cached shadow maps instead of lightmaps
        // No longer apply lightmaps - using cached shadow map approach instead
        unifiedShader.setUniformi("u_hasLightmap", 0);
        
        Log.info("UnifiedLightingRenderer", "Rendering instance with cached shadow maps (lightmaps disabled)");
        
        // Render each node part
        for (Node node : instance.nodes) {
            for (NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    renderNodePart(nodePart);
                }
            }
        }
    }
    
    private void renderNodePart(NodePart nodePart) {
        // Extract material properties
        Vector3 diffuseColor = new Vector3(0.7f, 0.7f, 0.7f);
        float diffuseAlpha = 1.0f;
        boolean hasTexture = false;
        
        if (nodePart.material != null) {
            // Extract diffuse color
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute diffuseAttr = 
                (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute) 
                nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
            
            if (diffuseAttr != null) {
                diffuseColor.set(diffuseAttr.color.r, diffuseAttr.color.g, diffuseAttr.color.b);
                diffuseAlpha = diffuseAttr.color.a;
            }
            
            // Extract texture
            com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute textureAttr = 
                (com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute) 
                nodePart.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse);
            
            if (textureAttr != null && textureAttr.textureDescription != null && 
                textureAttr.textureDescription.texture != null) {
                hasTexture = true;
                textureAttr.textureDescription.texture.bind(0);
                unifiedShader.setUniformi("u_diffuseTexture", 0);
            }
        }
        
        // Set material uniforms
        unifiedShader.setUniformf("u_diffuseColor", diffuseColor);
        unifiedShader.setUniformf("u_diffuseAlpha", diffuseAlpha);
        unifiedShader.setUniformi("u_hasTexture", hasTexture ? 1 : 0);
        
        // Render the mesh
        Mesh mesh = nodePart.meshPart.mesh;
        mesh.render(unifiedShader, nodePart.meshPart.primitiveType,
                   nodePart.meshPart.offset, nodePart.meshPart.size);
    }
    
    // Light management methods
    public LightManager getLightManager() {
        return lightManager;
    }
    
    public LightmapBaker getLightmapBaker() {
        return lightmapBaker;
    }
    
    // Configuration methods
    public void setShadowBias(float bias) {
        this.shadowBias = Math.max(0.001f, bias);
    }
    
    public void setLightmapBlendFactor(float factor) {
        this.lightmapBlendFactor = Math.max(0.0f, Math.min(1.0f, factor));
    }
    
    public void setLightmapIntensity(float intensity) {
        this.lightmapIntensity = Math.max(0.0f, intensity);
    }
    
    // Debug configuration methods
    public void setDebugShadowMaps(boolean enable) {
        this.debugShadowMaps = enable;
        Log.info("UnifiedLightingRenderer", "Shadow map debug rendering " + (enable ? "enabled" : "disabled"));
    }
    
    public void setDebugShowDepth(boolean showDepth) {
        this.debugShowDepth = showDepth;
        Log.info("UnifiedLightingRenderer", "Shadow map debug mode: " + (showDepth ? "depth" : "color"));
    }
    
    public boolean isDebugShadowMapsEnabled() {
        return debugShadowMaps;
    }
    
    // Statistics
    public int getLastFrameDrawCalls() { return lastFrameDrawCalls; }
    public int getLastFrameShadowedLights() { return lastFrameShadowedLights; }
    public int getLastFrameUnshadowedLights() { return lastFrameUnshadowedLights; }
    public long getLastFrameRenderTime() { return lastFrameRenderTime; }
    
    public void printPerformanceStats() {
        Log.info("UnifiedLightingRenderer", String.format(
            "Frame stats: %d draw calls, %d shadowed lights, %d unshadowed lights, %dms render time",
            lastFrameDrawCalls, lastFrameShadowedLights, lastFrameUnshadowedLights, lastFrameRenderTime));
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        
        if (unifiedShader != null) {
            unifiedShader.dispose();
        }
        
        if (lightmapBaker != null) {
            lightmapBaker.dispose();
        }
        
        if (shadowRenderer != null) {
            shadowRenderer.dispose();
        }
        
        if (debugRenderer != null) {
            debugRenderer.dispose();
        }
        
        if (cachedShadowMapManager != null) {
            cachedShadowMapManager.dispose();
        }
        
        disposed = true;
        Log.info("UnifiedLightingRenderer", "UnifiedLightingRenderer disposed");
    }
}