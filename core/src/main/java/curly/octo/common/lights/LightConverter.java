package curly.octo.common.lights;

import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.utils.Array;
import curly.octo.client.GameObjectManager;

/**
 * LightConverter provides utilities for converting between different light types
 * and managing light overflow scenarios. This class is essential for the hybrid
 * lighting system to handle cases where the number of requested shadow-casting
 * lights exceeds hardware limitations.
 *
 * Key Features:
 * - PointLight to FallbackLight conversion
 * - Automatic overflow handling for shadow light limits
 * - Maintains light properties during conversion
 * - Efficient batch conversion operations
 * - Memory management for converted lights
 *
 * Use Cases:
 * - Converting excess shadow lights to fallback lights
 * - Runtime light type switching for performance optimization
 * - Migrating lights between rendering systems
 * - Dynamic light quality adjustment based on performance
 */
public class LightConverter {

    private static int conversionCounter = 0; // For generating unique IDs

    /**
     * Converts a PointLight to a FallbackLight while preserving all properties.
     * This method creates a new FallbackLight that visually matches the original
     * PointLight but without shadow-casting capabilities.
     *
     * @param pointLight The PointLight to convert
     * @param gameObjectManager Reference to game object manager for parent tracking
     * @param lightId Custom ID for the new light (null to auto-generate)
     * @return New FallbackLight with equivalent properties
     */
    public static FallbackLight convertPointLightToFallback(PointLight pointLight,
                                                           GameObjectManager gameObjectManager,
                                                           String lightId) {
        if (pointLight == null) {
            return null;
        }

        // Generate unique ID if not provided
        if (lightId == null) {
            lightId = "converted_light_" + (conversionCounter++);
        }

        // Create FallbackLight with matching properties
        FallbackLight fallbackLight = new FallbackLight(
            gameObjectManager,
            lightId,
            pointLight.color.r,     // Red component
            pointLight.color.g,     // Green component
            pointLight.color.b,     // Blue component
            pointLight.intensity,   // Same intensity
            null,                   // No parent (world-positioned)
            null                    // No flickering (steady light like original)
        );

        // Set the same position as the original light
        fallbackLight.setPosition(pointLight.position.cpy());

        return fallbackLight;
    }

    /**
     * Converts a PointLight to a FallbackLight with auto-generated ID.
     * Convenience method for simple conversions.
     *
     * @param pointLight The PointLight to convert
     * @param gameObjectManager Reference to game object manager
     * @return New FallbackLight with equivalent properties
     */
    public static FallbackLight convertPointLightToFallback(PointLight pointLight,
                                                           GameObjectManager gameObjectManager) {
        return convertPointLightToFallback(pointLight, gameObjectManager, null);
    }

    /**
     * Handles shadow light overflow by converting excess lights to fallback lights.
     * This method takes an array of requested shadow lights and splits them into
     * actual shadow lights (limited quantity) and overflow fallback lights.
     *
     * @param requestedShadowLights All lights requested to cast shadows
     * @param maxShadowLights Maximum number of shadow-casting lights allowed
     * @param gameObjectManager Reference to game object manager
     * @param outShadowLights Output array for actual shadow lights (will be cleared first)
     * @param outOverflowFallbacks Output array for overflow fallback lights (will be cleared first)
     */
    public static void handleShadowLightOverflow(Array<PointLight> requestedShadowLights,
                                               int maxShadowLights,
                                               GameObjectManager gameObjectManager,
                                               Array<PointLight> outShadowLights,
                                               Array<FallbackLight> outOverflowFallbacks) {

        // Clear output arrays
        outShadowLights.clear();
        outOverflowFallbacks.clear();

        if (requestedShadowLights == null || requestedShadowLights.size == 0) {
            return;
        }

        // Add lights up to the shadow limit
        int shadowLightCount = Math.min(requestedShadowLights.size, maxShadowLights);
        for (int i = 0; i < shadowLightCount; i++) {
            outShadowLights.add(requestedShadowLights.get(i));
        }

        // Convert overflow lights to fallback lights
        if (requestedShadowLights.size > maxShadowLights) {
            int overflowCount = requestedShadowLights.size - maxShadowLights;
            System.out.println("Converting " + overflowCount + " excess shadow lights to fallback lights");

            for (int i = maxShadowLights; i < requestedShadowLights.size; i++) {
                PointLight overflowLight = requestedShadowLights.get(i);
                String fallbackId = "overflow_fallback_" + (i - maxShadowLights);

                FallbackLight fallbackLight = convertPointLightToFallback(
                    overflowLight, gameObjectManager, fallbackId
                );

                if (fallbackLight != null) {
                    outOverflowFallbacks.add(fallbackLight);
                }
            }
        }

        System.out.println("Shadow light overflow handling complete:");
        System.out.println("  - Shadow lights: " + outShadowLights.size + "/" + maxShadowLights);
        System.out.println("  - Overflow fallbacks: " + outOverflowFallbacks.size);
    }

    /**
     * Creates a hybrid light array that combines shadow lights, existing fallback lights,
     * and overflow fallback lights into a single array for shader processing.
     * This method ensures all lights are rendered regardless of shadow limitations.
     *
     * @param shadowLights Array of shadow-casting lights
     * @param existingFallbackLights Array of existing fallback lights
     * @param overflowFallbackLights Array of fallback lights created from overflow
     * @param maxTotalLights Maximum total lights supported by shader (now much higher)
     * @param outAllFallbackLights Output array containing all fallback lights for rendering
     * @param cameraPosition Current camera position for distance-based sorting
     */
    public static void createHybridLightArray(Array<PointLight> shadowLights,
                                            Array<FallbackLight> existingFallbackLights,
                                            Array<FallbackLight> overflowFallbackLights,
                                            int maxTotalLights,
                                            com.badlogic.gdx.math.Vector3 cameraPosition,
                                            Array<FallbackLight> outAllFallbackLights) {

        outAllFallbackLights.clear();

        // Create temporary combined array of all fallback lights
        Array<FallbackLight> allFallbackLights = new Array<>(
            existingFallbackLights.size + overflowFallbackLights.size);

        // Add all existing fallback lights
        for (FallbackLight existingLight : existingFallbackLights) {
            allFallbackLights.add(existingLight);
        }

        // Add all overflow fallback lights (converted from excess shadow lights)
        for (FallbackLight overflowLight : overflowFallbackLights) {
            allFallbackLights.add(overflowLight);
        }

        // Sort ALL fallback lights by distance/importance (only if camera position is provided)
        if (cameraPosition != null) {
            sortFallbackLightsByImportance(allFallbackLights, cameraPosition);
        }

        // Add the closest/brightest fallback lights up to the limit
        for (FallbackLight fallbackLight : allFallbackLights) {
            if (outAllFallbackLights.size >= maxTotalLights) break;
            outAllFallbackLights.add(fallbackLight);
        }

        int totalLightCount = shadowLights.size + outAllFallbackLights.size;

        // With the new higher limits, culling should be much rarer
        if (totalLightCount > maxTotalLights) {
            int culledCount = totalLightCount - maxTotalLights;
            System.out.println("Warning: " + culledCount + " lights culled due to shader array limit (" +
                             maxTotalLights + "). Consider distance culling or reducing light count.");
        }

        System.out.println("Hybrid light array created:");
        System.out.println("  - Shadow lights: " + shadowLights.size);
        System.out.println("  - Fallback lights: " + outAllFallbackLights.size + " (sorted by distance)");
        System.out.println("  - Total lights: " + totalLightCount + "/" + maxTotalLights + " limit");

        // Performance tip for very high light counts
        if (totalLightCount > 150) {
            System.out.println("  - Performance tip: With " + totalLightCount +
                             " lights, consider enabling distance culling for better performance");
        }
    }

    /**
     * Legacy version of createHybridLightArray without camera position sorting.
     * This method is deprecated and should not be used for new code.
     * It exists for backward compatibility only.
     *
     * @deprecated Use createHybridLightArray with cameraPosition parameter instead
     */
    @Deprecated
    public static void createHybridLightArray(Array<PointLight> shadowLights,
                                            Array<FallbackLight> existingFallbackLights,
                                            Array<FallbackLight> overflowFallbackLights,
                                            int maxTotalLights,
                                            Array<FallbackLight> outAllFallbackLights) {

        // Call the new version with null camera position (no sorting)
        createHybridLightArray(shadowLights, existingFallbackLights, overflowFallbackLights,
                             maxTotalLights, null, outAllFallbackLights);

        System.out.println("Warning: Using deprecated createHybridLightArray without camera position. " +
                         "Fallback lights will NOT be sorted by distance.");
    }

    /**
     * Sorts lights by importance for optimal shadow allocation.
     * This method can be used to prioritize which lights get shadows when
     * there are more shadow requests than available shadow slots.
     *
     * Priority factors:
     * - Light intensity (brighter lights get priority)
     * - Distance from camera (closer lights get priority)
     * - Manual priority values (if implemented in the future)
     *
     * @param lights Array of lights to sort (modified in-place)
     * @param cameraPosition Current camera position for distance calculations
     */
    public static void sortLightsByImportance(Array<PointLight> lights,
                                            com.badlogic.gdx.math.Vector3 cameraPosition) {
        if (lights == null || lights.size <= 1 || cameraPosition == null) {
            return;
        }

        // Sort by combined importance score (intensity / distance²)
        lights.sort((light1, light2) -> {
            float distance1 = light1.position.dst(cameraPosition);
            float distance2 = light2.position.dst(cameraPosition);

            // Calculate importance scores (intensity / distance²)
            // Add small epsilon to prevent division by zero
            float importance1 = light1.intensity / Math.max(distance1 * distance1, 0.01f);
            float importance2 = light2.intensity / Math.max(distance2 * distance2, 0.01f);

            // Sort in descending order (most important first)
            return Float.compare(importance2, importance1);
        });

        System.out.println("Sorted " + lights.size + " lights by importance (intensity/distance²)");
    }

    /**
     * Sorts FallbackLight arrays by importance for optimal fallback light allocation.
     * This method ensures the closest and brightest fallback lights are used first
     * when there are more fallback lights than available slots.
     *
     * @param fallbackLights Array of fallback lights to sort (modified in-place)
     * @param cameraPosition Current camera position for distance calculations
     */
    public static void sortFallbackLightsByImportance(Array<FallbackLight> fallbackLights,
                                                     com.badlogic.gdx.math.Vector3 cameraPosition) {
        if (fallbackLights == null || fallbackLights.size <= 1 || cameraPosition == null) {
            return;
        }

        // Sort by combined importance score (intensity / distance²)
        fallbackLights.sort((light1, light2) -> {
            float distance1 = light1.getWorldPosition().dst(cameraPosition);
            float distance2 = light2.getWorldPosition().dst(cameraPosition);

            // Calculate importance scores (intensity / distance²)
            // Add small epsilon to prevent division by zero
            float importance1 = light1.getEffectiveIntensity() / Math.max(distance1 * distance1, 0.01f);
            float importance2 = light2.getEffectiveIntensity() / Math.max(distance2 * distance2, 0.01f);

            // Sort in descending order (most important first)
            return Float.compare(importance2, importance1);
        });

        System.out.println("Sorted " + fallbackLights.size + " fallback lights by importance (intensity/distance²)");
    }

    /**
     * Cleans up temporary fallback lights created during overflow handling.
     * This method should be called when the overflow lights are no longer needed
     * to prevent memory leaks.
     *
     * @param overflowFallbackLights Array of temporary fallback lights to clean up
     */
    public static void cleanupOverflowFallbackLights(Array<FallbackLight> overflowFallbackLights) {
        if (overflowFallbackLights == null) {
            return;
        }

        int cleanedCount = 0;
        for (FallbackLight light : overflowFallbackLights) {
            if (light != null) {
                light.destroy();
                cleanedCount++;
            }
        }

        overflowFallbackLights.clear();

        if (cleanedCount > 0) {
            System.out.println("Cleaned up " + cleanedCount + " overflow fallback lights");
        }
    }

    /**
     * Gets statistics about light conversion operations.
     * Useful for debugging and performance monitoring.
     *
     * @return Formatted string with conversion statistics
     */
    public static String getConversionStatistics() {
        return String.format("LightConverter Statistics:\\n" +
                           "  - Total conversions performed: %d\\n" +
                           "  - Memory usage estimate: ~%d KB",
                           conversionCounter,
                           conversionCounter * 2); // Rough estimate: 2KB per converted light
    }

    /**
     * Resets the conversion counter.
     * Useful for testing or when starting a new level/scene.
     */
    public static void resetStatistics() {
        conversionCounter = 0;
        System.out.println("LightConverter statistics reset");
    }
}
