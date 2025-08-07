package curly.octo.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.hints.LightHint;

/**
 * Example showing how to use the new lighting system effectively.
 * This demonstrates best practices for different lighting scenarios.
 */
public class LightingSystemExample {

    /**
     * Example of setting up a dungeon with mixed lighting types
     */
    public static void setupDungeonLighting(GameMap map) {
        Log.info("LightingExample", "Setting up dungeon lighting with mixed light types");

        // Add some baked static lights for main illumination
        // These will be computed at map generation time for best performance
        addStaticTorchLight(map, 10, 5, 10, Color.ORANGE, 8.0f, true); // High priority shadow-casting torch
        addStaticTorchLight(map, 30, 5, 30, Color.ORANGE, 8.0f, true); // Another main torch

        // Add some ambient static lighting (no shadows, just illumination)
        addStaticAmbientLight(map, 20, 10, 20, Color.CYAN, 4.0f, false); // Magical crystal glow
        addStaticAmbientLight(map, 40, 10, 40, Color.GREEN, 3.0f, false); // Moss glow

        // Reserve dynamic lights for moving effects and player interaction
        // These lights will be added at runtime, not during map generation

        Log.info("LightingExample", "Dungeon lighting setup complete");
    }

    /**
     * Example of outdoor lighting with sun and ambient
     */
    public static void setupOutdoorLighting(GameMap map) {
        Log.info("LightingExample", "Setting up outdoor lighting");

        // Add a few key area lights that cast shadows (like campfires)
        addStaticTorchLight(map, 15, 3, 15, Color.YELLOW, 12.0f, true); // Campfire
        addStaticTorchLight(map, 45, 3, 45, Color.YELLOW, 12.0f, true); // Another campfire

        // Add ambient lighting for overall illumination
        // In a real outdoor scene, you might have fewer lights and rely more on ambient
        addStaticAmbientLight(map, 25, 8, 25, Color.WHITE, 6.0f, false); // Moon glow area

        Log.info("LightingExample", "Outdoor lighting setup complete");
    }

    /**
     * Add a static torch light that will be baked into lightmaps
     */
    private static void addStaticTorchLight(GameMap map, int x, int y, int z, Color color,
                                          float intensity, boolean castsShadows) {
        MapTile tile = map.getTile(x, y, z);
        if (tile != null) {
            LightHint lightHint = new LightHint(
                LightType.BAKED_STATIC,
                intensity,
                color.r, color.g, color.b,
                castsShadows
            );
            lightHint.bakingPriority = castsShadows ? 10 : 5; // Shadow lights get higher priority
            lightHint.range = intensity * 2.5f; // Torch has good range

            tile.AddHint(lightHint);

            Log.debug("LightingExample", "Added static torch at (" + x + "," + y + "," + z + ") " +
                "intensity=" + intensity + " shadows=" + castsShadows);
        }
    }

    /**
     * Add a static ambient light for general illumination
     */
    private static void addStaticAmbientLight(GameMap map, int x, int y, int z, Color color,
                                            float intensity, boolean castsShadows) {
        MapTile tile = map.getTile(x, y, z);
        if (tile != null) {
            LightHint lightHint = new LightHint(
                LightType.BAKED_STATIC,
                intensity,
                color.r, color.g, color.b,
                castsShadows
            );
            lightHint.bakingPriority = 3; // Lower priority for ambient
            lightHint.range = intensity * 3.0f; // Ambient lights have wider range

            tile.AddHint(lightHint);

            Log.debug("LightingExample", "Added static ambient at (" + x + "," + y + "," + z + ") " +
                "intensity=" + intensity);
        }
    }

    /**
     * Example of how to add dynamic lights at runtime
     */
    public static void addPlayerLight(LightManager lightManager, Vector3 playerPosition) {
        // Add a small light that follows the player
        Light playerLight = new Light.Builder()
            .setId("player_light")
            .setType(LightType.DYNAMIC_UNSHADOWED) // Fast, no shadows for player light
            .setPosition(playerPosition)
            .setColor(0.8f, 0.8f, 1.0f) // Cool white
            .setIntensity(4.0f)
            .setEnabled(true)
            .build();

        lightManager.addLight(playerLight);
        Log.debug("LightingExample", "Added player light");
    }

    /**
     * Example of adding a dynamic torch with shadows (expensive but high quality)
     */
    public static void addDynamicTorch(LightManager lightManager, Vector3 position, String torchId) {
        Light torch = new Light.Builder()
            .setId(torchId)
            .setType(LightType.DYNAMIC_SHADOWED)
            .setPosition(position)
            .setColor(1.0f, 0.6f, 0.2f) // Warm torch color
            .setIntensity(10.0f)
            .setCastsShadows(true)
            .setEnabled(true)
            .build();

        lightManager.addLight(torch);
        Log.debug("LightingExample", "Added dynamic torch: " + torchId);
    }

    /**
     * Example of adding flickering effect lights (no shadows, animated)
     */
    public static void addFlickeringLight(LightManager lightManager, Vector3 position, String lightId) {
        Light flickeringLight = new Light.Builder()
            .setId(lightId)
            .setType(LightType.DYNAMIC_UNSHADOWED) // No shadows for performance
            .setPosition(position)
            .setColor(1.0f, 0.4f, 0.1f) // Fire color
            .setIntensity(6.0f) // Will be animated/varied in game logic
            .setEnabled(true)
            .build();

        lightManager.addLight(flickeringLight);
        Log.debug("LightingExample", "Added flickering light: " + lightId);
    }

    /**
     * Performance optimization recommendations
     */
    public static void configureForPerformance(LightManager lightManager) {
        Log.info("LightingExample", "Configuring lighting for optimal performance");

        // Conservative settings for good performance
        lightManager.setMaxShadowedLights(2); // Only 2 shadow-casting lights at once
        lightManager.setMaxUnshadowedLights(8); // More unshadowed lights for effects
        lightManager.setLightCullingThreshold(0.02f); // Cull dim lights aggressively
        lightManager.setMaxLightDistance(40.0f); // Don't process very distant lights

        Log.info("LightingExample", "Performance optimization applied");
    }

    /**
     * Quality optimization for high-end systems
     */
    public static void configureForQuality(LightManager lightManager) {
        Log.info("LightingExample", "Configuring lighting for high quality");

        // Higher quality settings
        lightManager.setMaxShadowedLights(4); // More shadow-casting lights
        lightManager.setMaxUnshadowedLights(16); // More total lights
        lightManager.setLightCullingThreshold(0.005f); // Keep more subtle lighting
        lightManager.setMaxLightDistance(60.0f); // Process more distant lights

        Log.info("LightingExample", "Quality optimization applied");
    }

    /**
     * Example of lighting configuration for different scenarios
     */
    public static void demonstrateUsage() {
        Log.info("LightingExample", "=== Lighting System Usage Examples ===");

        // Create a light manager
        LightManager lightManager = new LightManager();

        // Configure for your target performance
        configureForPerformance(lightManager); // or configureForQuality(lightManager)

        // Add various light types
        addPlayerLight(lightManager, new Vector3(10, 5, 10));
        addDynamicTorch(lightManager, new Vector3(20, 5, 20), "torch_1");
        addFlickeringLight(lightManager, new Vector3(30, 5, 30), "candle_1");

        // In your render loop, you would call:
        // Vector3 playerPos = getPlayerPosition();
        // Array<PointLight> shadowLights = lightManager.getShadowCastingLights(playerPos);
        // Array<PointLight> allLights = lightManager.getAllDynamicLights(playerPos);

        Log.info("LightingExample", "Usage demonstration complete");

        // Print statistics
        lightManager.printStatistics();
    }
}
