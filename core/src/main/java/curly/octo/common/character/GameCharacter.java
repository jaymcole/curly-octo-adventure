package curly.octo.common.character;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.WorldObject;
import curly.octo.common.map.GameMap;

public abstract class GameCharacter extends WorldObject {

    protected transient btKinematicCharacterController characterController;
    protected transient btPairCachingGhostObject ghostObject;
    protected transient btCapsuleShape physicsShape;
    protected transient btDiscreteDynamicsWorld dynamicsWorld;
    protected transient boolean physicsInitialized = false;

    protected float yaw = 0f;
    protected float pitch = 0f;

    protected transient ICharacterBrain brain;
    protected Vector3 velocity = new Vector3();
    protected Vector3 externalForce = new Vector3();

    public GameCharacter() {
        super();
    }

    public GameCharacter(String id) {
        super(id);
    }

    public GameCharacter(String id, String modelAssetPath) {
        super(id, modelAssetPath);
    }

    public void setBrain(ICharacterBrain brain) {
        this.brain = brain;
        if (this.brain != null) {
            this.brain.setCharacter(this);
        }
    }

    public void initializePhysics(btDiscreteDynamicsWorld dynamicsWorld, float height, float width) {
        if (physicsInitialized || position == null) return;

        this.dynamicsWorld = dynamicsWorld;

        try {
            // btCapsuleShape height is the height of the cylinder part only
            // Total height = cylinder_height + 2 * radius
            float radius = width / 2f;
            float cylinderHeight = height - (2 * radius);

            // Ensure cylinder height is at least 0 (sphere)
            if (cylinderHeight < 0) cylinderHeight = 0;

            physicsShape = new btCapsuleShape(radius, cylinderHeight);

            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            transform.setToTranslation(position.x, position.y + height / 2f, position.z);

            ghostObject = new btPairCachingGhostObject();
            ghostObject.setWorldTransform(transform);
            ghostObject.setCollisionShape(physicsShape);
            ghostObject.setCollisionFlags(
                ghostObject.getCollisionFlags() |
                btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT
            );
            // Use integer value 4 for DISABLE_DEACTIVATION
            ghostObject.setActivationState(4);

            // Use standard constructor and set up axis explicitly
            characterController = new btKinematicCharacterController(ghostObject, physicsShape, 0.35f);
            characterController.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));
            characterController.setUp(new Vector3(0, 1, 0)); // Explicitly set Y-axis up
            characterController.setMaxSlope((float)Math.toRadians(Constants.PHYSICS_MAX_SLOPE_DEGREES));
            characterController.setJumpSpeed(Constants.PLAYER_JUMP_FORCE);
            characterController.setUseGhostSweepTest(false);

            dynamicsWorld.addCollisionObject(ghostObject,
                GameMap.PLAYER_GROUP, // Using PLAYER_GROUP for all characters for now
                (short)(GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP | GameMap.NPC_GROUP)
            );
            dynamicsWorld.addAction(characterController);

            physicsInitialized = true;
        } catch (Exception e) {
            Log.error("GameCharacter", "Failed to initialize physics for " + entityId, e);
        }
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        if (brain != null) {
            brain.update(delta);
        }

        if (characterController != null) {
            // Combine velocity from brain/input with external forces
            Vector3 finalVelocity = new Vector3(velocity).add(externalForce);

            // Scale by delta time to get displacement for this frame
            characterController.setWalkDirection(finalVelocity.scl(delta));

            // Dampen external force over time
            externalForce.scl(0.95f);

            if (ghostObject != null) {
                Vector3 tempVector = new Vector3();
                position.set(ghostObject.getWorldTransform().getTranslation(tempVector));

                // Force the ghost object to remain upright (identity rotation)
                // This prevents the capsule from tipping over due to physics interactions
                com.badlogic.gdx.math.Matrix4 currentTransform = ghostObject.getWorldTransform();
                com.badlogic.gdx.math.Matrix4 uprightTransform = new com.badlogic.gdx.math.Matrix4();
                uprightTransform.setToTranslation(currentTransform.getTranslation(tempVector));
                // Do NOT rotate the physics body with yaw - keep it axis-aligned
                ghostObject.setWorldTransform(uprightTransform);
            }
        }

        if (getModelInstance() != null && position != null) {
            getModelInstance().transform.setToTranslation(position);
            getModelInstance().transform.rotate(Vector3.Y, yaw);
        }
    }

    public void setWalkDirection(Vector3 walkDirection) {
        this.velocity.set(walkDirection);
    }

    public void jump(Vector3 jumpForce) {
        if (characterController != null && characterController.canJump()) {
            characterController.jump(jumpForce);
        }
    }

    public boolean canJump() {
        return characterController != null && characterController.onGround();
    }

    public void applyImpulse(Vector3 impulse) {
        externalForce.add(impulse);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (characterController != null) {
            if (dynamicsWorld != null) {
                dynamicsWorld.removeAction(characterController);
            }
            characterController.dispose();
            characterController = null;
        }
        if (ghostObject != null) {
            if (dynamicsWorld != null) {
                dynamicsWorld.removeCollisionObject(ghostObject);
            }
            ghostObject.dispose();
            ghostObject = null;
        }
        if (physicsShape != null) {
            physicsShape.dispose();
            physicsShape = null;
        }
        physicsInitialized = false;
        dynamicsWorld = null;
    }
}
