package curly.octo.map.hints;

import curly.octo.lighting.LightType;

public class LightHint extends MapHint{
    public float intensity;
    public float color_r;
    public float color_g;
    public float color_b;
    
    // New lighting system properties
    public LightType lightType = LightType.DYNAMIC_UNSHADOWED;
    public boolean castsShadows = false;
    public int bakingPriority = 0;
    public float range = 20.0f;
    
    // For backward compatibility
    public LightHint() {
        super();
    }
    
    // New constructor for enhanced lighting
    public LightHint(LightType type, float intensity, float r, float g, float b, boolean shadows) {
        this.lightType = type;
        this.intensity = intensity;
        this.color_r = r;
        this.color_g = g;
        this.color_b = b;
        this.castsShadows = shadows;
        this.range = intensity * 2.0f; // Reasonable default range
    }
}
