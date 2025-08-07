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
    private final LightmapBaker lightmapBaker;
    private final CubeShadowMapRenderer shadowRenderer;
    
    // Unified lighting shader
    private ShaderProgram unifiedShader;
    
    // Rendering configuration
    private float shadowBias = 0.005f;
    private float lightmapBlendFactor = 0.7f; // How much to favor baked lighting over dynamic
    private float lightmapIntensity = 1.0f;
    
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
        
        lightmapBaker = new LightmapBaker();
        shadowRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowedLights);
        
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
     * Render geometry with unified lighting (baked + dynamic)
     */
    public void render(Array<ModelInstance> instances, Camera camera, Vector3 viewerPosition, 
                      ObjectMap<String, Texture> lightmaps, Vector3 ambientLight) {
        
        long startTime = System.currentTimeMillis();
        lastFrameDrawCalls = 0;
        
        // Get lights from manager
        Array<PointLight> shadowedLights = lightManager.getShadowCastingLights(viewerPosition);
        Array<PointLight> allDynamicLights = lightManager.getAllDynamicLights(viewerPosition);
        
        // Separate unshadowed lights
        Array<PointLight> unshadowedLights = new Array<>();
        for (PointLight light : allDynamicLights) {
            if (!shadowedLights.contains(light, true)) {
                unshadowedLights.add(light);
            }
        }
        
        // Generate shadow maps for shadowed lights
        if (shadowedLights.size > 0) {
            shadowRenderer.resetLightIndex();
            for (PointLight light : shadowedLights) {
                shadowRenderer.generateCubeShadowMap(instances, light);
            }
        }
        
        // Render with unified lighting
        renderWithUnifiedLighting(instances, camera, shadowedLights, unshadowedLights, lightmaps, ambientLight);
        
        // Update statistics
        lastFrameShadowedLights = shadowedLights.size;
        lastFrameUnshadowedLights = unshadowedLights.size;
        lastFrameRenderTime = System.currentTimeMillis() - startTime;
    }
    
    private void renderWithUnifiedLighting(Array<ModelInstance> instances, Camera camera,
                                         Array<PointLight> shadowedLights, Array<PointLight> unshadowedLights,
                                         ObjectMap<String, Texture> lightmaps, Vector3 ambientLight) {
        
        unifiedShader.bind();
        
        // Set common uniforms
        unifiedShader.setUniformMatrix("u_projViewTrans", camera.combined);
        unifiedShader.setUniformf("u_ambientLight", ambientLight);
        unifiedShader.setUniformf("u_shadowBias", shadowBias);
        unifiedShader.setUniformf("u_lightmapBlendFactor", lightmapBlendFactor);
        unifiedShader.setUniformf("u_lightmapIntensity", lightmapIntensity);
        unifiedShader.setUniformf("u_farPlane", 50.0f); // Should match shadow camera far plane
        
        // Bind shadow maps
        bindShadowMaps(shadowedLights);
        
        // Set shadowed light uniforms
        setShadowedLightUniforms(shadowedLights);
        
        // Set unshadowed light uniforms
        setUnshadowedLightUniforms(unshadowedLights);
        
        // Enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        
        // Render each instance
        for (ModelInstance instance : instances) {
            renderInstance(instance, lightmaps);
            lastFrameDrawCalls++;
        }
    }
    
    private void bindShadowMaps(Array<PointLight> shadowedLights) {
        int textureUnit = 1; // Start from texture unit 1 (0 is reserved for diffuse)
        int numShadowLights = Math.min(shadowedLights.size, 4);
        
        for (int lightIndex = 0; lightIndex < numShadowLights; lightIndex++) {
            for (int face = 0; face < 6; face++) {
                Texture shadowMap = shadowRenderer.getShadowMapTexture(lightIndex, face);
                if (shadowMap != null) {
                    shadowMap.bind(textureUnit);
                    unifiedShader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                    textureUnit++;
                }
            }
        }
    }
    
    private void setShadowedLightUniforms(Array<PointLight> shadowedLights) {
        int numLights = Math.min(shadowedLights.size, 4);
        unifiedShader.setUniformi("u_numShadowLights", numLights);
        
        for (int i = 0; i < numLights; i++) {
            PointLight light = shadowedLights.get(i);
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
        
        // Determine if this instance has a lightmap
        String lightmapKey = instance.userData instanceof String ? (String) instance.userData : null;
        Texture lightmap = (lightmapKey != null) ? lightmaps.get(lightmapKey) : null;
        
        if (lightmap != null) {
            lightmap.bind(25); // Use high texture unit for lightmaps
            unifiedShader.setUniformi("u_lightmapTexture", 25);
            unifiedShader.setUniformi("u_hasLightmap", 1);
        } else {
            unifiedShader.setUniformi("u_hasLightmap", 0);
        }
        
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
        
        disposed = true;
        Log.info("UnifiedLightingRenderer", "UnifiedLightingRenderer disposed");
    }
}