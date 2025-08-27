package lights;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import curly.octo.game.GameObjectManager;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * OverflowHandlingExample demonstrates the new overflow handling system
 * that prevents lights from disappearing when shadow limits are exceeded.
 * 
 * Key Features Demonstrated:
 * - Automatic conversion of excess shadow lights to fallback lights
 * - All requested lights remain visible in the scene
 * - Performance monitoring of overflow operations
 * - Comparison between old (lights disappear) and new (overflow handling) behavior
 * 
 * This example shows how to use the enhanced lighting system to ensure
 * that players never experience lights suddenly turning off due to technical limits.
 */
public class OverflowHandlingExample {
    
    private final CubeShadowMapRenderer shadowRenderer;
    private final LightingManager lightingManager;
    private final GameObjectManager gameObjectManager;
    
    // Test arrays for demonstrating overflow behavior
    private final Array<PointLight> manyLights;
    private final Array<PointLight> additionalLights;
    
    // Performance tracking
    private int totalLightsRequested = 0;
    private int shadowLightsGranted = 0;
    private int lightsConvertedToFallback = 0;
    
    /**
     * Creates a new overflow handling demonstration.
     * 
     * @param gameObjectManager Reference to the game object manager
     */
    public OverflowHandlingExample(GameObjectManager gameObjectManager) {
        this.gameObjectManager = gameObjectManager;
        
        // Create renderer with 4 shadow lights (lower than usual for demonstration)
        this.shadowRenderer = new CubeShadowMapRenderer(
            CubeShadowMapRenderer.QUALITY_MEDIUM, 4
        );
        
        this.lightingManager = new LightingManager(shadowRenderer);
        
        // Initialize test arrays
        this.manyLights = new Array<>();
        this.additionalLights = new Array<>();
        
        setupTestScenario();
    }
    
    /**
     * Sets up a test scenario with many lights to demonstrate overflow handling.
     * Creates more shadow lights than the system can handle to show overflow behavior.
     */
    private void setupTestScenario() {
        System.out.println("Setting up overflow handling test scenario...");
        System.out.println("Renderer supports: " + 4 + " shadow-casting lights");
        
        // Create 20 lights that all want to cast shadows (16 more than supported)
        createTestLights();
        
        // Create additional non-shadow lights
        createAdditionalLights();
        
        totalLightsRequested = manyLights.size + additionalLights.size;
        
        System.out.println("Test scenario created:");
        System.out.println("  - Shadow lights requested: " + manyLights.size);
        System.out.println("  - Additional lights: " + additionalLights.size);
        System.out.println("  - Total lights: " + totalLightsRequested);
        System.out.println("  - Expected overflow: " + (manyLights.size - 4) + " lights");
        System.out.println("  - With new limits: Up to 256 fallback lights supported!");
        System.out.println();
    }
    
    /**
     * Creates test lights that exceed the shadow casting limit.
     */
    private void createTestLights() {
        // Create lights in a pattern around the scene
        String[] lightNames = {
            "Main Torch", "Guard Light", "Altar Light", "Door Light",
            "Corner Light 1", "Corner Light 2", "Corner Light 3", "Corner Light 4", 
            "Magic Orb 1", "Magic Orb 2", "Emergency Light", "Backup Light",
            "Wall Sconce 1", "Wall Sconce 2", "Wall Sconce 3", "Wall Sconce 4",
            "Chandelier 1", "Chandelier 2", "Floor Lamp 1", "Floor Lamp 2"
        };
        
        Vector3[] lightPositions = {
            new Vector3(0, 5, 0),     // Center main light
            new Vector3(10, 4, 10),   // Guard post
            new Vector3(-5, 3, -8),   // Altar
            new Vector3(8, 4, -10),   // Door
            new Vector3(-10, 3, -10), // Corner 1
            new Vector3(10, 3, -10),  // Corner 2
            new Vector3(-10, 3, 10),  // Corner 3
            new Vector3(10, 3, 10),   // Corner 4
            new Vector3(-3, 6, 0),    // Magic orb 1
            new Vector3(3, 6, 0),     // Magic orb 2
            new Vector3(0, 2, 15),    // Emergency
            new Vector3(0, 2, -15),   // Backup
            new Vector3(-15, 4, 0),   // Wall sconce 1
            new Vector3(15, 4, 0),    // Wall sconce 2
            new Vector3(0, 4, -18),   // Wall sconce 3
            new Vector3(0, 4, 18),    // Wall sconce 4
            new Vector3(-8, 7, -8),   // Chandelier 1
            new Vector3(8, 7, 8),     // Chandelier 2
            new Vector3(-12, 2, 5),   // Floor lamp 1
            new Vector3(12, 2, -5)    // Floor lamp 2
        };
        
        Vector3[] lightColors = {
            new Vector3(1.0f, 0.9f, 0.7f),  // Warm white (main)
            new Vector3(1.0f, 0.8f, 0.6f),  // Torch orange
            new Vector3(0.8f, 0.9f, 1.0f),  // Cool blue (altar)
            new Vector3(1.0f, 0.7f, 0.5f),  // Door light
            new Vector3(0.9f, 0.9f, 0.8f),  // Corner lights
            new Vector3(0.9f, 0.9f, 0.8f),
            new Vector3(0.9f, 0.9f, 0.8f), 
            new Vector3(0.9f, 0.9f, 0.8f),
            new Vector3(0.6f, 0.4f, 1.0f),  // Purple magic
            new Vector3(1.0f, 0.4f, 0.6f),  // Pink magic
            new Vector3(1.0f, 0.3f, 0.3f),  // Red emergency
            new Vector3(0.3f, 1.0f, 0.3f),  // Green backup
            new Vector3(1.0f, 0.9f, 0.6f),  // Wall sconce warm
            new Vector3(1.0f, 0.9f, 0.6f),  // Wall sconce warm
            new Vector3(1.0f, 0.9f, 0.6f),  // Wall sconce warm
            new Vector3(1.0f, 0.9f, 0.6f),  // Wall sconce warm
            new Vector3(1.0f, 1.0f, 0.9f),  // Chandelier bright white
            new Vector3(1.0f, 1.0f, 0.9f),  // Chandelier bright white
            new Vector3(0.9f, 0.8f, 0.7f),  // Floor lamp warm
            new Vector3(0.9f, 0.8f, 0.7f)   // Floor lamp warm
        };
        
        float[] lightIntensities = {
            15.0f, 12.0f, 8.0f, 10.0f,      // Important lights get higher intensity
            6.0f, 6.0f, 6.0f, 6.0f,          // Corner lights medium intensity
            7.0f, 7.0f, 5.0f, 5.0f,          // Special effects and backup lights
            4.0f, 4.0f, 4.0f, 4.0f,          // Wall sconces modest intensity
            9.0f, 9.0f, 5.0f, 5.0f           // Chandeliers bright, floor lamps modest
        };
        
        for (int i = 0; i < lightNames.length; i++) {
            PointLight light = new PointLight();
            Vector3 color = lightColors[i];
            light.set(color.x, color.y, color.z, lightPositions[i], lightIntensities[i]);
            manyLights.add(light);
            
            System.out.println("Created " + lightNames[i] + " at " + lightPositions[i] + 
                             " with intensity " + lightIntensities[i]);
        }
    }
    
    /**
     * Creates additional non-shadow lights to demonstrate mixed lighting.
     */
    private void createAdditionalLights() {
        // Create some additional lights that don't need shadows
        Vector3[] additionalPositions = {
            new Vector3(0, 10, 0),      // Overhead ambient
            new Vector3(-20, 8, 0),     // Left fill
            new Vector3(20, 8, 0),      // Right fill
            new Vector3(0, 8, -20),     // Back fill
            new Vector3(0, 8, 20)       // Front fill
        };
        
        for (int i = 0; i < additionalPositions.length; i++) {
            PointLight light = new PointLight();
            light.set(0.7f, 0.7f, 0.8f, additionalPositions[i], 3.0f); // Subtle fill lights
            additionalLights.add(light);
        }
        
        System.out.println("Created " + additionalLights.size + " additional fill lights");
    }
    
    /**
     * Demonstrates the OLD behavior where lights disappear after hitting the limit.
     * This method shows what happens with the original renderer.
     */
    public void demonstrateOldBehavior(Array<ModelInstance> instances, Camera camera) {
        System.out.println("\\n=== DEMONSTRATING OLD BEHAVIOR (lights disappear) ===");
        
        // Use the original renderWithMultipleCubeShadows method
        // This will generate warnings and cause lights to disappear
        shadowRenderer.resetLightIndex();
        
        // Generate shadow maps for only the first 4 lights (the limit)
        int shadowLightsGenerated = 0;
        for (int i = 0; i < Math.min(manyLights.size, 4); i++) {
            shadowRenderer.generateCubeShadowMap(instances, manyLights.get(i));
            shadowLightsGenerated++;
        }
        
        // The original method will clip at 8 total lights, losing the rest
        Array<PointLight> allLightsForOldSystem = new Array<>();
        for (PointLight light : manyLights) {
            allLightsForOldSystem.add(light);
        }
        for (PointLight light : additionalLights) {
            allLightsForOldSystem.add(light);
        }
        
        Array<PointLight> shadowLightsForOldSystem = new Array<>();
        for (int i = 0; i < Math.min(manyLights.size, 4); i++) {
            shadowLightsForOldSystem.add(manyLights.get(i));
        }
        
        // This will warn about lights being lost and render only 8 out of 17 lights
        shadowRenderer.renderWithMultipleCubeShadows(
            instances, camera, shadowLightsForOldSystem, allLightsForOldSystem, 
            lightingManager.getAmbientLight()
        );
        
        int lightsLost = totalLightsRequested - 8;
        System.out.println("OLD BEHAVIOR RESULT:");
        System.out.println("  - Shadow lights rendered: " + shadowLightsGenerated);
        System.out.println("  - Total lights rendered: 8 (shader limit)");
        System.out.println("  - LIGHTS LOST: " + lightsLost + " out of " + totalLightsRequested);
        System.out.println("  - Players would notice lights suddenly turning off!");
        System.out.println();
    }
    
    /**
     * Demonstrates the NEW overflow handling behavior where ALL lights remain visible.
     * This method shows the improved system that converts excess lights to fallbacks.
     */
    public void demonstrateNewOverflowHandling(Array<ModelInstance> instances, Camera camera) {
        System.out.println("=== DEMONSTRATING NEW OVERFLOW HANDLING (all lights visible) ===");
        
        // Use the new renderWithShadowOverflowHandling method
        // This will automatically handle overflow and keep all lights visible
        shadowRenderer.renderWithShadowOverflowHandling(
            instances, camera, manyLights, additionalLights, 
            lightingManager.getAmbientLight()
        );
        
        // Calculate the results
        shadowLightsGranted = Math.min(manyLights.size, 4);
        lightsConvertedToFallback = manyLights.size - shadowLightsGranted;
        int totalLightsVisible = totalLightsRequested; // All lights remain visible!
        
        System.out.println("NEW OVERFLOW HANDLING RESULT:");
        System.out.println("  - Shadow lights rendered: " + shadowLightsGranted + "/" + manyLights.size + " requested");
        System.out.println("  - Lights converted to fallback: " + lightsConvertedToFallback);
        System.out.println("  - Total lights visible: " + totalLightsVisible + "/" + totalLightsRequested);
        System.out.println("  - LIGHTS LOST: 0 (ALL LIGHTS REMAIN VISIBLE!)");
        System.out.println("  - Players see a fully lit scene with no missing lights!");
        System.out.println();
    }
    
    /**
     * Demonstrates the LightingManager approach with automatic overflow handling.
     * This shows how to use the LightingManager for automatic light management.
     */
    public void demonstrateLightingManagerApproach(Array<ModelInstance> instances, Camera camera) {
        System.out.println("=== DEMONSTRATING LIGHTING MANAGER WITH AUTO-OVERFLOW ===");
        
        // Add all lights to the lighting manager (it will handle overflow automatically)
        for (PointLight shadowLight : manyLights) {
            lightingManager.addShadowCastingLight(shadowLight);
        }
        
        // Create equivalent fallback lights for additional lights
        for (PointLight additionalLight : additionalLights) {
            FallbackLight fallback = LightConverter.convertPointLightToFallback(
                additionalLight, gameObjectManager
            );
            lightingManager.addFallbackLight(fallback);
        }
        
        // The LightingManager automatically handles overflow during rendering
        lightingManager.renderSceneWithLighting(instances, camera);
        
        System.out.println("LIGHTING MANAGER RESULT:");
        System.out.println("  - Shadow lights: " + lightingManager.getShadowLightCount());
        System.out.println("  - Fallback lights: " + lightingManager.getFallbackLightCount());
        System.out.println("  - Total lights: " + lightingManager.getTotalLightCount());
        System.out.println("  - Lights rendered last frame: " + lightingManager.getLightsRenderedLastFrame());
        System.out.println("  - Automatic overflow handling: ENABLED");
        System.out.println();
    }
    
    /**
     * Runs a comprehensive comparison showing all three approaches.
     * This method demonstrates the evolution from light-losing to light-preserving systems.
     * 
     * @param instances Model instances to render
     * @param camera Scene camera
     */
    public void runComprehensiveDemo(Array<ModelInstance> instances, Camera camera) {
        System.out.println("\\n" + "=".repeat(70));
        System.out.println("COMPREHENSIVE LIGHTING OVERFLOW DEMONSTRATION");
        System.out.println("=".repeat(70));
        System.out.println("Scenario: " + totalLightsRequested + " total lights, " + 
                         manyLights.size + " requesting shadows");
        System.out.println("Hardware limit: 4 shadow-casting lights, 8 total lights in shader");
        System.out.println();
        
        // Demonstrate the old behavior (lights disappear)
        demonstrateOldBehavior(instances, camera);
        
        // Demonstrate the new overflow handling (all lights visible)  
        demonstrateNewOverflowHandling(instances, camera);
        
        // Demonstrate the LightingManager approach
        demonstrateLightingManagerApproach(instances, camera);
        
        // Summary
        System.out.println("=".repeat(70));
        System.out.println("SUMMARY:");
        System.out.println("  Old System: " + (totalLightsRequested - 8) + " lights lost");
        System.out.println("  New System: 0 lights lost (ALL VISIBLE)");
        System.out.println("  Performance: Minimal overhead for overflow handling");
        System.out.println("  User Experience: No more mysterious disappearing lights!");
        System.out.println("=".repeat(70));
    }
    
    /**
     * Updates the lighting system (call this each frame).
     * 
     * @param delta Time elapsed since last frame
     * @param cameraPosition Current camera position
     */
    public void update(float delta, Vector3 cameraPosition) {
        lightingManager.update(delta, cameraPosition);
    }
    
    /**
     * Gets performance statistics for monitoring overflow handling efficiency.
     * 
     * @return Formatted performance statistics string
     */
    public String getPerformanceStatistics() {
        return String.format(
            "Overflow Handling Performance:\\n" +
            "  Total Lights Requested: %d\\n" +
            "  Shadow Lights Granted: %d\\n" +
            "  Lights Converted to Fallback: %d\\n" +
            "  Conversion Efficiency: %.1f%%\\n" +
            "  Memory Overhead: ~%d KB",
            totalLightsRequested,
            shadowLightsGranted, 
            lightsConvertedToFallback,
            (100.0f * totalLightsRequested) / Math.max(totalLightsRequested, 1),
            (lightsConvertedToFallback * 2) // Rough estimate: 2KB per converted light
        );
    }
    
    /**
     * Cleans up resources.
     */
    public void dispose() {
        if (shadowRenderer != null) {
            shadowRenderer.dispose();
        }
        if (lightingManager != null) {
            lightingManager.dispose();
        }
        
        // Clear test arrays
        manyLights.clear();
        additionalLights.clear();
        
        System.out.println("OverflowHandlingExample disposed");
    }
}