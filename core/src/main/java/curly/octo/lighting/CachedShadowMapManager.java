package curly.octo.lighting;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Manages cached shadow maps for static lights to avoid regenerating them every frame.
 * Generates cube shadow maps once and reuses them until geometry changes.
 */
public class CachedShadowMapManager implements Disposable {
    
    private final CubeShadowMapRenderer shadowRenderer;
    
    // Cache of pre-generated shadow maps by light ID
    private final ObjectMap<String, CachedShadowMap> cachedShadowMaps;
    
    // Geometry fingerprint for cache invalidation
    private long geometryFingerprint = 0;
    private long lastKnownFingerprint = -1;
    
    private boolean disposed = false;
    
    public CachedShadowMapManager(CubeShadowMapRenderer shadowRenderer) {
        this.shadowRenderer = shadowRenderer;
        this.cachedShadowMaps = new ObjectMap<>();
        
        Log.info("CachedShadowMapManager", "Initialized cached shadow map manager");
    }
    
    /**
     * Update cached shadow maps for static lights if needed
     */
    public void updateCachedShadowMaps(Array<ModelInstance> geometry, Array<Light> staticLights) {
        // Calculate geometry fingerprint for cache invalidation
        long newFingerprint = calculateGeometryFingerprint(geometry);
        
        boolean geometryChanged = (newFingerprint != lastKnownFingerprint);
        if (geometryChanged) {
            Log.info("CachedShadowMapManager", "Geometry changed (fingerprint " + 
                lastKnownFingerprint + " -> " + newFingerprint + "), invalidating shadow map cache");
            clearAllCachedShadowMaps();
            lastKnownFingerprint = newFingerprint;
        }
        
        // Update shadow maps for lights that need them
        int lightsProcessed = 0;
        int lightsCached = 0;
        
        for (Light light : staticLights) {
            if (!light.isEnabled() || light.getType() != LightType.BAKED_STATIC) continue;
            
            lightsProcessed++;
            
            String lightId = light.getId();
            CachedShadowMap cached = cachedShadowMaps.get(lightId);
            
            // Check if we need to generate/regenerate shadow map
            boolean needsGeneration = (cached == null) || geometryChanged || 
                !light.getPosition().equals(cached.lightPosition) ||
                light.getIntensity() != cached.lightIntensity;
            
            if (needsGeneration) {
                Log.info("CachedShadowMapManager", "Generating shadow maps for light " + lightId + 
                    " at (" + light.getPosition().x + "," + light.getPosition().y + "," + light.getPosition().z + ")");
                
                // Create PointLight for shadow generation
                PointLight pointLight = new PointLight();
                pointLight.set(light.getColor(), light.getPosition(), light.getIntensity());
                
                // Generate cube shadow map using existing renderer
                shadowRenderer.generateCubeShadowMap(geometry, pointLight);
                
                // Cache the results
                CachedShadowMap newCache = new CachedShadowMap();
                newCache.lightPosition.set(light.getPosition());
                newCache.lightIntensity = light.getIntensity();
                
                // Copy shadow map textures from renderer (we need to store them separately)
                // Note: This assumes the shadow renderer maintains the textures
                // If textures are reused, we might need to copy them to separate textures
                for (int face = 0; face < 6; face++) {
                    newCache.shadowMapTextures[face] = shadowRenderer.getShadowMapTexture(0, face);
                }
                
                cachedShadowMaps.put(lightId, newCache);
                lightsCached++;
                
                Log.info("CachedShadowMapManager", "Cached shadow maps for light " + lightId);
            }
        }
        
        Log.info("CachedShadowMapManager", "Processed " + lightsProcessed + " static lights, " + 
            lightsCached + " needed cache updates, " + cachedShadowMaps.size + " total cached");
    }
    
    /**
     * Get cached shadow maps as PointLights that can be fed to the unified renderer
     */
    public Array<PointLight> getCachedLightsAsPointLights(Array<Light> staticLights) {
        Array<PointLight> pointLights = new Array<>();
        
        for (Light light : staticLights) {
            if (!light.isEnabled() || light.getType() != LightType.BAKED_STATIC) continue;
            
            CachedShadowMap cached = cachedShadowMaps.get(light.getId());
            if (cached != null) {
                // Create PointLight that represents this cached shadow-mapped light
                PointLight pointLight = new PointLight();
                pointLight.set(light.getColor(), light.getPosition(), light.getIntensity());
                pointLights.add(pointLight);
            }
        }
        
        Log.info("CachedShadowMapManager", "Returning " + pointLights.size + " cached lights as PointLights");
        return pointLights;
    }
    
    /**
     * Bind cached shadow maps to texture units for rendering.
     * Should be called before rendering with the unified shader.
     */
    public int bindCachedShadowMaps(Array<Light> staticLights, int startingTextureUnit, 
                                   com.badlogic.gdx.graphics.glutils.ShaderProgram shader) {
        int textureUnit = startingTextureUnit;
        int lightIndex = 0;
        
        for (Light light : staticLights) {
            if (!light.isEnabled() || light.getType() != LightType.BAKED_STATIC) continue;
            if (lightIndex >= 4) break; // Match unified renderer limit
            
            CachedShadowMap cached = cachedShadowMaps.get(light.getId());
            if (cached != null) {
                // Bind all 6 faces of the cube shadow map
                for (int face = 0; face < 6; face++) {
                    if (cached.shadowMapTextures[face] != null) {
                        cached.shadowMapTextures[face].bind(textureUnit);
                        shader.setUniformi("u_cubeShadowMaps[" + (lightIndex * 6 + face) + "]", textureUnit);
                        textureUnit++;
                    }
                }
                lightIndex++;
            }
        }
        
        Log.info("CachedShadowMapManager", "Bound " + lightIndex + " cached shadow-mapped lights to texture units " + 
            startingTextureUnit + "-" + (textureUnit - 1));
        
        return textureUnit; // Return next available texture unit
    }
    
    /**
     * Calculate a simple fingerprint of the geometry for cache invalidation
     */
    private long calculateGeometryFingerprint(Array<ModelInstance> geometry) {
        long fingerprint = 0;
        
        for (ModelInstance instance : geometry) {
            // Use transform matrix and node count as a simple fingerprint
            fingerprint ^= Float.floatToIntBits(instance.transform.val[12]); // X position
            fingerprint ^= Float.floatToIntBits(instance.transform.val[13]); // Y position  
            fingerprint ^= Float.floatToIntBits(instance.transform.val[14]); // Z position
            fingerprint ^= instance.nodes.size;
        }
        
        return fingerprint;
    }
    
    /**
     * Clear all cached shadow maps (e.g., when geometry changes)
     */
    private void clearAllCachedShadowMaps() {
        Log.info("CachedShadowMapManager", "Clearing " + cachedShadowMaps.size + " cached shadow maps");
        
        // Note: We don't dispose the textures here because they might be owned by CubeShadowMapRenderer
        // In a more robust implementation, we'd need to manage texture ownership carefully
        
        cachedShadowMaps.clear();
    }
    
    /**
     * Manually invalidate cache for a specific light
     */
    public void invalidateLight(String lightId) {
        CachedShadowMap removed = cachedShadowMaps.remove(lightId);
        if (removed != null) {
            Log.info("CachedShadowMapManager", "Invalidated cached shadow maps for light " + lightId);
        }
    }
    
    /**
     * Manually invalidate entire cache (e.g., when map changes significantly)
     */
    public void invalidateAll() {
        clearAllCachedShadowMaps();
        lastKnownFingerprint = -1;
        Log.info("CachedShadowMapManager", "Invalidated entire shadow map cache");
    }
    
    /**
     * Get cache statistics
     */
    public int getCachedLightCount() {
        return cachedShadowMaps.size;
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        
        clearAllCachedShadowMaps();
        
        disposed = true;
        Log.info("CachedShadowMapManager", "CachedShadowMapManager disposed");
    }
    
    /**
     * Container for cached shadow map data
     */
    private static class CachedShadowMap {
        public final com.badlogic.gdx.math.Vector3 lightPosition = new com.badlogic.gdx.math.Vector3();
        public float lightIntensity;
        public final Texture[] shadowMapTextures = new Texture[6]; // 6 faces of cube
    }
}