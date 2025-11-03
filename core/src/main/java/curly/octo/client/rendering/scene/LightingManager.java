package curly.octo.client.rendering.scene;

import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * Manages light selection and culling for rendering.
 * Determines which lights should cast shadows based on distance and intensity.
 */
public class LightingManager {

    /**
     * Helper class for sorting lights by significance.
     */
    private static class LightSignificance {
        final PointLight light;
        final float significance;
        final float distance;
        final float intensity;

        LightSignificance(PointLight light, float significance, float distance, float intensity) {
            this.light = light;
            this.significance = significance;
            this.distance = distance;
            this.intensity = intensity;
        }
    }

    /**
     * Result of light selection containing shadow-casting and non-shadow-casting lights.
     */
    public static class LightSelectionResult {
        public final Array<PointLight> shadowCastingLights;
        public final Array<PointLight> nonShadowCastingLights;
        public final Array<PointLight> allLights;

        public LightSelectionResult(Array<PointLight> shadowCastingLights,
                                   Array<PointLight> nonShadowCastingLights,
                                   Array<PointLight> allLights) {
            this.shadowCastingLights = shadowCastingLights;
            this.nonShadowCastingLights = nonShadowCastingLights;
            this.allLights = allLights;
        }
    }

    /**
     * Selects the most significant lights for shadow casting based on distance and intensity.
     *
     * @param pointLights All available point lights
     * @param maxShadowLights Maximum number of shadow-casting lights
     * @param viewerPosition Position to calculate distance from (typically player/camera position)
     * @return Array of the most significant lights
     */
    public Array<PointLight> getMostSignificantLights(PointLightsAttribute pointLights,
                                                      int maxShadowLights,
                                                      Vector3 viewerPosition) {
        Array<PointLight> result = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return result;
        }

        // Create array of lights with significance scores (distance-based)
        Array<LightSignificance> lightScores = new Array<>();
        for (PointLight light : pointLights.lights) {
            // Score based on distance from viewer (closer lights are more significant)
            // Use negative distance so closer lights have higher significance when sorted
            float distance = viewerPosition.dst(light.position);
            float significance = -distance; // Negative so closer = higher significance
            lightScores.add(new LightSignificance(light, significance, distance, light.intensity));
        }

        // Sort by significance (highest first = closest first)
        // In case of tie distance, use intensity as tiebreaker
        lightScores.sort((a, b) -> {
            int distanceComparison = Float.compare(b.significance, a.significance);
            if (distanceComparison == 0) {
                // Tie in distance, use intensity as tiebreaker (brighter first)
                return Float.compare(b.intensity, a.intensity);
            }
            return distanceComparison;
        });

        // Take the N most significant lights
        int numLights = Math.min(maxShadowLights, lightScores.size);
        for (int i = 0; i < numLights; i++) {
            result.add(lightScores.get(i).light);
        }

        return result;
    }

    /**
     * Selects lights and separates them into shadow-casting and non-shadow-casting groups.
     *
     * @param pointLights All available point lights
     * @param maxShadowLights Maximum number of shadow-casting lights
     * @param viewerPosition Position to calculate distance from
     * @return LightSelectionResult containing categorized lights
     */
    public LightSelectionResult selectLights(PointLightsAttribute pointLights,
                                           int maxShadowLights,
                                           Vector3 viewerPosition) {
        Array<PointLight> allLights = new Array<>();
        Array<PointLight> shadowCastingLights = new Array<>();
        Array<PointLight> nonShadowCastingLights = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return new LightSelectionResult(shadowCastingLights, nonShadowCastingLights, allLights);
        }

        // Get all lights
        for (PointLight light : pointLights.lights) {
            allLights.add(light);
        }

        // Get most significant lights for shadow casting
        shadowCastingLights = getMostSignificantLights(pointLights, maxShadowLights, viewerPosition);

        // Remaining lights are non-shadow-casting
        for (PointLight light : allLights) {
            if (!shadowCastingLights.contains(light, true)) {
                nonShadowCastingLights.add(light);
            }
        }

        return new LightSelectionResult(shadowCastingLights, nonShadowCastingLights, allLights);
    }

    /**
     * Converts light array to position array for shader uniforms.
     *
     * @param lights Array of point lights
     * @return Float array of positions [x1, y1, z1, x2, y2, z2, ...]
     */
    public float[] getLightPositions(Array<PointLight> lights) {
        float[] positions = new float[lights.size * 3];
        for (int i = 0; i < lights.size; i++) {
            PointLight light = lights.get(i);
            positions[i * 3] = light.position.x;
            positions[i * 3 + 1] = light.position.y;
            positions[i * 3 + 2] = light.position.z;
        }
        return positions;
    }

    /**
     * Converts light array to color array for shader uniforms.
     *
     * @param lights Array of point lights
     * @return Float array of colors [r1, g1, b1, r2, g2, b2, ...]
     */
    public float[] getLightColors(Array<PointLight> lights) {
        float[] colors = new float[lights.size * 3];
        for (int i = 0; i < lights.size; i++) {
            PointLight light = lights.get(i);
            colors[i * 3] = light.color.r;
            colors[i * 3 + 1] = light.color.g;
            colors[i * 3 + 2] = light.color.b;
        }
        return colors;
    }

    /**
     * Converts light array to intensity array for shader uniforms.
     *
     * @param lights Array of point lights
     * @return Float array of intensities [i1, i2, i3, ...]
     */
    public float[] getLightIntensities(Array<PointLight> lights) {
        float[] intensities = new float[lights.size];
        for (int i = 0; i < lights.size; i++) {
            intensities[i] = lights.get(i).intensity;
        }
        return intensities;
    }
}
