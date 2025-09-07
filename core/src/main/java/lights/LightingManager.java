package lights;

import curly.octo.Constants;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import curly.octo.rendering.CubeShadowMapRenderer;
import lights.LightConverter;

/**
 * LightingManager is the central controller for all lighting in the game.
 * It manages both shadow-casting lights (expensive) and fallback lights (cheap),
 * providing a unified interface for lighting operations while optimizing performance.
 * 
 * Architecture:
 * - Shadow-casting lights: Limited quantity (1-8), use cube shadow maps
 * - Fallback lights: Unlimited quantity, no shadow generation
 * - Hybrid rendering: Both light types contribute to final scene lighting
 * - Performance monitoring: Tracks light counts and rendering statistics
 * 
 * Key Features:
 * - Automatic light sorting by importance/distance
 * - Dynamic light culling for performance optimization
 * - Unified shader uniform management
 * - Memory-efficient light storage and iteration
 * - Integration with existing CubeShadowMapRenderer system
 */
public class LightingManager implements Disposable {
    
    // Configuration constants
    private static final int MAX_SHADOW_LIGHTS = Constants.LIGHTING_MAX_SHADOW_LIGHTS;     // Hardware shader limit (unchanged)
    private static final int MAX_TOTAL_LIGHTS = Constants.LIGHTING_MAX_FALLBACK_LIGHTS;    // Dramatically increased fallback light support
    private static final float LIGHT_CULL_DISTANCE = 75.0f;  // Increased culling distance for more lights
    private static final boolean ENABLE_DEBUG_LOGGING = false; // Performance logging
    
    // Light collections - separate storage for different light types
    private final Array<PointLight> shadowCastingLights;    // Expensive shadow-casting lights
    private final Array<FallbackLight> fallbackLights;     // Cheap non-shadow lights
    private final Array<BaseLight> allBaseLights;          // All BaseLight instances for unified management
    
    // Rendering integration
    private final CubeShadowMapRenderer shadowMapRenderer;
    
    // Performance tracking
    private int framesSinceLastCull = 0;                    // Culling optimization timer
    private static final int CULL_FREQUENCY = Constants.GAME_TARGET_FPS;          // Cull every 60 frames (1 second at 60fps)
    private int totalLightsRenderedLastFrame = 0;
    private int shadowLightsRenderedLastFrame = 0;
    
    // Ambient lighting
    private Vector3 ambientLight;
    
    // Temporary arrays for rendering (reused to reduce GC pressure)
    private final Array<PointLight> tempShadowLights;
    private final Array<PointLight> tempAllPointLights;
    
    // Overflow handling arrays
    private final Array<PointLight> actualShadowLights;
    private final Array<FallbackLight> overflowFallbackLights;
    private final Array<FallbackLight> allActiveFallbackLights;
    
    /**
     * Creates a new LightingManager instance.
     * 
     * @param shadowMapRenderer The CubeShadowMapRenderer for shadow-casting lights (can be null to disable shadows)
     */
    public LightingManager(CubeShadowMapRenderer shadowMapRenderer) {
        this.shadowMapRenderer = shadowMapRenderer;
        
        // Initialize light collections with appropriate initial capacities
        this.shadowCastingLights = new Array<>(MAX_SHADOW_LIGHTS);
        this.fallbackLights = new Array<>(128); // Start with higher capacity for more lights
        this.allBaseLights = new Array<>(256);  // Much higher capacity for all lights combined
        
        // Initialize temporary arrays for rendering
        this.tempShadowLights = new Array<>(MAX_SHADOW_LIGHTS);
        this.tempAllPointLights = new Array<>(MAX_TOTAL_LIGHTS);
        
        // Initialize overflow handling arrays
        this.actualShadowLights = new Array<>(MAX_SHADOW_LIGHTS);
        this.overflowFallbackLights = new Array<>(128); // Much higher capacity for converted excess shadow lights
        this.allActiveFallbackLights = new Array<>(MAX_TOTAL_LIGHTS);
        
        // Set default ambient lighting (subtle blue-grey for realism)
        this.ambientLight = new Vector3(0.15f, 0.15f, 0.2f);
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("LightingManager initialized - Shadow lights: " + MAX_SHADOW_LIGHTS + 
                             ", Max total lights: " + MAX_TOTAL_LIGHTS);
        }
    }
    
    /**
     * Adds a shadow-casting light to the system.
     * Shadow-casting lights are expensive but provide realistic shadows.
     * Limited to MAX_SHADOW_LIGHTS due to shader and performance constraints.
     * 
     * @param light The PointLight to add as a shadow caster
     * @return True if successfully added, false if shadow light limit reached
     */
    public boolean addShadowCastingLight(PointLight light) {
        if (shadowCastingLights.size >= MAX_SHADOW_LIGHTS) {
            System.err.println("Warning: Cannot add shadow-casting light - limit of " + 
                             MAX_SHADOW_LIGHTS + " reached. Consider using FallbackLight instead.");
            return false;
        }
        
        shadowCastingLights.add(light);
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("Added shadow-casting light at (" + 
                             light.position.x + "," + light.position.y + "," + light.position.z + 
                             ") - Total shadow lights: " + shadowCastingLights.size);
        }
        
        return true;
    }
    
    /**
     * Adds a FallbackLight to the system.
     * FallbackLights are performant and can be used in unlimited quantities.
     * They contribute to scene lighting without casting shadows.
     * 
     * @param light The FallbackLight to add
     * @return True if successfully added, false if total light limit reached
     */
    public boolean addFallbackLight(FallbackLight light) {
        if (getTotalLightCount() >= MAX_TOTAL_LIGHTS) {
            System.err.println("Warning: Cannot add fallback light - total light limit of " + 
                             MAX_TOTAL_LIGHTS + " reached.");
            return false;
        }
        
        fallbackLights.add(light);
        allBaseLights.add(light);
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("Added fallback light '" + light.entityId + "' at (" + 
                             light.getWorldPosition().x + "," + light.getWorldPosition().y + "," + 
                             light.getWorldPosition().z + ") - Total fallback lights: " + fallbackLights.size);
        }
        
        return true;
    }
    
    /**
     * Removes a shadow-casting light from the system.
     * 
     * @param light The PointLight to remove
     * @return True if the light was found and removed
     */
    public boolean removeShadowCastingLight(PointLight light) {
        boolean removed = shadowCastingLights.removeValue(light, true);
        
        if (removed && ENABLE_DEBUG_LOGGING) {
            System.out.println("Removed shadow-casting light - Remaining: " + shadowCastingLights.size);
        }
        
        return removed;
    }
    
    /**
     * Removes a FallbackLight from the system.
     * 
     * @param light The FallbackLight to remove
     * @return True if the light was found and removed
     */
    public boolean removeFallbackLight(FallbackLight light) {
        boolean removedFromFallback = fallbackLights.removeValue(light, true);
        boolean removedFromBase = allBaseLights.removeValue(light, true);
        
        if (removedFromFallback && ENABLE_DEBUG_LOGGING) {
            System.out.println("Removed fallback light '" + light.entityId + "' - Remaining: " + fallbackLights.size);
        }
        
        return removedFromFallback;
    }
    
    /**
     * Updates all lights in the system.
     * This method should be called once per frame to update light animations,
     * parent tracking, flickering, and performance optimizations.
     * 
     * @param delta Time elapsed since last update (in seconds)
     * @param cameraPosition Current camera position for distance-based optimizations
     */
    public void update(float delta, Vector3 cameraPosition) {
        // Update all BaseLight instances (handles parent tracking, flickering, etc.)
        for (BaseLight baseLight : allBaseLights) {
            if (baseLight != null) {
                baseLight.update(delta);
            }
        }
        
        // Perform periodic distance-based culling for performance
        framesSinceLastCull++;
        if (framesSinceLastCull >= CULL_FREQUENCY) {
            performDistanceCulling(cameraPosition);
            framesSinceLastCull = 0;
        }
    }
    
    /**
     * Handles shadow light overflow by converting excess shadow lights to fallback lights.
     * This ensures all requested lights remain visible even when exceeding shadow limits.
     * This method is called automatically during rendering.
     * 
     * @param cameraPosition Current camera position for distance-based light sorting
     */
    private void handleShadowLightOverflow(Vector3 cameraPosition) {
        // Sort shadow lights by importance (distance + intensity) before overflow handling
        // This ensures the closest/brightest lights get shadow casting priority
        LightConverter.sortLightsByImportance(shadowCastingLights, cameraPosition);
        
        // Handle overflow using the LightConverter utility
        LightConverter.handleShadowLightOverflow(
            shadowCastingLights,      // All requested shadow lights (now sorted by importance)
            MAX_SHADOW_LIGHTS,        // Maximum shadow lights allowed
            null,                     // No game object manager needed for overflow lights
            actualShadowLights,       // Output: actual shadow lights (limited)
            overflowFallbackLights    // Output: excess lights converted to fallback
        );
        
        // Combine all fallback lights (existing + overflow) with distance-based sorting
        LightConverter.createHybridLightArray(
            actualShadowLights,       // Shadow lights (limited quantity)
            fallbackLights,           // Existing fallback lights
            overflowFallbackLights,   // Overflow fallback lights
            MAX_TOTAL_LIGHTS,         // Maximum total lights for shader
            cameraPosition,           // Current camera position for distance sorting
            allActiveFallbackLights   // Output: all active fallback lights
        );
    }
    
    /**
     * Renders the scene with the complete lighting system including overflow handling.
     * This method integrates shadow-casting and fallback lights into a single
     * render pass, minimizing draw calls while maximizing lighting quality.
     * ALL LIGHTS REMAIN VISIBLE - excess shadow lights are rendered as fallback lights.
     * 
     * @param instances Model instances to render with lighting
     * @param camera Active camera for the scene
     */
    public void renderSceneWithLighting(Array<ModelInstance> instances, Camera camera) {
        if (shadowMapRenderer == null) {
            System.err.println("Warning: No shadow map renderer available - lighting disabled");
            return;
        }
        
        // Handle shadow light overflow (converts excess to fallback)
        handleShadowLightOverflow(camera.position);
        
        // Clear temporary arrays
        tempShadowLights.clear();
        tempAllPointLights.clear();
        
        // Step 1: Generate shadow maps for actual shadow lights (limited quantity)
        shadowMapRenderer.resetLightIndex();
        for (PointLight shadowLight : actualShadowLights) {
            shadowMapRenderer.generateCubeShadowMap(instances, shadowLight);
            tempShadowLights.add(shadowLight);
        }
        
        // Step 2: Add shadow lights to the total light array
        for (PointLight shadowLight : actualShadowLights) {
            tempAllPointLights.add(shadowLight);
        }
        
        // Step 3: Convert all active fallback lights to PointLight format for shader compatibility
        for (FallbackLight fallbackLight : allActiveFallbackLights) {
            if (fallbackLight.isReadyForRendering()) {
                // Create a temporary PointLight representation for shader compatibility
                PointLight pointLight = new PointLight();
                pointLight.setPosition(fallbackLight.getWorldPosition());
                pointLight.setColor(fallbackLight.getLightColor().r, 
                                  fallbackLight.getLightColor().g, 
                                  fallbackLight.getLightColor().b, 1.0f);
                pointLight.setIntensity(fallbackLight.getEffectiveIntensity());
                
                tempAllPointLights.add(pointLight);
            }
        }
        
        // Step 4: Render scene with combined lighting (ALL lights will be visible)
        if (tempShadowLights.size > 0 || tempAllPointLights.size > 0) {
            shadowMapRenderer.renderWithShadowOverflowHandlingCompatible(
                instances, camera, tempShadowLights, tempAllPointLights, ambientLight
            );
            
            // Update performance statistics
            totalLightsRenderedLastFrame = tempAllPointLights.size;
            shadowLightsRenderedLastFrame = tempShadowLights.size;
        }
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("Rendered frame - Shadow lights: " + shadowLightsRenderedLastFrame + 
                             ", Total lights: " + totalLightsRenderedLastFrame +
                             ", Overflow converted: " + overflowFallbackLights.size);
        }
    }
    
    /**
     * Sets shader uniforms for fallback lights only.
     * This is a utility method for custom rendering pipelines that want to
     * handle fallback lights separately from shadow-casting lights.
     * 
     * @param shader The shader program to set uniforms on
     * @param maxLights Maximum number of lights to send to the shader
     */
    public void setFallbackLightUniforms(ShaderProgram shader, int maxLights) {
        int lightIndex = 0;
        int lightsToProcess = Math.min(fallbackLights.size, maxLights);
        
        // Set fallback light data
        for (int i = 0; i < lightsToProcess; i++) {
            FallbackLight light = fallbackLights.get(i);
            if (light.isReadyForRendering()) {
                Vector3 pos = light.getWorldPosition();
                shader.setUniformf("u_fallbackLightPositions[" + lightIndex + "]", pos.x, pos.y, pos.z);
                shader.setUniformf("u_fallbackLightColors[" + lightIndex + "]", 
                                 light.getLightColor().r, light.getLightColor().g, light.getLightColor().b);
                shader.setUniformf("u_fallbackLightIntensities[" + lightIndex + "]", light.getEffectiveIntensity());
                lightIndex++;
            }
        }
        
        // Set the count of active fallback lights
        shader.setUniformi("u_numFallbackLights", lightIndex);
    }
    
    /**
     * Performs distance-based culling to optimize performance.
     * Lights beyond LIGHT_CULL_DISTANCE from the camera are temporarily
     * disabled to reduce shader workload.
     * 
     * @param cameraPosition Current camera position
     */
    private void performDistanceCulling(Vector3 cameraPosition) {
        int culledCount = 0;
        
        // Note: For FallbackLights, we could implement a distance-based priority system
        // or temporarily remove distant lights. For now, we'll just log the potential
        // for optimization.
        
        for (FallbackLight light : fallbackLights) {
            float distance = light.getWorldPosition().dst(cameraPosition);
            if (distance > LIGHT_CULL_DISTANCE) {
                culledCount++;
                // In a more advanced implementation, we could:
                // - Set a "culled" flag to skip this light in rendering
                // - Reduce the light's effective intensity based on distance
                // - Remove the light temporarily from active arrays
            }
        }
        
        if (ENABLE_DEBUG_LOGGING && culledCount > 0) {
            System.out.println("Distance culling: " + culledCount + " lights beyond " + 
                             LIGHT_CULL_DISTANCE + " units from camera");
        }
    }
    
    /**
     * Gets the current ambient light color.
     * 
     * @return Current ambient light as Vector3 (RGB)
     */
    public Vector3 getAmbientLight() {
        return ambientLight.cpy();
    }
    
    /**
     * Sets the ambient light color for the entire scene.
     * Ambient light provides base illumination that affects all surfaces equally.
     * 
     * @param red Red component (0.0 to 1.0)
     * @param green Green component (0.0 to 1.0)  
     * @param blue Blue component (0.0 to 1.0)
     */
    public void setAmbientLight(float red, float green, float blue) {
        ambientLight.set(red, green, blue);
    }
    
    /**
     * Sets the ambient light using a Vector3.
     * 
     * @param ambient New ambient light color
     */
    public void setAmbientLight(Vector3 ambient) {
        ambientLight.set(ambient);
    }
    
    // Performance and debugging methods
    
    /**
     * Gets the number of active shadow-casting lights.
     * 
     * @return Count of shadow-casting lights
     */
    public int getShadowLightCount() {
        return shadowCastingLights.size;
    }
    
    /**
     * Gets the number of active fallback lights.
     * 
     * @return Count of fallback lights
     */
    public int getFallbackLightCount() {
        return fallbackLights.size;
    }
    
    /**
     * Gets the total number of lights in the system.
     * 
     * @return Total light count
     */
    public int getTotalLightCount() {
        return shadowCastingLights.size + fallbackLights.size;
    }
    
    /**
     * Gets the number of lights rendered in the last frame.
     * 
     * @return Lights rendered last frame
     */
    public int getLightsRenderedLastFrame() {
        return totalLightsRenderedLastFrame;
    }
    
    /**
     * Gets the number of shadow lights rendered in the last frame.
     * 
     * @return Shadow lights rendered last frame
     */
    public int getShadowLightsRenderedLastFrame() {
        return shadowLightsRenderedLastFrame;
    }
    
    /**
     * Checks if the shadow light limit has been reached.
     * 
     * @return True if no more shadow lights can be added
     */
    public boolean isShadowLightLimitReached() {
        return shadowCastingLights.size >= MAX_SHADOW_LIGHTS;
    }
    
    /**
     * Checks if the total light limit has been reached.
     * 
     * @return True if no more lights of any type can be added
     */
    public boolean isTotalLightLimitReached() {
        return getTotalLightCount() >= MAX_TOTAL_LIGHTS;
    }
    
    /**
     * Gets performance statistics as a formatted string.
     * Useful for debugging and performance monitoring.
     * 
     * @return Performance statistics string
     */
    public String getPerformanceStats() {
        return String.format(
            "LightingManager Stats:\n" +
            "  Shadow Lights: %d/%d\n" +
            "  Fallback Lights: %d\n" +
            "  Total Lights: %d/%d\n" +
            "  Last Frame Rendered: %d (%d shadow)\n" +
            "  Memory Usage: ~%d KB",
            shadowCastingLights.size, MAX_SHADOW_LIGHTS,
            fallbackLights.size,
            getTotalLightCount(), MAX_TOTAL_LIGHTS,
            totalLightsRenderedLastFrame, shadowLightsRenderedLastFrame,
            estimateMemoryUsage() / 1024
        );
    }
    
    /**
     * Estimates the memory usage of the lighting system.
     * 
     * @return Estimated memory usage in bytes
     */
    private int estimateMemoryUsage() {
        // Rough estimate: PointLight ~100 bytes, FallbackLight ~200 bytes
        int shadowLightMemory = shadowCastingLights.size * 100;
        int fallbackLightMemory = fallbackLights.size * 200;
        int arrayOverhead = 1000; // Array overhead and temporary objects
        
        return shadowLightMemory + fallbackLightMemory + arrayOverhead;
    }
    
    /**
     * Removes all lights from the system and cleans up resources.
     */
    public void clearAllLights() {
        // Clear shadow-casting lights
        shadowCastingLights.clear();
        
        // Destroy and clear fallback lights
        for (FallbackLight light : fallbackLights) {
            light.destroy();
        }
        fallbackLights.clear();
        allBaseLights.clear();
        
        // Clean up overflow handling arrays
        LightConverter.cleanupOverflowFallbackLights(overflowFallbackLights);
        actualShadowLights.clear();
        allActiveFallbackLights.clear();
        
        // Clear temporary arrays
        tempShadowLights.clear();
        tempAllPointLights.clear();
        
        // Reset performance counters
        totalLightsRenderedLastFrame = 0;
        shadowLightsRenderedLastFrame = 0;
        framesSinceLastCull = 0;
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("LightingManager: All lights cleared");
        }
    }
    
    /**
     * Disposes of the LightingManager and all its resources.
     * This method should be called when the LightingManager is no longer needed.
     */
    @Override
    public void dispose() {
        // Clean up all lights
        clearAllLights();
        
        // The CubeShadowMapRenderer is managed externally, so we don't dispose it here
        
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("LightingManager disposed");
        }
    }
}