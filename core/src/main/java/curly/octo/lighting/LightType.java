package curly.octo.lighting;

/**
 * Enumeration of different light types supported by the lighting system
 */
public enum LightType {
    /**
     * Dynamic light that casts shadows in real-time.
     * Most expensive but highest quality lighting.
     */
    DYNAMIC_SHADOWED,
    
    /**
     * Dynamic light without shadows.
     * Cheaper than shadowed lights, good for effects like flickering torches.
     */
    DYNAMIC_UNSHADOWED,
    
    /**
     * Static light that has been baked into lightmaps.
     * Computed at map generation time, very cheap at runtime.
     */
    BAKED_STATIC
}