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
    protected float movementSpeed = 0.30f;
    protected float jumpForce = 0.30f;
    protected float characterHeight;
    protected float characterWidth;

    protected transient ICharacterBrain brain;
    protected Vector3 velocity = new Vector3();
    protected Vector3 externalForce = new Vector3();
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
            Vector3 finalVelocity = new Vector3(velocity).scl(movementSpeed).add(externalForce);
            characterController.setWalkDirection(finalVelocity);

            externalForce.scl(0.95f);

            if (ghostObject != null) {
                Vector3 tempVector = new Vector3();
                ghostObject.getWorldTransform().getTranslation(tempVector);
                position.set(tempVector.x, tempVector.y - (characterHeight / 2f), tempVector.z);

                if (Float.isNaN(position.x) || Float.isInfinite(position.x) ||
                    Float.isNaN(position.y) || Float.isInfinite(position.y) ||
                    Float.isNaN(position.z) || Float.isInfinite(position.z) ||
                    Math.abs(position.x) > 100000 || Math.abs(position.y) > 100000 || Math.abs(position.z) > 100000) {

                    position.set(lastPosition);

                    com.badlogic.gdx.math.Matrix4 resetTransform = new com.badlogic.gdx.math.Matrix4();
                    resetTransform.setToTranslation(position.x, position.y + characterHeight / 2f, position.z);
                    ghostObject.setWorldTransform(resetTransform);

                    velocity.setZero();
                    externalForce.setZero();
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
        }
    }

    public void setWalkDirection(Vector3 walkDirection) {
        this.velocity.set(walkDirection);
    }

    public void jump() {
        if (canJump()) {
            applyImpulse(new Vector3(0, jumpForce, 0));
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
