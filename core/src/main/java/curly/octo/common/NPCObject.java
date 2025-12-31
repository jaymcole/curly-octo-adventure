package curly.octo.common;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.network.messages.NPCInstructionMessage;

import java.util.Random;

/**
 * Represents an NPC in the game world.
 * NPCs execute instructions distributed from the server and are synchronized across clients.
 */
public class NPCObject extends WorldObject {

    private static final float NPC_HEIGHT = 1.8f;
    private static final float NPC_WIDTH = 0.6f;
    private static final float NPC_DEPTH = 0.6f;

    // Current instruction being executed
    private transient NPCInstructionMessage currentInstruction;
    private transient long currentInstructionId = -1;
    private transient long instructionStartTime;
    private transient Random instructionRng;

    // Behavior state
    private transient Vector3 currentWaypoint;
    private transient Vector3 wanderCenter;
    private transient float wanderRadius;
    private transient float movementSpeed;

    // Network sync state
    private transient Vector3 lastSyncedPosition;
    private transient Vector3 targetPosition; // For interpolation
    private transient float interpolationAlpha;

    // Physics
    private transient btRigidBody physicsBody;
    private transient btCapsuleShape physicsShape;
    private transient boolean physicsInitialized = false;
    private Vector3 externalForce = new Vector3(0, 0, 0);  // For push mechanics

    // Rendering
    private transient ModelAssetManager.ModelBounds modelBounds;
    private transient boolean graphicsInitialized = false;

    // Orientation
    private float yaw = 0f; // Degrees

    /**
     * No-arg constructor for Kryo serialization.
     */
    public NPCObject() {
        super();
        initializeTransientFields();
    }

    /**
     * Constructor for creating an NPC with an ID.
     */
    public NPCObject(String npcId) {
        super(npcId);
        initializeTransientFields();
    }

    /**
     * Constructor with model path.
     */
    public NPCObject(String npcId, String modelAssetPath) {
        super(npcId, modelAssetPath);
        initializeTransientFields();
    }

    /**
     * Initialize transient fields that don't serialize.
     */
    private void initializeTransientFields() {
        this.currentWaypoint = new Vector3();
        this.wanderCenter = new Vector3();
        this.lastSyncedPosition = new Vector3();
        this.targetPosition = new Vector3();
        this.interpolationAlpha = 1.0f;
        this.movementSpeed = 2.0f; // Default speed
    }

    /**
     * Initialize graphics with placeholder cube model.
     */
    public void initializeGraphics(ModelAssetManager modelAssetManager) {
        if (graphicsInitialized) return;

        try {
            // Create a simple colored cube as placeholder
            ModelBuilder modelBuilder = new ModelBuilder();
            Material material = new Material(
                    ColorAttribute.createDiffuse(new Color(0.3f, 0.7f, 0.3f, 1.0f)) // Green NPC
            );

            Model cubeModel = modelBuilder.createBox(
                    NPC_WIDTH, NPC_HEIGHT, NPC_DEPTH,
                    material,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
            );

            ModelInstance modelInstance = new ModelInstance(cubeModel);
            setModelInstance(modelInstance);

            // Set initial position
            if (position != null) {
                modelInstance.transform.setToTranslation(position);
            }

            graphicsInitialized = true;
            Log.info("NPCObject", "Graphics initialized for NPC " + entityId);
        } catch (Exception e) {
            Log.error("NPCObject", "Failed to initialize graphics for NPC " + entityId + ": " + e.getMessage());
        }
    }

    /**
     * Initialize physics body for collision detection.
     * NPCs use kinematic rigid bodies (similar to remote players).
     */
    public void initializePhysics(com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld dynamicsWorld) {
        if (physicsInitialized || position == null) return;

        try {
            // Create capsule collision shape (radius, height) - same size as player
            float capsuleRadius = 1.0f;
            float capsuleHeight = 5.0f;
            physicsShape = new btCapsuleShape(capsuleRadius, capsuleHeight);

            // Create rigid body with zero mass (kinematic)
            btRigidBody.btRigidBodyConstructionInfo constructionInfo =
                    new btRigidBody.btRigidBodyConstructionInfo(0, null, physicsShape, Vector3.Zero);

            physicsBody = new btRigidBody(constructionInfo);
            physicsBody.setCollisionFlags(
                    physicsBody.getCollisionFlags() |
                            btRigidBody.CollisionFlags.CF_KINEMATIC_OBJECT
            );
            physicsBody.setActivationState(com.badlogic.gdx.physics.bullet.collision.Collision.DISABLE_DEACTIVATION);

            // Set initial position (capsule center is at height/2 + radius above ground)
            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            transform.setToTranslation(
                position.x,
                position.y + capsuleHeight / 2f + capsuleRadius,
                position.z
            );
            transform.rotate(Vector3.Y, yaw);
            physicsBody.setWorldTransform(transform);

            // Add to dynamics world with collision groups
            dynamicsWorld.addRigidBody(physicsBody,
                curly.octo.common.map.GameMap.NPC_GROUP,
                curly.octo.common.map.GameMap.GROUND_GROUP |
                curly.octo.common.map.GameMap.PLAYER_GROUP |
                curly.octo.common.map.GameMap.NPC_GROUP
            );

            constructionInfo.dispose();

            physicsInitialized = true;
            Log.info("NPCObject", "Physics initialized for NPC " + entityId + " with capsule collision (r=" +
                capsuleRadius + ", h=" + capsuleHeight + ")");
        } catch (Exception e) {
            Log.error("NPCObject", "Failed to initialize physics for NPC " + entityId + ": " + e.getMessage());
        }
    }

    /**
     * Apply external force to NPC (e.g., from player collision push).
     * @param force Force vector to apply
     */
    public void applyPushForce(Vector3 force) {
        if (externalForce == null) {
            externalForce = new Vector3();
        }
        externalForce.add(force);
    }

    /**
     * Execute a new instruction from the server.
     */
    public void executeInstruction(NPCInstructionMessage instruction) {
        this.currentInstruction = instruction;
        this.currentInstructionId = instruction.instructionId;
        this.instructionStartTime = System.currentTimeMillis();
        this.instructionRng = new Random(instruction.randomSeed);

        // Initialize instruction-specific state
        switch (instruction.type) {
            case IDLE:
                // Nothing to initialize for IDLE
                break;

            case WANDER:
                wanderCenter.set(
                        instruction.params.get("centerX"),
                        instruction.params.get("centerY"),
                        instruction.params.get("centerZ")
                );
                wanderRadius = instruction.params.get("radius");
                movementSpeed = instruction.params.get("speed");
                generateNewWaypoint();
                break;

            case PATROL:
            case CHASE:
            case CUSTOM_PATH:
                Log.warn("NPCObject", "Instruction type " + instruction.type + " not yet implemented");
                break;
        }

        Log.info("NPCObject", "NPC " + entityId + " executing instruction: " + instruction.type);
    }

    /**
     * Generate a new random waypoint for wandering.
     */
    private void generateNewWaypoint() {
        if (currentInstruction == null || instructionRng == null) return;

        // Generate random point within wander radius
        float angle = instructionRng.nextFloat() * (float) Math.PI * 2;
        float distance = instructionRng.nextFloat() * wanderRadius;

        currentWaypoint.set(
                wanderCenter.x + (float) Math.cos(angle) * distance,
                wanderCenter.y,
                wanderCenter.z + (float) Math.sin(angle) * distance
        );
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        if (currentInstruction == null) return;

        // Check if instruction has expired
        long elapsed = System.currentTimeMillis() - instructionStartTime;
        if (elapsed > currentInstruction.duration * 1000) {
            currentInstruction = null;
            return;
        }

        // Execute current instruction
        switch (currentInstruction.type) {
            case IDLE:
                // Do nothing
                break;

            case WANDER:
                updateWander(delta);
                break;

            case PATROL:
            case CHASE:
            case CUSTOM_PATH:
                // Not yet implemented
                break;
        }

        // Apply external forces (push mechanics) with damping
        if (externalForce != null && externalForce.len() > 0.01f) {
            position.add(externalForce.x * delta, externalForce.y * delta, externalForce.z * delta);
            externalForce.scl(0.95f);  // Damping - forces decay over time
        }

        // Update physics body position if initialized
        if (physicsBody != null && position != null) {
            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            // Capsule offset: height/2 + radius = 2.5 + 1.0 = 3.5
            transform.setToTranslation(position.x, position.y + 3.5f, position.z);
            transform.rotate(Vector3.Y, yaw);
            physicsBody.setWorldTransform(transform);
        }

        // Update model instance position
        if (getModelInstance() != null && position != null) {
            getModelInstance().transform.setToTranslation(position);
            getModelInstance().transform.rotate(Vector3.Y, yaw);
        }
    }

    /**
     * Update wander behavior.
     */
    private void updateWander(float delta) {
        if (position == null || currentWaypoint == null) return;

        // Check if we've reached the current waypoint
        float distanceToWaypoint = position.dst2(currentWaypoint);
        if (distanceToWaypoint < 0.5f) {
            generateNewWaypoint();
        }

        // Move toward waypoint
        Vector3 direction = new Vector3(currentWaypoint).sub(position);
        direction.y = 0; // Keep movement horizontal
        direction.nor();

        // Update yaw to face movement direction
        if (direction.len2() > 0.01f) {
            yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        }

        // Move toward waypoint
        position.add(direction.scl(movementSpeed * delta));
    }

    /**
     * Apply a sync correction from the elected client.
     */
    public void applySyncCorrection(Vector3 syncPosition, float syncYaw, long activeInstructionId) {
        if (position == null) {
            position = new Vector3();
        }

        // Check if we're executing the same instruction
        if (currentInstruction != null && currentInstruction.instructionId != activeInstructionId) {
            Log.warn("NPCObject", "NPC " + entityId + " instruction mismatch: " +
                    currentInstruction.instructionId + " vs " + activeInstructionId);
        }

        // Calculate error
        float error = position.dst(syncPosition);

        if (error > 1.0f) {
            // Large error: snap to correct position
            position.set(syncPosition);
            yaw = syncYaw;
            Log.info("NPCObject", "NPC " + entityId + " snapped to sync position (error: " + error + ")");
        } else if (error > 0.1f) {
            // Medium error: smooth interpolation
            position.lerp(syncPosition, 0.3f);
            yaw = yaw * 0.7f + syncYaw * 0.3f; // Lerp angle
        }
        // Small error < 0.1: ignore (within tolerance)

        lastSyncedPosition.set(syncPosition);
    }

    /**
     * Get the current yaw orientation.
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Set the yaw orientation.
     */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * Check if graphics are initialized.
     */
    public boolean isGraphicsInitialized() {
        return graphicsInitialized;
    }

    /**
     * Check if physics are initialized.
     */
    public boolean isPhysicsInitialized() {
        return physicsInitialized;
    }

    /**
     * Get the ID of the current instruction being executed.
     * Used for sync validation - ensures clients are executing the same instruction.
     */
    public long getCurrentInstructionId() {
        return currentInstructionId;
    }

    @Override
    public void dispose() {
        if (physicsBody != null) {
            physicsBody.dispose();
            physicsBody = null;
        }
        if (physicsShape != null) {
            physicsShape.dispose();
            physicsShape = null;
        }
        physicsInitialized = false;
        graphicsInitialized = false;
    }
}
