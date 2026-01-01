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
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
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

    // Behavior state (waypoint queue navigation - server-provided paths)
    private transient java.util.List<Vector3> waypointQueue;    // Queue of waypoints to navigate
    private transient int currentWaypointIndex;                  // Index in queue
    private transient Vector3 targetWaypoint;                    // Current target from queue
    private transient float movementSpeed;

    // Legacy fields (kept for backward compatibility but deprecated)
    @Deprecated
    private transient Vector3 currentWaypoint;
    @Deprecated
    private transient Vector3 wanderCenter;
    @Deprecated
    private transient float wanderRadius;

    // Network sync state
    private transient Vector3 lastSyncedPosition;
    private transient Vector3 targetPosition; // For interpolation
    private transient float interpolationAlpha;

    // Physics - using character controller like players for proper physics-based movement
    private transient com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject ghostObject;
    private transient com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController characterController;
    private transient btCapsuleShape physicsShape;
    private transient com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld dynamicsWorld;
    private transient boolean physicsInitialized = false;
    private Vector3 externalForce = new Vector3(0, 0, 0);  // For push mechanics

    // Rendering
    private transient ModelAssetManager.ModelBounds modelBounds;
    private transient boolean graphicsInitialized = false;

    // Orientation
    private float yaw = 0f; // Degrees

    // Navigation (legacy - kept for old methods that haven't been removed yet)
    @Deprecated
    private transient curly.octo.common.map.GameMap gameMap; // For legacy waypoint generation methods
    private transient Vector3 lastPosition; // Track position for stuck detection
    private transient float stuckTimer = 0f; // Accumulator for stuck time
    private static final float STUCK_THRESHOLD = 3.0f; // Seconds before considering stuck
    private static final float STUCK_DISTANCE = 0.1f; // Units - minimum movement to not be stuck
    private static final int MAX_WAYPOINT_ATTEMPTS = 10; // Max tries to find valid waypoint

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
        // Waypoint queue navigation (new system)
        this.waypointQueue = new java.util.ArrayList<>();
        this.currentWaypointIndex = 0;
        this.targetWaypoint = new Vector3();

        // Legacy fields (deprecated)
        this.currentWaypoint = new Vector3();
        this.wanderCenter = new Vector3();

        // Network sync
        this.lastSyncedPosition = new Vector3();
        this.targetPosition = new Vector3();
        this.lastPosition = new Vector3();
        this.interpolationAlpha = 1.0f;

        // Movement
        this.movementSpeed = 0.3f; // Default speed - slow walking pace
        this.stuckTimer = 0f;
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
     * Initialize physics using character controller for proper physics-based movement.
     * NPCs use the same character controller as players for consistent movement behavior.
     */
    public void initializePhysics(btDiscreteDynamicsWorld dynamicsWorld) {
        if (physicsInitialized || position == null) return;

        // Store dynamics world reference for raycasting
        this.dynamicsWorld = dynamicsWorld;

        try {
            // Create capsule collision shape (radius, height) - same size as player
            float capsuleRadius = 1.0f;
            float capsuleHeight = 5.0f;
            physicsShape = new btCapsuleShape(capsuleRadius, capsuleHeight);

            // Position capsule so its bottom sits on the ground
            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
            transform.setToTranslation(
                position.x,
                position.y + capsuleHeight / 2f + capsuleRadius,
                position.z
            );

            // Create ghost object for character controller
            ghostObject = new com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject();
            ghostObject.setWorldTransform(transform);
            ghostObject.setCollisionShape(physicsShape);
            ghostObject.setCollisionFlags(
                ghostObject.getCollisionFlags() |
                com.badlogic.gdx.physics.bullet.collision.btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT
            );

            // Create character controller with same settings as player
            characterController = new com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController(
                ghostObject, physicsShape, 1.0f
            );
            characterController.setGravity(new Vector3(0, curly.octo.common.Constants.PHYSICS_GRAVITY, 0));
            characterController.setMaxSlope((float)Math.toRadians(curly.octo.common.Constants.PHYSICS_MAX_SLOPE_DEGREES));
            characterController.setJumpSpeed(0f); // NPCs don't jump
            characterController.setUseGhostSweepTest(false);

            // Add to dynamics world with collision groups
            dynamicsWorld.addCollisionObject(ghostObject,
                curly.octo.common.map.GameMap.NPC_GROUP,
                curly.octo.common.map.GameMap.GROUND_GROUP |
                curly.octo.common.map.GameMap.PLAYER_GROUP |
                curly.octo.common.map.GameMap.NPC_GROUP
            );
            dynamicsWorld.addAction(characterController);

            physicsInitialized = true;
            Log.info("NPCObject", "Physics initialized for NPC " + entityId + " with character controller (r=" +
                capsuleRadius + ", h=" + capsuleHeight + ")");
        } catch (Exception e) {
            Log.error("NPCObject", "Failed to initialize physics for NPC " + entityId + ": " + e.getMessage());
            e.printStackTrace();
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
                // Extract waypoint list from instruction (new tile index system)
                waypointQueue.clear();
                currentWaypointIndex = 0;

                int[] waypointTileIndices = instruction.waypointTileIndices;

                // DIAGNOSTIC LOGGING
                Log.info("NPCObject", "DIAGNOSTIC - NPC " + entityId + " processing WANDER instruction");
                Log.info("NPCObject", "  waypointTileIndices: " + (waypointTileIndices != null ?
                         "array[" + waypointTileIndices.length + "]" : "NULL"));

                if (waypointTileIndices != null && waypointTileIndices.length > 0) {
                    Log.info("NPCObject", "  Parsing " + (waypointTileIndices.length / 3) + " tile waypoints...");

                    // Parse tile indices and convert to world positions: [x1,y1,z1, x2,y2,z2, ...]
                    for (int i = 0; i < waypointTileIndices.length; i += 3) {
                        if (i + 2 < waypointTileIndices.length) {  // Ensure we have x,y,z
                            int tileX = waypointTileIndices[i];
                            int tileY = waypointTileIndices[i + 1];
                            int tileZ = waypointTileIndices[i + 2];

                            // Convert tile indices to world coordinates (centered in tile)
                            float worldX = tileX * curly.octo.common.Constants.MAP_TILE_SIZE + curly.octo.common.Constants.MAP_TILE_SIZE / 2f;
                            float worldY = tileY * curly.octo.common.Constants.MAP_TILE_SIZE + curly.octo.common.Constants.MAP_TILE_SIZE / 2f;
                            float worldZ = tileZ * curly.octo.common.Constants.MAP_TILE_SIZE + curly.octo.common.Constants.MAP_TILE_SIZE / 2f;

                            Vector3 waypoint = new Vector3(worldX, worldY, worldZ);
                            waypointQueue.add(waypoint);

                            // DIAGNOSTIC: Log every 10th waypoint
                            if (i % 30 == 0) {
                                Log.info("NPCObject", "  Tile [" + tileX + "," + tileY + "," + tileZ + "] → World " +
                                         String.format("(%.1f, %.1f, %.1f)", worldX, worldY, worldZ));
                            }
                        }
                    }

                    if (!waypointQueue.isEmpty()) {
                        targetWaypoint.set(waypointQueue.get(0));
                        movementSpeed = instruction.params.getOrDefault("speed", 0.1f);

                        Log.info("NPCObject", "✓ NPC " + entityId + " loaded path with " +
                                 waypointQueue.size() + " waypoints (speed: " + movementSpeed + ")");

                        int[] firstTile = new int[]{waypointTileIndices[0], waypointTileIndices[1], waypointTileIndices[2]};
                        int lastIdx = waypointTileIndices.length - 3;
                        int[] lastTile = new int[]{waypointTileIndices[lastIdx], waypointTileIndices[lastIdx+1], waypointTileIndices[lastIdx+2]};

                        Log.info("NPCObject", "  First waypoint: Tile [" + firstTile[0] + "," + firstTile[1] + "," + firstTile[2] + "] = " +
                                 String.format("(%.1f, %.1f, %.1f)", targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
                        Log.info("NPCObject", "  Last waypoint: Tile [" + lastTile[0] + "," + lastTile[1] + "," + lastTile[2] + "] = " +
                                 String.format("(%.1f, %.1f, %.1f)",
                                     waypointQueue.get(waypointQueue.size()-1).x,
                                     waypointQueue.get(waypointQueue.size()-1).y,
                                     waypointQueue.get(waypointQueue.size()-1).z));
                    } else {
                        Log.error("NPCObject", "✗ NPC " + entityId + " parsed 0 waypoints from tile indices!");
                    }
                } else {
                    Log.error("NPCObject", "✗ NPC " + entityId + " received WANDER with NO TILE WAYPOINT DATA!");
                }
                break;

            case PATROL:
            case CHASE:
            case CUSTOM_PATH:
                Log.warn("NPCObject", "Instruction type " + instruction.type + " not yet implemented");
                break;
        }

        Log.info("NPCObject", "NPC " + entityId + " executing instruction: " + instruction.type);
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        // Initialize movement velocity
        Vector3 walkVelocity = new Vector3(0, 0, 0);

        if (currentInstruction != null) {
            // Check if instruction has expired
            long elapsed = System.currentTimeMillis() - instructionStartTime;
            if (elapsed > currentInstruction.duration * 1000) {
                currentInstruction = null;
            } else {
                // Execute current instruction to calculate movement
                switch (currentInstruction.type) {
                    case IDLE:
                        // No movement
                        break;

                    case WANDER:
                        walkVelocity = calculateWanderVelocity(delta);
                        break;

                    case PATROL:
                    case CHASE:
                    case CUSTOM_PATH:
                        // Not yet implemented
                        break;
                }
            }
        }

        // Add external forces (push mechanics) to velocity
        if (externalForce != null && externalForce.len() > 0.01f) {
            walkVelocity.add(externalForce);
            externalForce.scl(0.95f);  // Damping - forces decay over time
        }

        // Apply velocity using character controller (physics-based movement)
        if (characterController != null) {
            characterController.setWalkDirection(walkVelocity);

            // Sync position from physics (character controller updates the ghost object)
            if (position != null && ghostObject != null) {
                Vector3 tempVector = new Vector3();
                position.set(ghostObject.getWorldTransform().getTranslation(tempVector));

                // Maintain upright orientation
                com.badlogic.gdx.math.Matrix4 currentTransform = ghostObject.getWorldTransform();
                com.badlogic.gdx.math.Matrix4 uprightTransform = new com.badlogic.gdx.math.Matrix4();
                uprightTransform.setToTranslation(currentTransform.getTranslation(tempVector));
                uprightTransform.rotate(Vector3.Y, yaw);
                ghostObject.setWorldTransform(uprightTransform);
            }
        }

        // Stuck detection and recovery
        updateStuckDetection(delta);

        // Update model instance position
        if (getModelInstance() != null && position != null) {
            getModelInstance().transform.setToTranslation(position);
            getModelInstance().transform.rotate(Vector3.Y, yaw);
        }
    }

    /**
     * Calculate wander velocity for physics-based movement.
     * Navigates through waypoint queue provided by server.
     *
     * @param delta Time delta
     * @return Velocity vector for character controller
     */
    private Vector3 calculateWanderVelocity(float delta) {
        if (position == null || targetWaypoint == null || waypointQueue.isEmpty()) {
            // DIAGNOSTIC
            if (waypointQueue.isEmpty()) {
                Log.warn("NPCObject", "calculateWanderVelocity: waypointQueue is EMPTY! NPC will not move.");
            }
            return new Vector3(0, 0, 0);  // No path to follow
        }

        // Check if reached current target waypoint (2D distance on XZ plane, ignore Y)
        float dx = position.x - targetWaypoint.x;
        float dz = position.z - targetWaypoint.z;
        float distanceToWaypoint = (float) Math.sqrt(dx * dx + dz * dz);

        // DIAGNOSTIC: Log distance every few frames
        if (Math.random() < 0.01) {
            Log.info("NPCObject", "NPC " + entityId + " distance to waypoint " + currentWaypointIndex +
                     ": " + String.format("%.2f", distanceToWaypoint) + " units (threshold: 0.5)");
            Log.info("NPCObject", "  NPC pos: " + String.format("(%.1f, %.1f, %.1f)", position.x, position.y, position.z));
            Log.info("NPCObject", "  Target: " + String.format("(%.1f, %.1f, %.1f)", targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
        }

        if (distanceToWaypoint < 0.5f) {
            // Advance to next waypoint in queue
            currentWaypointIndex++;

            if (currentWaypointIndex < waypointQueue.size()) {
                // More waypoints to go
                targetWaypoint.set(waypointQueue.get(currentWaypointIndex));

                Log.info("NPCObject", "NPC " + entityId + " reached waypoint " +
                         currentWaypointIndex + "/" + waypointQueue.size() +
                         " - next target: " + String.format("(%.1f, %.1f, %.1f)",
                             targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
            } else {
                // Reached end of path - STOP
                Log.info("NPCObject", "NPC " + entityId + " COMPLETED full path (" +
                         waypointQueue.size() + " waypoints)");
                return new Vector3(0, 0, 0);  // Stop moving
            }
        }

        // Calculate direction toward current target waypoint
        Vector3 direction = new Vector3(targetWaypoint).sub(position);
        direction.y = 0;  // Keep movement horizontal (no flying)

        if (direction.len2() > 0.01f) {
            direction.nor();

            // Update yaw to face movement direction
            yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));

            // Calculate desired velocity and apply wall sliding
            Vector3 finalVelocity = direction.scl(movementSpeed);
            finalVelocity = applyWallSliding(finalVelocity);
            return finalVelocity;
        }

        return new Vector3(0, 0, 0);
    }

    /**
     * Apply a sync correction from the elected client.
     * Updates the physics ghost object position instead of directly modifying position field.
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

        // Only apply sync corrections for large errors to avoid fighting physics simulation
        if (error > 2.0f) {
            // Large error: snap ghost object to correct position
            if (ghostObject != null) {
                // Update ghost object transform (capsule center is offset from ground position)
                com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
                transform.setToTranslation(
                    syncPosition.x,
                    syncPosition.y + 3.5f,  // Capsule offset: height/2 + radius
                    syncPosition.z
                );
                transform.rotate(Vector3.Y, syncYaw);
                ghostObject.setWorldTransform(transform);

                // Update local position to match
                position.set(syncPosition);
                yaw = syncYaw;

                Log.info("NPCObject", "NPC " + entityId + " snapped to sync position (error: " + error + ")");
            }
        }
        // Smaller errors are ignored - let physics handle smooth movement

        lastSyncedPosition.set(syncPosition);
    }

    /**
     * Update stuck detection and trigger recovery if needed.
     */
    private void updateStuckDetection(float delta) {
        if (position == null || lastPosition == null || waypointQueue.isEmpty()) {
            return;
        }

        float distanceMoved = position.dst(lastPosition);

        // Are we trying to move but not making progress?
        boolean tryingToMove = (currentInstruction != null &&
                               currentInstruction.type == NPCInstructionMessage.InstructionType.WANDER &&
                               currentWaypointIndex < waypointQueue.size());

        if (tryingToMove && distanceMoved < STUCK_DISTANCE) {
            stuckTimer += delta;

            if (stuckTimer >= 1.5f) {  // Reduced from 3.0 to 1.5 seconds
                Log.warn("NPCObject", "NPC " + entityId + " stuck - attempting recovery");
                handleStuckRecovery();
                stuckTimer = 0f;
            }
        } else {
            stuckTimer = 0f;
        }

        // Store current position for next frame comparison
        if (lastPosition != null && position != null) {
            lastPosition.set(position);
        }
    }

    /**
     * Handle stuck recovery by trying different strategies.
     */
    private void handleStuckRecovery() {
        // Strategy 1: Skip to next waypoint if available
        if (currentWaypointIndex + 1 < waypointQueue.size()) {
            Log.info("NPCObject", "Skipping problematic waypoint " + currentWaypointIndex);
            currentWaypointIndex++;
            targetWaypoint.set(waypointQueue.get(currentWaypointIndex));
            return;
        }

        // Strategy 2: Go back to previous waypoint and try again
        if (currentWaypointIndex > 0) {
            Log.info("NPCObject", "Backing up to previous waypoint");
            currentWaypointIndex--;
            targetWaypoint.set(waypointQueue.get(currentWaypointIndex));
            return;
        }

        // Strategy 3: Clear path - server will generate new path when it detects completion
        Log.info("NPCObject", "Clearing failed path - awaiting new instruction");
        waypointQueue.clear();
        currentWaypointIndex = 0;
    }

    /**
     * Apply wall sliding behavior to prevent getting stuck on obstacles.
     * Uses raycasting to detect walls ahead and slides along them.
     *
     * @param desiredVelocity The velocity the NPC wants to move at
     * @return The adjusted velocity that slides along walls
     */
    private Vector3 applyWallSliding(Vector3 desiredVelocity) {
        if (characterController == null || dynamicsWorld == null || desiredVelocity.len2() < 0.01f) {
            return desiredVelocity;
        }

        // Raycast ahead in movement direction (at mid-capsule height)
        Vector3 rayStart = new Vector3(position);
        rayStart.y += 3.0f;  // Mid-height of capsule

        Vector3 rayDirection = new Vector3(desiredVelocity).nor();
        Vector3 rayEnd = new Vector3(rayStart).add(rayDirection.scl(1.5f));  // Look 1.5 units ahead

        com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback rayCallback =
            new com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback(rayStart, rayEnd);

        dynamicsWorld.rayTest(rayStart, rayEnd, rayCallback);

        if (rayCallback.hasHit()) {
            // Wall detected ahead - calculate slide direction
            Vector3 hitNormal = new Vector3();
            rayCallback.getHitNormalWorld(hitNormal);

            // Project velocity onto plane perpendicular to wall normal
            Vector3 slideVelocity = new Vector3(desiredVelocity);
            float dotProduct = slideVelocity.dot(hitNormal);

            if (dotProduct < 0) {  // Moving toward wall
                // Remove velocity component toward wall, keeping parallel component
                slideVelocity.sub(new Vector3(hitNormal).scl(dotProduct));
                rayCallback.dispose();
                return slideVelocity;
            }
        }

        rayCallback.dispose();
        return desiredVelocity;
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

    /**
     * Get the waypoint queue for debug visualization.
     * @return List of waypoints, or null if none
     */
    public java.util.List<Vector3> getWaypointQueue() {
        return waypointQueue;
    }

    /**
     * Get current waypoint index in queue.
     * @return Index (0-based)
     */
    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    /**
     * Get the current target waypoint being navigated to.
     * @return Target waypoint vector
     */
    public Vector3 getTargetWaypoint() {
        return targetWaypoint;
    }

    /**
     * Get the current waypoint the NPC is moving toward.
     * Used for debug visualization.
     * @return Current waypoint position, or null if none
     */
    public Vector3 getCurrentWaypoint() {
        return currentWaypoint;
    }

    /**
     * Get the center of the wander zone.
     * Used for debug visualization.
     * @return Wander center position, or null if not wandering
     */
    public Vector3 getWanderCenter() {
        return wanderCenter;
    }

    /**
     * Get the wander radius.
     * Used for debug visualization.
     * @return Wander radius in units
     */
    public float getWanderRadius() {
        return wanderRadius;
    }

    /**
     * Get the current instruction being executed.
     * Used for debug visualization and sync validation.
     * @return Current instruction, or null if none
     */
    public NPCInstructionMessage getCurrentInstruction() {
        return currentInstruction;
    }

    @Override
    public void dispose() {
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
        physicsInitialized = false;
        graphicsInitialized = false;
    }
}
