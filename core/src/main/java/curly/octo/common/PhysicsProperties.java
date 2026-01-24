package curly.octo.common;

public class PhysicsProperties {

    public static final PhysicsProperties DEFAULT = new PhysicsProperties(1.0f, 1.0f);

    private float volumeDisplacement;
    private float weight;
    private float density;
    private boolean floats;

    public PhysicsProperties() {
        this.volumeDisplacement = 1.0f;
        this.weight = 1.0f;
        this.density = 1.0f;
        this.floats = false;
    }

    public PhysicsProperties(float volumeDisplacement, float weight) {
        this.volumeDisplacement = volumeDisplacement;
        this.weight = weight;
        this.density = weight / volumeDisplacement;
        this.floats = density < 1.0f; // Less dense than water
    }

    public PhysicsProperties(float volumeDisplacement, float weight, boolean floats) {
        this.volumeDisplacement = volumeDisplacement;
        this.weight = weight;
        this.density = weight / volumeDisplacement;
        this.floats = floats;
    }

    public float getVolumeDisplacement() {
        return volumeDisplacement;
    }

    public float getWeight() {
        return weight;
    }

    public float getDensity() {
        return density;
    }

    public float getBuoyantForce(float fluidDensity) {
        return volumeDisplacement * fluidDensity * 9.81f; // F = ρVg
    }

    public float getNetForce(float fluidDensity) {
        return getBuoyantForce(fluidDensity) - (weight * 9.81f);
    }

    @Override
    public String toString() {
        return String.format("PhysicsProperties{volume=%.2f, weight=%.2f, density=%.2f, floats=%s}",
                           volumeDisplacement, weight, density, floats);
    }
}
