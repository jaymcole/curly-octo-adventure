package curly.octo.lighting;

import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;

/**
 * Central manager for all lights in the game.
 * Handles different light types and optimizes rendering performance.
 */
public class LightManager {
    
    private final ObjectMap<String, Light> allLights;
    private final Array<Light> dynamicShadowedLights;
    private final Array<Light> dynamicUnshadowedLights;
    private final Array<Light> bakedLights;
    
    // Performance limits
    private int maxShadowedLights = 4; // Conservative default for performance
    private int maxUnshadowedLights = 16;
    
    // Culling settings
    private float lightCullingThreshold = 0.01f; // Minimum light contribution to render
    private Vector3 viewerPosition = new Vector3(); // For distance culling
    private float maxLightDistance = 50.0f; // Maximum distance to consider lights
    
    // Statistics for debug/profiling
    private int lastFrameShadowedLights = 0;
    private int lastFrameUnshadowedLights = 0;
    private int lastFrameCulledLights = 0;
    
    public LightManager() {
        allLights = new ObjectMap<>();
        dynamicShadowedLights = new Array<>();
        dynamicUnshadowedLights = new Array<>();
        bakedLights = new Array<>();
        
        Log.info("LightManager", "Initialized with limits: " + maxShadowedLights + 
            " shadowed, " + maxUnshadowedLights + " unshadowed lights");
    }
    
    /**
     * Add a light to the manager
     */
    public void addLight(Light light) {
        if (light == null || light.getId().isEmpty()) {
            Log.warn("LightManager", "Cannot add light with null or empty ID");
            return;
        }
        
        // Remove existing light with same ID
        removeLight(light.getId());
        
        // Add to master list
        allLights.put(light.getId(), light);
        
        // Add to appropriate type list
        switch (light.getType()) {
            case DYNAMIC_SHADOWED:
                dynamicShadowedLights.add(light);
                break;
            case DYNAMIC_UNSHADOWED:
                dynamicUnshadowedLights.add(light);
                break;
            case BAKED_STATIC:
                bakedLights.add(light);
                break;
        }
        
        Log.debug("LightManager", "Added light: " + light);
    }
    
    /**
     * Remove a light by ID
     */
    public void removeLight(String lightId) {
        Light light = allLights.remove(lightId);
        if (light != null) {
            dynamicShadowedLights.removeValue(light, true);
            dynamicUnshadowedLights.removeValue(light, true);
            bakedLights.removeValue(light, true);
            Log.debug("LightManager", "Removed light: " + lightId);
        }
    }
    
    /**
     * Clear all lights
     */
    public void clearAllLights() {
        allLights.clear();
        dynamicShadowedLights.clear();
        dynamicUnshadowedLights.clear();
        bakedLights.clear();
        Log.info("LightManager", "Cleared all lights");
    }
    
    /**
     * Get lights that should cast shadows, prioritized by significance
     */
    public Array<PointLight> getShadowCastingLights(Vector3 viewPosition) {
        updateViewerPosition(viewPosition);
        
        Array<PointLight> result = new Array<>();
        Array<LightWithDistance> candidates = new Array<>();
        
        // Collect shadow-casting candidates with distances
        for (Light light : dynamicShadowedLights) {
            if (!light.isEnabled() || !light.castsShadows()) continue;
            
            // No distance limit - render all lights for dark game environment
            float distance = light.getPosition().dst(viewPosition);
            
            float significance = calculateLightSignificance(light, distance);
            if (significance > lightCullingThreshold) {
                candidates.add(new LightWithDistance(light, distance, significance));
            }
        }
        
        // Sort by significance (highest first)
        candidates.sort((a, b) -> Float.compare(b.significance, a.significance));
        
        // Convert top candidates to PointLights
        int count = Math.min(candidates.size, maxShadowedLights);
        for (int i = 0; i < count; i++) {
            Light light = candidates.get(i).light;
            PointLight pointLight = new PointLight();
            pointLight.set(light.getColor(), light.getPosition(), light.getIntensity());
            result.add(pointLight);
        }
        
        lastFrameShadowedLights = result.size;
        lastFrameCulledLights = candidates.size - result.size;
        
        return result;
    }
    
    /**
     * Get all dynamic lights (both shadowed and unshadowed) for lighting calculation
     */
    public Array<PointLight> getAllDynamicLights(Vector3 viewPosition) {
        updateViewerPosition(viewPosition);
        
        Array<PointLight> result = new Array<>();
        Array<LightWithDistance> candidates = new Array<>();
        
        // Collect all dynamic lights
        for (Light light : dynamicShadowedLights) {
            if (!light.isEnabled()) continue;
            addLightCandidate(light, viewPosition, candidates);
        }
        
        for (Light light : dynamicUnshadowedLights) {
            if (!light.isEnabled()) continue;
            addLightCandidate(light, viewPosition, candidates);
        }
        
        // Sort by significance and take the best ones
        candidates.sort((a, b) -> Float.compare(b.significance, a.significance));
        
        int maxTotal = maxShadowedLights + maxUnshadowedLights;
        int count = Math.min(candidates.size, maxTotal);
        
        for (int i = 0; i < count; i++) {
            Light light = candidates.get(i).light;
            PointLight pointLight = new PointLight();
            pointLight.set(light.getColor(), light.getPosition(), light.getIntensity());
            result.add(pointLight);
        }
        
        // Update statistics
        lastFrameUnshadowedLights = Math.max(0, result.size - lastFrameShadowedLights);
        
        return result;
    }
    
    private void addLightCandidate(Light light, Vector3 viewPosition, Array<LightWithDistance> candidates) {
        // No distance limit - render all lights for dark game environment
        float distance = light.getPosition().dst(viewPosition);
        
        float significance = calculateLightSignificance(light, distance);
        if (significance > lightCullingThreshold) {
            candidates.add(new LightWithDistance(light, distance, significance));
        }
    }
    
    /**
     * Calculate light significance for prioritization
     * Optimized for dark environments where dim lights should be visible from far distances
     */
    private float calculateLightSignificance(Light light, float distance) {
        float attenuation = light.calculateAttenuation(distance);
        float colorBrightness = (light.getColor().r + light.getColor().g + light.getColor().b) / 3.0f;
        
        float significance = attenuation * colorBrightness;
        
        // Dark environment boost: lights are much more noticeable in darkness
        // Apply distance-scaled boost - closer lights get normal treatment, 
        // distant lights get exponential boost for visibility
        if (distance > 50.0f) {
            float darkBoost = 1.0f + (distance - 50.0f) * 0.02f; // Exponential boost for distant lights
            significance *= Math.min(darkBoost, 5.0f); // Cap at 5x boost
        }
        
        // Boost significance for shadow-casting lights
        if (light.getType() == LightType.DYNAMIC_SHADOWED) {
            significance *= 1.5f;
        }
        
        // Additional boost for very dim lights that would be visible in darkness
        if (light.getIntensity() <= 3.0f && significance > 0.001f) {
            significance *= 2.0f; // Double significance for dim atmospheric lights
        }
        
        return significance;
    }
    
    private void updateViewerPosition(Vector3 viewPosition) {
        this.viewerPosition.set(viewPosition);
    }
    
    /**
     * Get baked/static lights for lightmap rendering
     */
    public Array<Light> getBakedLights() {
        return bakedLights;
    }
    
    /**
     * Get a specific light by ID
     */
    public Light getLight(String lightId) {
        return allLights.get(lightId);
    }
    
    /**
     * Update the position of an existing light without removing/re-adding it
     * Returns true if the light was found and updated
     */
    public boolean updateLightPosition(String lightId, Vector3 newPosition) {
        Light light = allLights.get(lightId);
        if (light != null) {
            light.setPosition(newPosition);
            return true;
        }
        return false;
    }
    
    // Performance configuration
    public void setMaxShadowedLights(int max) {
        this.maxShadowedLights = Math.max(1, Math.min(8, max));
        Log.info("LightManager", "Set max shadowed lights to: " + maxShadowedLights);
    }
    
    public void setMaxUnshadowedLights(int max) {
        this.maxUnshadowedLights = Math.max(1, Math.min(32, max));
        Log.info("LightManager", "Set max unshadowed lights to: " + maxUnshadowedLights);
    }
    
    public void setLightCullingThreshold(float threshold) {
        this.lightCullingThreshold = Math.max(0.001f, threshold);
    }
    
    public void setMaxLightDistance(float distance) {
        this.maxLightDistance = Math.max(10.0f, distance);
    }
    
    // Statistics
    public int getLastFrameShadowedLights() { return lastFrameShadowedLights; }
    public int getLastFrameUnshadowedLights() { return lastFrameUnshadowedLights; }
    public int getLastFrameCulledLights() { return lastFrameCulledLights; }
    public int getTotalLights() { return allLights.size; }
    
    public void printStatistics() {
        Log.info("LightManager", String.format("Lights: %d total, %d shadowed, %d unshadowed, %d culled",
            getTotalLights(), lastFrameShadowedLights, lastFrameUnshadowedLights, lastFrameCulledLights));
    }
    
    /**
     * Helper class for sorting lights by distance and significance
     */
    private static class LightWithDistance {
        final Light light;
        final float distance;
        final float significance;
        
        LightWithDistance(Light light, float distance, float significance) {
            this.light = light;
            this.distance = distance;
            this.significance = significance;
        }
    }
}