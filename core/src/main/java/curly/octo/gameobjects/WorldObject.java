package curly.octo.gameobjects;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;

public class WorldObject extends GameObject implements Disposable {

    private ModelInstance modelInstance;
    private btRigidBody rigidBody;
    private String modelAssetPath;
    private PhysicsProperties basePhysicsProperties;
    private float scaleFactor = 1.0f;
    private boolean disposed = false;

    public WorldObject(String id) {
        super(id);
    }

    public WorldObject(String id, String modelAssetPath) {
        super(id);
        this.modelAssetPath = modelAssetPath;
        this.basePhysicsProperties = PhysicsProperties.DEFAULT;
    }
    
    public WorldObject(String id, String modelAssetPath, PhysicsProperties basePhysicsProperties) {
        super(id);
        this.modelAssetPath = modelAssetPath;
        this.basePhysicsProperties = basePhysicsProperties;
    }
    
    public WorldObject(String id, String modelAssetPath, PhysicsProperties basePhysicsProperties, float scaleFactor) {
        super(id);
        this.modelAssetPath = modelAssetPath;
        this.basePhysicsProperties = basePhysicsProperties;
        this.scaleFactor = scaleFactor;
    }

    @Override
    public void update(float delta) {
        if (disposed) return;
        
        if (rigidBody != null && modelInstance != null) {
            rigidBody.getWorldTransform(modelInstance.transform);
        }
    }

    public ModelInstance getModelInstance() {
        return modelInstance;
    }

    public void setModelInstance(ModelInstance modelInstance) {
        this.modelInstance = modelInstance;
        if (modelInstance != null && position != null) {
            modelInstance.transform.setToTranslation(position);
        }
    }

    public btRigidBody getRigidBody() {
        return rigidBody;
    }

    public void setRigidBody(btRigidBody rigidBody) {
        this.rigidBody = rigidBody;
    }

    public String getModelAssetPath() {
        return modelAssetPath;
    }

    public void setModelAssetPath(String modelAssetPath) {
        this.modelAssetPath = modelAssetPath;
    }

    public PhysicsProperties getBasePhysicsProperties() {
        return basePhysicsProperties != null ? basePhysicsProperties : PhysicsProperties.DEFAULT;
    }

    public void setBasePhysicsProperties(PhysicsProperties basePhysicsProperties) {
        this.basePhysicsProperties = basePhysicsProperties;
    }
    
    public float getScaleFactor() {
        return scaleFactor;
    }
    
    public void setScaleFactor(float scaleFactor) {
        float oldScale = this.scaleFactor;
        this.scaleFactor = scaleFactor;
        
        // Update model transform if available
        if (modelInstance != null) {
            modelInstance.transform.scl(1.0f / oldScale); // Undo previous scale
            modelInstance.transform.scl(scaleFactor); // Apply new scale
        }
    }
    
    private float getScaledVolumeDisplacement() {
        return getBasePhysicsProperties().getVolumeDisplacement() * (scaleFactor * scaleFactor * scaleFactor);
    }
    
    private float getScaledWeight() {
        return getBasePhysicsProperties().getWeight() * (scaleFactor * scaleFactor * scaleFactor);
    }
    
    private float getScaledDensity() {
        return getBasePhysicsProperties().getDensity(); // Density doesn't change with scale
    }
    
    public float getVolumeDisplacement() {
        return getScaledVolumeDisplacement();
    }

    public float getWeight() {
        return getScaledWeight();
    }
    
    public float getDensity() {
        return getScaledDensity();
    }
    
    public boolean floats() {
        return getScaledDensity() < 1.0f; // Less dense than water
    }
    
    public float getBuoyantForce(float fluidDensity) {
        return getScaledVolumeDisplacement() * fluidDensity * 9.81f; // F = ρVg
    }
    
    public float getNetForce(float fluidDensity) {
        return getBuoyantForce(fluidDensity) - (getScaledWeight() * 9.81f);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        
        if (rigidBody != null) {
            rigidBody.dispose();
            rigidBody = null;
        }
        
        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
