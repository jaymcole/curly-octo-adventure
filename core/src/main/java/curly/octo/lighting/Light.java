package curly.octo.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

/**
 * Represents a light in the lighting system with its properties and type.
 * This is a wrapper around LibGDX's PointLight with additional metadata.
 */
public class Light {
    private final LightType type;
    private final Vector3 position;
    private final Color color;
    private final float intensity;
    private final float range;
    private final boolean enabled;
    private final String id; // Unique identifier for the light
    
    // Baking-specific properties
    private final int bakingPriority; // Higher priority lights are baked first
    private final boolean castsShadows;
    
    // Dynamic properties
    private boolean isDirty; // Mark when light properties have changed
    
    public Light(Builder builder) {
        this.type = builder.type;
        this.position = new Vector3(builder.position);
        this.color = new Color(builder.color);
        this.intensity = builder.intensity;
        this.range = builder.range;
        this.enabled = builder.enabled;
        this.id = builder.id;
        this.bakingPriority = builder.bakingPriority;
        this.castsShadows = builder.castsShadows;
        this.isDirty = true;
    }
    
    public LightType getType() { return type; }
    public Vector3 getPosition() { return position; }
    public Color getColor() { return color; }
    public float getIntensity() { return intensity; }
    public float getRange() { return range; }
    public boolean isEnabled() { return enabled; }
    public String getId() { return id; }
    public int getBakingPriority() { return bakingPriority; }
    public boolean castsShadows() { return castsShadows; }
    public boolean isDirty() { return isDirty; }
    public void clearDirty() { this.isDirty = false; }
    
    /**
     * Update the position of this light (for dynamic lights)
     */
    public void setPosition(Vector3 newPosition) {
        this.position.set(newPosition);
        this.isDirty = true;
    }
    
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        this.isDirty = true;
    }
    
    /**
     * Calculate effective range based on intensity and attenuation
     */
    public float getEffectiveRange() {
        // Calculate distance where light contribution becomes negligible (< 1/256)
        return (float) Math.sqrt(intensity * 256.0f);
    }
    
    /**
     * Calculate light attenuation at a given distance
     */
    public float calculateAttenuation(float distance) {
        // Don't hard-cut at effective range - let the LightManager handle distance culling
        // This allows maxLightDistance to work properly
        return intensity / (1.0f + 0.05f * distance + 0.016f * distance * distance);
    }
    
    /**
     * Check if this light affects a given point significantly
     */
    public boolean affectsPoint(Vector3 point, float threshold) {
        float distance = position.dst(point);
        return calculateAttenuation(distance) > threshold;
    }
    
    public static class Builder {
        private LightType type = LightType.DYNAMIC_UNSHADOWED;
        private Vector3 position = new Vector3();
        private Color color = new Color(1, 1, 1, 1);
        private float intensity = 10.0f;
        private float range = 20.0f;
        private boolean enabled = true;
        private String id = "";
        private int bakingPriority = 0;
        private boolean castsShadows = false;
        
        public Builder setType(LightType type) {
            this.type = type;
            return this;
        }
        
        public Builder setPosition(float x, float y, float z) {
            this.position.set(x, y, z);
            return this;
        }
        
        public Builder setPosition(Vector3 position) {
            this.position.set(position);
            return this;
        }
        
        public Builder setColor(float r, float g, float b) {
            this.color.set(r, g, b, 1.0f);
            return this;
        }
        
        public Builder setColor(Color color) {
            this.color.set(color);
            return this;
        }
        
        public Builder setIntensity(float intensity) {
            this.intensity = intensity;
            return this;
        }
        
        public Builder setRange(float range) {
            this.range = range;
            return this;
        }
        
        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder setId(String id) {
            this.id = id;
            return this;
        }
        
        public Builder setBakingPriority(int priority) {
            this.bakingPriority = priority;
            return this;
        }
        
        public Builder setCastsShadows(boolean castsShadows) {
            this.castsShadows = castsShadows;
            return this;
        }
        
        public Light build() {
            return new Light(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Light[%s] type=%s pos=(%.1f,%.1f,%.1f) intensity=%.1f range=%.1f shadows=%s", 
            id, type, position.x, position.y, position.z, intensity, range, castsShadows);
    }
}