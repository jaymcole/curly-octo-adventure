package curly.octo.common;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;

public class WorldObject extends GameObject implements Disposable, Possessable {

    private ModelInstance modelInstance;
    private btRigidBody rigidBody;
    private String modelAssetPath;
    private PhysicsProperties basePhysicsProperties;
    private float scaleFactor = 1.0f;
    private boolean disposed = false;

    // Possession state
    private boolean possessed = false;
    private float jumpCooldownTime = 0.0f;
    private static final float JUMP_COOLDOWN_DURATION = 1.0f; // 1 second cooldown
    private static final float JUMP_FORCE_MULTIPLIER = 10.0f;
    private Vector3 tempVector = new Vector3();

    // No-arg constructor for Kryo serialization
    public WorldObject() {
        super();
        this.basePhysicsProperties = PhysicsProperties.DEFAULT;
        this.scaleFactor = 1.0f;
        this.tempVector = new Vector3();
    }

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

        // Update jump cooldown
        if (jumpCooldownTime > 0) {
            jumpCooldownTime -= delta;
            if (jumpCooldownTime < 0) {
                jumpCooldownTime = 0;
            }
        }

        if (rigidBody != null && modelInstance != null) {
            rigidBody.getWorldTransform(modelInstance.transform);
        }
        // Non-physics objects should implement their own transform logic
    }

    public ModelInstance getModelInstance() {
        return modelInstance;
    }

    public void setModelInstance(ModelInstance modelInstance) {
        this.modelInstance = modelInstance;
        // Subclasses should handle their own transform logic after setting model instance
    }

    @Override
    public void setPosition(Vector3 newPosition) {
        super.setPosition(newPosition);
        // Subclasses should handle their own transform logic when position changes
    }


    public void updateModelPositionWithBounds(ModelAssetManager.ModelBounds bounds, float objectHeight, float scale, float yawDegrees) {
        if (modelInstance != null && position != null && bounds != null) {
            Vector3 modelPosition = bounds.getGroundCenteredPosition(position);
            modelPosition.y -= (objectHeight/2);
            // Reset transform and apply transformations in order
            modelInstance.transform.idt();
            modelInstance.transform.setToTranslation(modelPosition);
            modelInstance.transform.scl(scale);

            // Rotate only around Y axis (horizontal rotation)
//            if (yawDegrees != 0f) {
            modelInstance.transform.rotate(Vector3.Y, yawDegrees);
//            }
        } else {
            Log.error("WorldObject.updateModelPositionWithBounds", "Something is big broken with the model transform update");
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

    // Possessable interface implementation
    @Override
    public boolean canBePossessed() {
        return !disposed && rigidBody != null;
    }

    @Override
    public void onPossessionStart() {
        possessed = true;
    }

    @Override
    public void onPossessionEnd() {
        possessed = false;
    }

    // Movement interface implementation for WorldObject
    @Override
    public void move(Vector3 direction) {
        if (disposed || rigidBody == null) {
            return;
        }

        // Apply upward tilt to the direction (30-degree upward angle)
        tempVector.set(direction).nor();
        tempVector.y += 0.5f; // Add upward component
        tempVector.nor();

        // Scale force by object weight
        float forceScale = JUMP_FORCE_MULTIPLIER * getWeight();
        tempVector.scl(forceScale);

        // Apply impulse to rigid body
        rigidBody.applyCentralImpulse(tempVector);

        // Start cooldown
        jumpCooldownTime = JUMP_COOLDOWN_DURATION;
    }

    @Override
    public void jump() {
        if (disposed || rigidBody == null) {
            return;
        }

        // Jump straight up with full force
        tempVector.set(0, 1, 0);
        float forceScale = JUMP_FORCE_MULTIPLIER * getWeight();
        tempVector.scl(forceScale);

        // Apply impulse to rigid body
        rigidBody.applyCentralImpulse(tempVector);

        // Start cooldown
        jumpCooldownTime = JUMP_COOLDOWN_DURATION;
    }

    @Override
    public void stopMovement() {
        // WorldObjects don't have continuous movement to stop
        // Physics handles deceleration naturally
    }

    @Override
    public void rotateLook(float deltaYaw, float deltaPitch) {
        // WorldObjects don't have look rotation - they use third-person camera
        // This is handled in getCameraDirection/getCameraPosition
    }

    @Override
    public Vector3 getCameraPosition() {
        if (position == null) {
            return new Vector3(0, 5, 5);
        }

        // Position camera above and behind the object
        tempVector.set(position);
        tempVector.y += 3.0f * scaleFactor; // Height based on scale
        tempVector.z += 5.0f * scaleFactor; // Distance based on scale
        return tempVector.cpy();
    }

    @Override
    public Vector3 getCameraDirection() {
        if (position == null) {
            return new Vector3(0, -0.3f, -1).nor();
        }

        // Look down slightly toward the object
        tempVector.set(position);
        tempVector.sub(getCameraPosition()).nor();
        return tempVector.cpy();
    }

    @Override
    public boolean isPossessed() {
        return possessed;
    }
}
