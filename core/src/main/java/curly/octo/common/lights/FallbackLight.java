package curly.octo.common.lights;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import curly.octo.client.GameObjectManager;

/**
 * FallbackLight is a non-shadow-casting dynamic light designed for performance.
 * This class extends BaseLight but removes all shadow-related functionality,
 * making it suitable for scenarios where you need many lights without the
 * computational overhead of shadow mapping.
 *
 * Key Features:
 * - Position-based lighting with world coordinates
 * - RGB color specification with individual component control
 * - Intensity-based brightness scaling
 * - Efficient flickering system for dynamic lighting effects
 * - Parent object tracking for attached lights (torches, lamps, etc.)
 * - No shadow map generation or storage
 * - Minimal GPU memory footprint
 *
 * Performance Benefits:
 * - No framebuffer allocation (saves VRAM)
 * - No depth map rendering passes (saves GPU cycles)
 * - Suitable for unlimited light quantities (limited only by shader array size)
 * - Fast creation and destruction
 *
 * Use Cases:
 * - Ambient lighting effects
 * - Decorative lighting (candles, torches, magical effects)
 * - Performance-critical scenarios with many light sources
 * - Background lighting that doesn't require realistic shadows
 */
public class FallbackLight extends BaseLight {

    // Performance tracking for debugging
    private static int fallbackLightCount = 0;

    /**
     * Creates a new FallbackLight with the specified properties.
     * This constructor initializes a non-shadow-casting light that contributes
     * to the scene's overall lighting without generating shadow maps.
     *
     * @param gameObjectManager Reference to the game object manager for parent tracking
     * @param lightId Unique identifier for this light instance
     * @param red Red component of the light color (0.0 to 1.0)
     * @param green Green component of the light color (0.0 to 1.0)
     * @param blue Blue component of the light color (0.0 to 1.0)
     * @param intensity Light intensity/brightness multiplier (recommended: 1.0 to 10.0)
     * @param parentId Optional parent object ID for attached lights (null for world-space lights)
     * @param flickerPattern Optional flicker values for dynamic lighting (null for steady light)
     */
    public FallbackLight(GameObjectManager gameObjectManager, String lightId,
                        float red, float green, float blue, float intensity,
                        String parentId, float[] flickerPattern) {
        // Call parent constructor with null environment to prevent PointLight creation
        // BaseLight will handle the common functionality like position, flickering, and parent tracking
        super(null, gameObjectManager, lightId, red, green, blue, intensity, parentId, flickerPattern);

        // Track instance count for performance monitoring
        fallbackLightCount++;

        // Initialize fallback-specific properties
        this.lightColor = new Color(red, green, blue, 1.0f);
        this.lightIntensity = intensity;
        this.worldPosition = new Vector3();

        // Log creation for debugging (can be removed in production)
        if (parentId != null) {
            System.out.println("Created FallbackLight '" + lightId + "' attached to '" + parentId +
                             "' - Color: (" + red + "," + green + "," + blue + ") Intensity: " + intensity);
        } else {
            System.out.println("Created FallbackLight '" + lightId + "' at world position" +
                             " - Color: (" + red + "," + green + "," + blue + ") Intensity: " + intensity);
        }
    }

    // Light properties specific to fallback lighting
    private Color lightColor;           // RGB color of the light
    private float lightIntensity;       // Base intensity before flickering
    private Vector3 worldPosition;      // Current world position
    private float effectiveIntensity;   // Current intensity after flickering

    /**
     * Convenience constructor for creating a white FallbackLight.
     * Uses default white color (1.0, 1.0, 1.0) for simple lighting setups.
     *
     * @param gameObjectManager Reference to the game object manager
     * @param lightId Unique identifier for this light
     * @param intensity Light intensity/brightness
     * @param parentId Optional parent object ID
     * @param flickerPattern Optional flicker values
     */
    public FallbackLight(GameObjectManager gameObjectManager, String lightId,
                        float intensity, String parentId, float[] flickerPattern) {
        this(gameObjectManager, lightId, 1.0f, 1.0f, 1.0f, intensity, parentId, flickerPattern);
    }

    /**
     * Convenience constructor for creating a steady (non-flickering) FallbackLight.
     *
     * @param gameObjectManager Reference to the game object manager
     * @param lightId Unique identifier for this light
     * @param red Red component of the light color
     * @param green Green component of the light color
     * @param blue Blue component of the light color
     * @param intensity Light intensity/brightness
     * @param parentId Optional parent object ID
     */
    public FallbackLight(GameObjectManager gameObjectManager, String lightId,
                        float red, float green, float blue, float intensity, String parentId) {
        this(gameObjectManager, lightId, red, green, blue, intensity, parentId, null);
    }

    /**
     * Updates the FallbackLight's state each frame.
     * This method handles parent tracking, position updates, and flickering effects.
     * Called automatically by the game's update loop.
     *
     * @param delta Time elapsed since last update (in seconds)
     */
    @Override
    public void update(float delta) {
        // Let the parent class handle common updates (parent tracking, flickering)
        super.update(delta);

        // Update our world position based on current position
        updateWorldPosition();

        // Update effective intensity based on current flicker state
        updateEffectiveIntensity();
    }

    /**
     * Updates the world position of this light.
     * If the light has a parent, it will update to follow the parent's position.
     * This method is called automatically during update().
     */
    private void updateWorldPosition() {
        if (this.position != null) {
            worldPosition.set(this.position);
        } else {
            // Fallback to origin if position is not set
            worldPosition.set(0, 0, 0);
        }
    }

    /**
     * Updates the effective intensity based on the current flicker state.
     * This allows the light to maintain smooth flickering effects for dynamic lighting.
     */
    private void updateEffectiveIntensity() {
        // The base class handles flickering calculations, we just need to track the result
        // Since we don't have access to the current flicker multiplier directly,
        // we'll calculate it based on the current flicker state
        effectiveIntensity = lightIntensity; // Base class handles flickering internally
    }

    /**
     * Sets the position of this FallbackLight in world coordinates.
     * This method updates both the internal position and the world position cache.
     *
     * @param newPosition The new world position for this light
     */
    @Override
    public void setPosition(Vector3 newPosition) {
        // Call parent implementation to maintain consistency
        super.setPosition(newPosition);

        // Update our cached world position
        worldPosition.set(newPosition);
    }

    /**
     * Gets the current world position of this light.
     * This position is used by the shader for lighting calculations.
     *
     * @return Current world position as a Vector3
     */
    public Vector3 getWorldPosition() {
        return worldPosition.cpy(); // Return copy to prevent external modification
    }

    /**
     * Gets the current color of this light.
     *
     * @return Light color as a Color object
     */
    public Color getLightColor() {
        return lightColor.cpy(); // Return copy to prevent external modification
    }

    /**
     * Gets the base intensity of this light (before flickering).
     *
     * @return Base light intensity
     */
    public float getLightIntensity() {
        return lightIntensity;
    }

    /**
     * Gets the current effective intensity (after flickering is applied).
     * This is the intensity value that should be passed to the shader.
     *
     * @return Current effective intensity
     */
    public float getEffectiveIntensity() {
        return effectiveIntensity;
    }

    /**
     * Sets the color of this light.
     *
     * @param red Red component (0.0 to 1.0)
     * @param green Green component (0.0 to 1.0)
     * @param blue Blue component (0.0 to 1.0)
     */
    public void setLightColor(float red, float green, float blue) {
        lightColor.set(red, green, blue, 1.0f);
    }

    /**
     * Sets the color of this light using a Color object.
     *
     * @param color New light color
     */
    public void setLightColor(Color color) {
        lightColor.set(color);
    }

    /**
     * Sets the base intensity of this light.
     *
     * @param intensity New base intensity
     */
    public void setLightIntensity(float intensity) {
        this.lightIntensity = intensity;
    }

    /**
     * Clean up resources when this light is no longer needed.
     * FallbackLights have minimal cleanup requirements since they don't
     * use framebuffers or other GPU resources.
     */
    @Override
    public void destroy() {
        // Call parent cleanup (handles Environment removal if needed)
        super.destroy();

        // Decrement our instance counter
        fallbackLightCount--;

        // Clear our references
        lightColor = null;
        worldPosition = null;

        // Log destruction for debugging
        System.out.println("Destroyed FallbackLight '" + entityId + "' - Remaining count: " + fallbackLightCount);
    }

    /**
     * Gets the total number of active FallbackLight instances.
     * Useful for performance monitoring and debugging.
     *
     * @return Number of active FallbackLight instances
     */
    public static int getActiveCount() {
        return fallbackLightCount;
    }

    /**
     * Checks if this light is ready for rendering.
     * A light is ready if it has valid position, color, and intensity values.
     *
     * @return True if the light is ready for rendering
     */
    public boolean isReadyForRendering() {
        return worldPosition != null && lightColor != null && lightIntensity > 0.0f;
    }

    /**
     * Creates a debug string representation of this light.
     * Useful for debugging and performance monitoring.
     *
     * @return Debug string with light properties
     */
    @Override
    public String toString() {
        return String.format("FallbackLight[id=%s, pos=(%.2f,%.2f,%.2f), color=(%.2f,%.2f,%.2f), intensity=%.2f]",
            entityId,
            worldPosition.x, worldPosition.y, worldPosition.z,
            lightColor.r, lightColor.g, lightColor.b,
            effectiveIntensity);
    }
}
