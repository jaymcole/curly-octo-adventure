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
    protected float movementSpeed = Constants.PLAYER_MOVEMENT_SPEED;  // 10.0f
    protected float jumpForce = Constants.PLAYER_JUMP_FORCE;  // 25f
    protected float characterHeight;
    protected float characterWidth;

    protected transient ICharacterBrain brain;
    protected Vector3 velocity = new Vector3();  // Horizontal (X/Z) movement direction from brain
    protected Vector3 externalForce = new Vector3();  // Horizontal (X/Z) forces from collisions/pushes
    protected float verticalVelocity = 0f;  // Vertical velocity (managed separately from Bullet)
    private Vector3 initialPosition;
    private Vector3 lastPosition = new Vector3();

    public GameCharacter() {
        super();
        this.characterHeight = Constants.PLAYER_HEIGHT;
        this.characterWidth = Constants.PLAYER_WIDTH;
    }

    public GameCharacter(String id, float height, float width) {
        super(id);
        this.characterHeight = height;
        this.characterWidth = width;
    }

    public GameCharacter(String id, String modelAssetPath, float height, float width) {
        super(id, modelAssetPath);
        this.characterHeight = height;
        this.characterWidth = width;
    }

    public void setBrain(ICharacterBrain brain) {
        this.brain = brain;
        if (this.brain != null) {
            this.brain.setCharacter(this);
        }
    }

    public ICharacterBrain getBrain() {
        return brain;
    }

    public void setInitialPosition(Vector3 position) {
        if (physicsInitialized) {
            setPosition(position);
        } else {
            this.initialPosition = position;
        }
    }

    public void initializePhysics(btDiscreteDynamicsWorld dynamicsWorld, float height, float width) {
        if (physicsInitialized || (position == null && initialPosition == null)) return;

        this.dynamicsWorld = dynamicsWorld;

        try {
            float radius = width / 2f;
            float cylinderHeight = height - (2 * radius);

            if (cylinderHeight < 0) cylinderHeight = 0;
            if (radius <= 0.001f) return;

            physicsShape = new btCapsuleShape(radius, cylinderHeight);

            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            Vector3 pos = (initialPosition != null) ? initialPosition : position;

            if (pos == null || Float.isNaN(pos.x) || Float.isNaN(pos.y) || Float.isNaN(pos.z)) {
                 pos = new Vector3(0, 10, 0);
            }

            transform.setToTranslation(pos.x, pos.y + height / 2f, pos.z);

            ghostObject = new btPairCachingGhostObject();
            ghostObject.setWorldTransform(transform);
            ghostObject.setCollisionShape(physicsShape);
            ghostObject.setCollisionFlags(
                ghostObject.getCollisionFlags() |
                btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT
            );
            ghostObject.setActivationState(4);

            characterController = new btKinematicCharacterController(ghostObject, physicsShape, 0.35f, new Vector3(0, 1, 0));
            characterController.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));
            characterController.setMaxSlope((float)Math.toRadians(Constants.PHYSICS_MAX_SLOPE_DEGREES));
            characterController.setJumpSpeed(jumpForce);
            characterController.setMaxJumpHeight(4f);
            characterController.setFallSpeed(55f);
            characterController.setUseGhostSweepTest(false);

            dynamicsWorld.addCollisionObject(ghostObject,
                GameMap.PLAYER_GROUP,
                (short)(GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP | GameMap.NPC_GROUP)
            );
            dynamicsWorld.addAction(characterController);

            physicsInitialized = true;

            if (initialPosition != null) {
                setPosition(initialPosition);
                initialPosition = null;
            }

            if (position != null) {
                lastPosition.set(position);
            }

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
            // Calculate horizontal movement with proper deltaTime scaling
            Vector3 finalVelocity = new Vector3(velocity).scl(movementSpeed * delta);

            // Add horizontal external forces (from collisions, pushes, etc.)
            finalVelocity.add(externalForce.x, 0, externalForce.z);

            // Apply gravity to vertical velocity
            verticalVelocity += Constants.PHYSICS_GRAVITY * delta;

            // Set Y component to our managed vertical velocity
            finalVelocity.y = verticalVelocity * delta;

            // Reset vertical velocity when on ground
            if (characterController.onGround() && verticalVelocity < 0) {
                verticalVelocity = 0;
                finalVelocity.y = 0;
            }

            // Validate before passing to Bullet to prevent physics corruption
            if (Float.isNaN(finalVelocity.x) || Float.isNaN(finalVelocity.z) || Float.isNaN(finalVelocity.y) ||
                Float.isInfinite(finalVelocity.x) || Float.isInfinite(finalVelocity.z) || Float.isInfinite(finalVelocity.y) ||
                finalVelocity.len() > 100f) {  // Sanity check: no movement faster than 100 units/frame
                Log.warn("GameCharacter", "[PHYSICS] Invalid walk direction rejected: " + finalVelocity +
                         " velocity=" + velocity + " verticalVel=" + verticalVelocity + " delta=" + delta);
                finalVelocity.set(0, 0, 0);
                verticalVelocity = 0;
            }

            characterController.setWalkDirection(finalVelocity);

            // Frame-rate independent exponential decay - much faster (95% loss per second)
            float decayFactor = (float) Math.pow(0.05f, delta);
            externalForce.scl(decayFactor);

            if (ghostObject != null) {
                Vector3 tempVector = new Vector3();
                ghostObject.getWorldTransform().getTranslation(tempVector);
                position.set(tempVector.x, tempVector.y - (characterHeight / 2f), tempVector.z);

                if (Float.isNaN(position.x) || Float.isInfinite(position.x) ||
                    Float.isNaN(position.y) || Float.isInfinite(position.y) ||
                    Float.isNaN(position.z) || Float.isInfinite(position.z) ||
                    Math.abs(position.x) > 100000 || Math.abs(position.y) > 100000 || Math.abs(position.z) > 100000) {

                    Log.error("GameCharacter", "[NaN_RECOVERY] Invalid position for " + entityId +
                             " pos=" + position + " lastPos=" + lastPosition +
                             " velocity=" + velocity + " externalForce=" + externalForce);

                    position.set(lastPosition);

                    com.badlogic.gdx.math.Matrix4 resetTransform = new com.badlogic.gdx.math.Matrix4();
                    resetTransform.setToTranslation(position.x, position.y + characterHeight / 2f, position.z);
                    ghostObject.setWorldTransform(resetTransform);

                    velocity.setZero();
                    externalForce.setZero();
                    verticalVelocity = 0;
                } else {
                    lastPosition.set(position);
                }

                com.badlogic.gdx.math.Matrix4 currentTransform = ghostObject.getWorldTransform();
                com.badlogic.gdx.math.Matrix4 uprightTransform = new com.badlogic.gdx.math.Matrix4();
                uprightTransform.setToTranslation(currentTransform.getTranslation(tempVector));
                ghostObject.setWorldTransform(uprightTransform);
            }
        }

        if (getModelInstance() != null && position != null) {
            getModelInstance().transform.setToTranslation(position);
            getModelInstance().transform.rotate(Vector3.Y, yaw);

            // Apply model scale to match character dimensions
            float scale = getModelScale();
            if (scale != 1.0f) {
                getModelInstance().transform.scale(scale, scale, scale);
            }
        }
    }

    public void setWalkDirection(Vector3 walkDirection) {
        // Defensive validation: reject NaN and Infinity values
        if (walkDirection == null ||
            Float.isNaN(walkDirection.x) || Float.isInfinite(walkDirection.x) ||
            Float.isNaN(walkDirection.y) || Float.isInfinite(walkDirection.y) ||
            Float.isNaN(walkDirection.z) || Float.isInfinite(walkDirection.z)) {

            Log.warn("GameCharacter", "Rejected invalid walk direction for " + entityId +
                     ": " + walkDirection + ", resetting to zero");
            this.velocity.setZero();
            return;
        }

        // Only store horizontal (X/Z) velocity - Y is managed by character controller
        this.velocity.set(walkDirection.x, 0, walkDirection.z);
    }

    public void jump() {
        if (canJump() && characterController != null) {
            Log.info("GameCharacter", "[JUMP] Initiating jump for " + entityId +
                     " pos=" + position + " onGround=" + characterController.onGround());
            // Set vertical velocity directly instead of using characterController.jump()
            // This avoids conflicts with setWalkDirection() calls
            verticalVelocity = jumpForce;
        } else {
            Log.warn("GameCharacter", "[JUMP] BLOCKED for " + entityId +
                    " canJump=" + canJump() +
                    " controller=" + (characterController != null) +
                    " onGround=" + (characterController != null ? characterController.onGround() : "N/A"));
        }
    }

    public boolean canJump() {
        return characterController != null && characterController.onGround();
    }

    public void applyImpulse(Vector3 impulse) {
        // Only apply horizontal forces - Y-axis is managed by character controller
        externalForce.add(impulse.x, 0, impulse.z);
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
        this.pitch = Math.max(-89f, Math.min(89f, pitch));
    }

    @Override
    public Vector3 getCameraPosition() {
        if (position != null) {
            return new Vector3(position).add(0, characterHeight * 0.9f, 0); // Camera at 90% of height
        }
        return new Vector3(0, characterHeight * 0.9f, 0);
    }

    @Override
    public Vector3 getCameraDirection() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        return new Vector3(
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) -Math.sin(pitchRad),
            (float) (Math.cos(pitchRad) * Math.cos(yawRad))
        ).nor();
    }

    public btPairCachingGhostObject getGhostObject() {
        return ghostObject;
    }

    public boolean isPhysicsInitialized() {
        return physicsInitialized;
    }

    public float getCharacterHeight() {
        return characterHeight;
    }

    public float getCharacterWidth() {
        return characterWidth;
    }

    /**
     * Get the scale factor for this character's model.
     * Override in subclasses to provide character-specific scaling.
     * @return Scale factor (1.0 = no scaling)
     */
    protected float getModelScale() {
        return 1.0f; // Default: no scaling
    }

    public btKinematicCharacterController getCharacterController() {
        return characterController;
    }

    /**
     * Forcefully clears physics references without attempting to remove them from the world.
     * Use this ONLY when the dynamics world itself is being disposed or is already invalid.
     */
    public void forceClearPhysics() {
        if (characterController != null) {
            characterController.dispose();
            characterController = null;
        }
        if (ghostObject != null) {
            ghostObject.dispose();
            ghostObject = null;
        }
        if (physicsShape != null) {
            physicsShape.dispose();
            physicsShape = null;
        }
        dynamicsWorld = null;
        physicsInitialized = false;
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
