package lights;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import curly.octo.game.GameObjectManager;
import curly.octo.rendering.HybridLightingRenderer;

/**
 * LightingSystemExample demonstrates how to use the complete lighting system
 * with both shadow-casting and fallback lights. This class provides practical
 * examples and best practices for implementing the hybrid lighting system
 * in your game.
 * 
 * Key Concepts Demonstrated:
 * - Creating and managing shadow-casting lights (expensive, limited)
 * - Creating and managing fallback lights (cheap, unlimited)
 * - Using LightingManager for unified light management
 * - Integrating with HybridLightingRenderer for optimal performance
 * - Performance monitoring and optimization techniques
 * 
 * Performance Guidelines:
 * - Use shadow-casting lights sparingly (maximum 8)
 * - Use fallback lights for ambient lighting and decorative effects
 * - Monitor performance statistics and adjust accordingly
 * - Consider distance-based culling for scenes with many lights
 */
public class LightingSystemExample {
    
    // Core lighting components
    private LightingManager lightingManager;
    private HybridLightingRenderer hybridRenderer;
    private GameObjectManager gameObjectManager;
    
    // Light collections for organized management
    private final Array<PointLight> shadowCastingLights;
    private final Array<FallbackLight> fallbackLights;
    
    // Performance monitoring
    private float performanceTimer = 0.0f;
    private static final float PERFORMANCE_LOG_INTERVAL = 5.0f; // Log every 5 seconds
    
    /**
     * Creates a new lighting system example.
     * 
     * @param gameObjectManager Reference to the game object manager
     */
    public LightingSystemExample(GameObjectManager gameObjectManager) {
        this.gameObjectManager = gameObjectManager;
        this.shadowCastingLights = new Array<>();
        this.fallbackLights = new Array<>();
        
        initializeLightingSystem();
        createExampleLights();
    }
    
    /**
     * Initializes the core lighting system components.
     */
    private void initializeLightingSystem() {
        // Create the hybrid renderer with high-quality shadows
        hybridRenderer = new HybridLightingRenderer(
            HybridLightingRenderer.QUALITY_HIGH,  // 1024x1024 shadow maps
            6                                     // Support up to 6 shadow-casting lights
        );
        
        // Create the lighting manager with the hybrid renderer
        lightingManager = new LightingManager(null); // We'll use HybridLightingRenderer directly
        
        // Set ambient lighting for a realistic underground cave environment
        lightingManager.setAmbientLight(0.1f, 0.1f, 0.15f); // Subtle blue-grey ambient
        
        System.out.println("Lighting system initialized:");
        System.out.println("  - Shadow quality: " + HybridLightingRenderer.QUALITY_HIGH + "x" + HybridLightingRenderer.QUALITY_HIGH);
        System.out.println("  - Max shadow lights: " + hybridRenderer.getMaxShadowLights());
        System.out.println("  - Max fallback lights: " + hybridRenderer.getMaxFallbackLights());
        System.out.println("  - Ambient light: (0.1, 0.1, 0.15)");
    }
    
    /**
     * Creates example lights to demonstrate the lighting system capabilities.
     * This method shows how to create different types of lights for various scenarios.
     */
    private void createExampleLights() {
        createShadowCastingLights();
        createFallbackLights();
        createDynamicLights();
        
        System.out.println("\\nExample lights created:");
        System.out.println("  - Shadow-casting lights: " + shadowCastingLights.size);
        System.out.println("  - Fallback lights: " + fallbackLights.size);
        System.out.println("  - Total lights: " + (shadowCastingLights.size + fallbackLights.size));
    }
    
    /**
     * Creates shadow-casting lights for primary illumination.
     * These lights are expensive but provide realistic shadows.
     */
    private void createShadowCastingLights() {
        // Primary light: Main torch or lamp providing strong illumination
        PointLight primaryLight = new PointLight();
        primaryLight.set(1.0f, 0.9f, 0.7f, new Vector3(10, 5, 10), 15.0f); // Warm white, high intensity
        shadowCastingLights.add(primaryLight);
        
        // Secondary light: Another important light source (perhaps sunlight through a window)
        PointLight secondaryLight = new PointLight();
        secondaryLight.set(0.9f, 0.95f, 1.0f, new Vector3(-8, 8, -5), 12.0f); // Cool white, medium intensity
        shadowCastingLights.add(secondaryLight);
        
        // Colored accent light: Magical or special effect light
        PointLight accentLight = new PointLight();
        accentLight.set(0.8f, 0.4f, 1.0f, new Vector3(0, 3, -10), 8.0f); // Purple magical light
        shadowCastingLights.add(accentLight);
        
        System.out.println("Created " + shadowCastingLights.size + " shadow-casting lights");
    }
    
    /**
     * Creates fallback lights for ambient lighting and decorative effects.
     * These lights are performant and can be used in large quantities.
     */
    private void createFallbackLights() {
        // Candle lights: Small, warm lights for atmosphere
        createCandleLights();
        
        // Torch lights: Medium-intensity lights attached to walls
        createTorchLights();
        
        // Magical effect lights: Colored lights for special effects
        createMagicalEffects();
        
        // Ambient lighting: Very subtle lights to fill dark areas
        createAmbientFill();
        
        System.out.println("Created " + fallbackLights.size + " fallback lights for atmosphere");
    }
    
    /**
     * Creates candle lights for intimate lighting.
     */
    private void createCandleLights() {
        // Create a pattern of candles with slight color variations and flickering
        float[] candleFlicker = {0.8f, 1.0f, 0.9f, 1.1f, 0.85f, 1.05f, 0.95f, 1.0f};
        
        for (int i = 0; i < 8; i++) {
            float x = (i % 4) * 3.0f - 4.5f;  // Spread candles along X axis
            float z = (i / 4) * 3.0f - 1.5f;  // Two rows of candles
            
            FallbackLight candle = new FallbackLight(
                gameObjectManager,
                "candle_" + i,
                1.0f, 0.8f, 0.6f,  // Warm orange color
                2.5f,               // Low intensity for intimate lighting
                null,               // No parent (world-positioned)
                candleFlicker       // Subtle flickering
            );
            candle.setPosition(new Vector3(x, 1.0f, z));
            fallbackLights.add(candle);
        }
    }
    
    /**
     * Creates torch lights attached to walls or posts.
     */
    private void createTorchLights() {
        // Wall torches with more intense flickering
        float[] torchFlicker = {0.7f, 1.2f, 0.8f, 1.3f, 0.6f, 1.1f, 0.9f, 1.4f, 0.75f, 1.0f};
        
        // Create torches around the perimeter
        Vector3[] torchPositions = {
            new Vector3(-15, 4, -10), new Vector3(15, 4, -10),  // Back wall
            new Vector3(-15, 4, 10),  new Vector3(15, 4, 10),   // Front wall
            new Vector3(-10, 4, 15),  new Vector3(10, 4, 15),   // Side walls
            new Vector3(-10, 4, -15), new Vector3(10, 4, -15)
        };
        
        for (int i = 0; i < torchPositions.length; i++) {
            FallbackLight torch = new FallbackLight(
                gameObjectManager,
                "torch_" + i,
                1.0f, 0.6f, 0.3f,  // Orange-red flame color
                5.0f,               // Medium intensity
                null,               // No parent
                torchFlicker        // Dramatic flickering
            );
            torch.setPosition(torchPositions[i]);
            fallbackLights.add(torch);
        }
    }
    
    /**
     * Creates magical effect lights for special visual effects.
     */
    private void createMagicalEffects() {
        // Floating magical orbs with color-shifting effects
        float[] magicFlicker = {1.0f, 0.9f, 1.1f, 0.8f, 1.2f, 0.95f, 1.05f, 0.85f};
        
        // Create magical lights with different colors
        Vector3[] colors = {
            new Vector3(0.6f, 0.8f, 1.0f),  // Ice blue
            new Vector3(1.0f, 0.4f, 0.8f),  // Pink/magenta
            new Vector3(0.4f, 1.0f, 0.6f),  // Green
            new Vector3(1.0f, 0.8f, 0.2f),  // Golden
            new Vector3(0.8f, 0.4f, 1.0f)   // Purple
        };
        
        for (int i = 0; i < colors.length; i++) {
            Vector3 color = colors[i];
            FallbackLight magicLight = new FallbackLight(
                gameObjectManager,
                "magic_orb_" + i,
                color.x, color.y, color.z,  // Varied magical colors
                3.5f,                       // Medium intensity
                null,                       // No parent
                magicFlicker               // Mystical flickering
            );
            
            // Position magical lights in a circle
            float angle = (float) (i * Math.PI * 2 / colors.length);
            float radius = 8.0f;
            magicLight.setPosition(new Vector3(
                (float) Math.cos(angle) * radius,
                2.5f + (float) Math.sin(i * 0.5f) * 0.5f, // Slight height variation
                (float) Math.sin(angle) * radius
            ));
            fallbackLights.add(magicLight);
        }
    }
    
    /**
     * Creates subtle ambient fill lights to eliminate pure darkness.
     */
    private void createAmbientFill() {
        // Very subtle fill lights to provide minimal illumination in dark areas
        Vector3[] fillPositions = {
            new Vector3(-20, 8, -20), new Vector3(20, 8, -20),
            new Vector3(-20, 8, 20),  new Vector3(20, 8, 20),
            new Vector3(0, 8, -20),   new Vector3(0, 8, 20),
            new Vector3(-20, 8, 0),   new Vector3(20, 8, 0)
        };
        
        for (int i = 0; i < fillPositions.length; i++) {
            FallbackLight fillLight = new FallbackLight(
                gameObjectManager,
                "fill_" + i,
                0.7f, 0.7f, 0.8f,  // Neutral grey-blue
                1.0f,               // Very low intensity
                null,               // No parent
                null                // No flickering (steady light)
            );
            fillLight.setPosition(fillPositions[i]);
            fallbackLights.add(fillLight);
        }
    }
    
    /**
     * Creates dynamic lights that can be moved or animated during gameplay.
     * This demonstrates how to create lights attached to game objects.
     */
    private void createDynamicLights() {
        // Example: Player's torch (attached to player)
        FallbackLight playerTorch = new FallbackLight(
            gameObjectManager,
            "player_torch",
            1.0f, 0.7f, 0.4f,   // Warm torch color
            4.0f,                // Medium intensity
            "player",            // Attach to player object
            new float[] {0.9f, 1.0f, 0.8f, 1.1f, 0.95f} // Gentle flickering
        );
        fallbackLights.add(playerTorch);
        
        // Example: Moving lantern (could be carried by NPCs)
        FallbackLight movingLantern = new FallbackLight(
            gameObjectManager,
            "npc_lantern",
            0.9f, 0.9f, 0.7f,   // Lantern color
            3.5f,                // Medium intensity  
            "guard_npc",         // Attach to NPC object
            null                 // Steady light
        );
        fallbackLights.add(movingLantern);
        
        System.out.println("Created dynamic lights attached to game objects");
    }
    
    /**
     * Updates the lighting system each frame.
     * This method should be called from your main game loop.
     * 
     * @param delta Time elapsed since last frame
     * @param cameraPosition Current camera position for optimizations
     */
    public void update(float delta, Vector3 cameraPosition) {
        // Update all fallback lights (handles parent tracking, flickering, etc.)
        for (FallbackLight light : fallbackLights) {
            light.update(delta);
        }
        
        // Update the lighting manager (handles performance optimizations)
        lightingManager.update(delta, cameraPosition);
        
        // Periodically log performance statistics
        performanceTimer += delta;
        if (performanceTimer >= PERFORMANCE_LOG_INTERVAL) {
            logPerformanceStatistics();
            performanceTimer = 0.0f;
        }
    }
    
    /**
     * Renders the scene with the complete lighting system.
     * This method demonstrates the recommended rendering approach.
     * 
     * @param instances Model instances to render
     * @param camera Scene camera
     */
    public void renderSceneWithLighting(Array<ModelInstance> instances, Camera camera) {
        // Step 1: Generate shadow maps for shadow-casting lights
        if (shadowCastingLights.size > 0) {
            hybridRenderer.generateAllShadowMaps(instances, shadowCastingLights);
        }
        
        // Step 2: Render the scene with hybrid lighting
        hybridRenderer.renderWithHybridLighting(
            instances,
            camera,
            shadowCastingLights,
            fallbackLights,
            lightingManager.getAmbientLight()
        );
    }
    
    /**
     * Adds a new fallback light at runtime.
     * This demonstrates dynamic light creation during gameplay.
     * 
     * @param position World position for the light
     * @param color Light color (RGB)
     * @param intensity Light intensity
     * @param flickerPattern Optional flicker pattern (null for steady light)
     * @param parentId Optional parent object ID (null for world-positioned)
     * @return The created FallbackLight, or null if creation failed
     */
    public FallbackLight createRuntimeLight(Vector3 position, Vector3 color, float intensity, 
                                          float[] flickerPattern, String parentId) {
        if (lightingManager.isTotalLightLimitReached()) {
            System.err.println("Cannot create runtime light - total light limit reached");
            return null;
        }
        
        String lightId = "runtime_light_" + System.currentTimeMillis();
        FallbackLight newLight = new FallbackLight(
            gameObjectManager,
            lightId,
            color.x, color.y, color.z,
            intensity,
            parentId,
            flickerPattern
        );
        
        if (parentId == null) {
            newLight.setPosition(position);
        }
        
        boolean added = lightingManager.addFallbackLight(newLight);
        if (added) {
            fallbackLights.add(newLight);
            System.out.println("Created runtime light '" + lightId + "' at " + position);
            return newLight;
        } else {
            newLight.destroy();
            return null;
        }
    }
    
    /**
     * Removes a fallback light at runtime.
     * 
     * @param light The FallbackLight to remove
     */
    public void removeRuntimeLight(FallbackLight light) {
        if (lightingManager.removeFallbackLight(light)) {
            fallbackLights.removeValue(light, true);
            light.destroy();
            System.out.println("Removed runtime light '" + light.entityId + "'");
        }
    }
    
    /**
     * Logs comprehensive performance statistics.
     * Use this method to monitor and optimize lighting performance.
     */
    private void logPerformanceStatistics() {
        System.out.println("\\n=== Lighting Performance Statistics ===");
        System.out.println("Lighting Manager: " + lightingManager.getPerformanceStats());
        System.out.println("\\nHybrid Renderer:");
        System.out.println("  Average Frame Time: " + String.format("%.2f", hybridRenderer.getAverageFrameTime()) + "ms");
        System.out.println("  Shadow Lights Rendered: " + hybridRenderer.getShadowLightsRendered());
        System.out.println("  Fallback Lights Rendered: " + hybridRenderer.getFallbackLightsRendered());
        System.out.println("  Total Draw Calls: " + hybridRenderer.getTotalDrawCalls());
        
        // Performance recommendations
        float frameTime = hybridRenderer.getAverageFrameTime();
        if (frameTime > 16.0f) { // More than 16ms (60fps threshold)
            System.out.println("\\nPERFORMANCE WARNING: Frame time exceeds 16ms");
            System.out.println("Recommendations:");
            System.out.println("  - Reduce number of shadow-casting lights");
            System.out.println("  - Lower shadow map quality");
            System.out.println("  - Enable distance culling for fallback lights");
            System.out.println("  - Reduce total number of lights in scene");
        } else if (frameTime < 8.0f) { // Less than 8ms (plenty of headroom)
            System.out.println("\\nPERFORMANCE GOOD: Plenty of headroom for more lights");
        }
        System.out.println("========================================\\n");
    }
    
    /**
     * Gets the lighting manager for external access.
     * 
     * @return The LightingManager instance
     */
    public LightingManager getLightingManager() {
        return lightingManager;
    }
    
    /**
     * Gets the hybrid renderer for external access.
     * 
     * @return The HybridLightingRenderer instance
     */
    public HybridLightingRenderer getHybridRenderer() {
        return hybridRenderer;
    }
    
    /**
     * Gets the list of shadow-casting lights.
     * 
     * @return Array of shadow-casting PointLights
     */
    public Array<PointLight> getShadowCastingLights() {
        return shadowCastingLights;
    }
    
    /**
     * Gets the list of fallback lights.
     * 
     * @return Array of FallbackLights
     */
    public Array<FallbackLight> getFallbackLights() {
        return fallbackLights;
    }
    
    /**
     * Disposes of all lighting system resources.
     * Call this method when the lighting system is no longer needed.
     */
    public void dispose() {
        // Dispose of lighting system components
        if (lightingManager != null) {
            lightingManager.dispose();
        }
        
        if (hybridRenderer != null) {
            hybridRenderer.dispose();
        }
        
        // Clear light arrays
        shadowCastingLights.clear();
        fallbackLights.clear();
        
        System.out.println("Lighting system disposed");
    }
}