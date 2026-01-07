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
    protected float movementSpeed = 10.0f;
    protected float jumpForce = 1.0f;
    protected float characterHeight;
    protected float characterWidth;

    protected transient ICharacterBrain brain;
    protected Vector3 velocity = new Vector3();
    protected Vector3 externalForce = new Vector3();
    private Vector3 initialPosition;

    public GameCharacter() {
        super();
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
        Log.info("GameCharacter", "[SPAWN_DEBUG] setInitialPosition called for " + entityId + " with position " + position);
        if (physicsInitialized) {
            Log.warn("GameCharacter", "[SPAWN_DEBUG] Physics already initialized. Setting position directly.");
            setPosition(position);
        } else {
            Log.info("GameCharacter", "[SPAWN_DEBUG] Storing initial position for later.");
            this.initialPosition = position;
        }
    }

    public void initializePhysics(btDiscreteDynamicsWorld dynamicsWorld, float height, float width) {
        if (physicsInitialized) {
            Log.warn("GameCharacter", "[SPAWN_DEBUG] initializePhysics called but already initialized for " + entityId);
            return;
        }
        if (position == null && initialPosition == null) {
            Log.error("GameCharacter", "[SPAWN_DEBUG] initializePhysics called with null position for " + entityId);
            return;
        }

        this.dynamicsWorld = dynamicsWorld;
        Log.info("GameCharacter", "[SPAWN_DEBUG] Initializing physics for " + entityId);

        try {
            float radius = width / 2f;
            float cylinderHeight = height - (2 * radius);
            if (cylinderHeight < 0) cylinderHeight = 0;

            physicsShape = new btCapsuleShape(radius, cylinderHeight);

            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            Vector3 pos = (initialPosition != null) ? initialPosition : position;
            Log.info("GameCharacter", "[SPAWN_DEBUG] Physics body for " + entityId + " will be created at: " + pos);
            transform.setToTranslation(pos.x, pos.y + height / 2f, pos.z);

            ghostObject = new btPairCachingGhostObject();
            ghostObject.setWorldTransform(transform);
            ghostObject.setCollisionShape(physicsShape);
            ghostObject.setCollisionFlags(
                ghostObject.getCollisionFlags() |
                btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT
            );
            ghostObject.setActivationState(4);

            characterController = new btKinematicCharacterController(ghostObject, physicsShape, 0.35f);
            characterController.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));
            characterController.setUp(new Vector3(0, 1, 0));
            characterController.setMaxSlope((float)Math.toRadians(Constants.PHYSICS_MAX_SLOPE_DEGREES));
            characterController.setJumpSpeed(jumpForce);
            characterController.setUseGhostSweepTest(false);

            dynamicsWorld.addCollisionObject(ghostObject,
                GameMap.PLAYER_GROUP,
                (short)(GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP | GameMap.NPC_GROUP)
            );
            dynamicsWorld.addAction(characterController);

            physicsInitialized = true;
            Log.info("GameCharacter", "[SPAWN_DEBUG] Physics initialized successfully for " + entityId);

            if (initialPosition != null) {
                Log.info("GameCharacter", "[SPAWN_DEBUG] Applying stored initial position " + initialPosition + " to " + entityId);
                setPosition(initialPosition);
                initialPosition = null;
            }

        } catch (Exception e) {
            Log.error("GameCharacter", "[SPAWN_DEBUG] Failed to initialize physics for " + entityId, e);
        }
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        if (brain != null) {
            brain.update(delta);
        }

        if (characterController != null) {
            Vector3 finalVelocity = new Vector3(velocity).scl(movementSpeed).add(externalForce);

            if (finalVelocity.len2() > 0.01f && System.currentTimeMillis() % 1000 < 50) {
                 Log.info("GameCharacter", "[MOVE_DEBUG] Applying velocity: " + finalVelocity + " to " + entityId + " (Hash: " + System.identityHashCode(this) + ")");
            }

            characterController.setWalkDirection(finalVelocity.scl(delta));
            externalForce.scl(0.95f);

            if (ghostObject != null) {
                Vector3 tempVector = new Vector3();
                ghostObject.getWorldTransform().getTranslation(tempVector);
                // Set our character's position to be the feet, not the center of the capsule
                position.set(tempVector.x, tempVector.y - (characterHeight / 2f), tempVector.z);

                com.badlogic.gdx.math.Matrix4 currentTransform = ghostObject.getWorldTransform();
                com.badlogic.gdx.math.Matrix4 uprightTransform = new com.badlogic.gdx.math.Matrix4();
                uprightTransform.setToTranslation(currentTransform.getTranslation(tempVector));
                ghostObject.setWorldTransform(uprightTransform);
            }
        }

        if (getModelInstance() != null && position != null) {
            getModelInstance().transform.setToTranslation(position);
            getModelInstance().transform.rotate(Vector3.Y, yaw);
        }

        if (System.currentTimeMillis() % 2000 < 100 && entityId.startsWith("player")) {
            Log.info("GameCharacter", "[SPAWN_DEBUG] Player " + entityId + " position: " + position + " (Hash: " + System.identityHashCode(this) + ")");
        }
    }

    public void setWalkDirection(Vector3 walkDirection) {
        this.velocity.set(walkDirection);
    }

    public void jump() {
        if (characterController != null && characterController.canJump()) {
            characterController.jump();
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
        Log.info("GameCharacter", "Force cleared physics for " + entityId);
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
