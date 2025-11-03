package curly.octo.common;

import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileFillType;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

public class PlayerObject extends WorldObject {

    private static final float PLAYER_HEIGHT = Constants.PLAYER_HEIGHT;
    private static final float PLAYER_SPEED = Constants.PLAYER_MOVEMENT_SPEED;
    private static final String PLAYER_MODEL_PATH = Constants.PLAYER_MODEL_PATH;
    private static final float PLAYER_MODEL_SCALE = Constants.PLAYER_MODEL_SCALE;

    private transient GameMap gameMap;
    private transient btKinematicCharacterController characterController;
    private transient boolean graphicsInitialized = false;
    private transient ModelAssetManager.ModelBounds modelBounds;

    // Player-specific state
    private MapTileFillType currentTileFillType = MapTileFillType.AIR;
    private MapTile currentTile = null;
    private MapTileFillType headTileFillType = MapTileFillType.AIR;
    private MapTile headTile = null;
    private Vector3 velocity = new Vector3();
    private Vector3 tempVector = new Vector3();
    private boolean possessed = false;

    // Fly mode state
    private boolean flyModeEnabled = false;
    private Vector3 flyVelocity = new Vector3();
    private static final float FLY_SPEED = Constants.PLAYER_FLY_SPEED;
    private static final float FLY_SPEED_FAST = FLY_SPEED * 3f; // Even faster when shift is held

    // Physics constants (matching old PlayerController behavior)
    private static final float JUMP_FORCE = Constants.PLAYER_JUMP_FORCE;
    private boolean onGround = false;

    // Camera angles for smooth movement
    private float yaw = 0f;
    private float pitch = 0f;
    private static final float MAX_PITCH = Constants.PLAYER_CAMERA_MAX_PITCH;
    private static final float MIN_PITCH = Constants.PLAYER_CAMERA_MIN_PITCH;

    // No-arg constructor for Kryo serialization
    public PlayerObject() {
        super();
        // Don't initialize graphics for serialized objects
    }

    public PlayerObject(String playerId) {
        super(playerId);
    }

    public void initializeGraphicsWithManager(ModelAssetManager modelAssetManager) {
        try {

            Model playerModel = null;
            // Try to load the player model first
            try {

                if (PLAYER_MODEL_PATH.endsWith(".obj")) {
                    ObjLoader objLoader = new ObjLoader();
                    Log.info("PlayerObject", "Loading OBJ model from: " + PLAYER_MODEL_PATH);
                    playerModel = objLoader.loadModel(Gdx.files.internal(PLAYER_MODEL_PATH));

                } else if (PLAYER_MODEL_PATH.endsWith(".gltf")) {
                    GLTFLoader gltfLoader = new GLTFLoader();
                    Log.info("PlayerObject", "Loading GLTF model from: " + PLAYER_MODEL_PATH);

                    SceneAsset sceneAsset = gltfLoader.load(Gdx.files.internal(PLAYER_MODEL_PATH));

                    // Log information about the scene structure
                    Log.info("PlayerObject", "GLTF scene has " + sceneAsset.scene.model.nodes.size + " nodes");
                    Log.info("PlayerObject", "GLTF scene has " + sceneAsset.scene.model.meshes.size + " meshes");
                    Log.info("PlayerObject", "GLTF scene has " + sceneAsset.scene.model.meshParts.size + " mesh parts");

                    // Log position information for each node
                    for (int i = 0; i < sceneAsset.scene.model.nodes.size; i++) {
                        com.badlogic.gdx.graphics.g3d.model.Node node = sceneAsset.scene.model.nodes.get(i);
                        com.badlogic.gdx.math.Vector3 translation = new com.badlogic.gdx.math.Vector3();
                        node.localTransform.getTranslation(translation);
                        Log.info("PlayerObject", "Node " + i + " (" + node.id + ") position: " + translation.toString());

                        // Also log if node has parts (meshes)
                        if (node.parts.size > 0) {
                            Log.info("PlayerObject", "  Node " + i + " has " + node.parts.size + " mesh parts");
                        }
                    }

                    // Use the entire scene model - this should include all models/nodes
                    playerModel = sceneAsset.scene.model;

                    // Process materials for standard LibGDX rendering compatibility
                    for (Material material : playerModel.materials) {
                        Log.info("PlayerObject", "Processing GLTF material with " + material.size() + " attributes");

                        // Check for and log transparency settings
                        if (material.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) {
                            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute blending =
                                (com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute) material.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type);
                            Log.info("PlayerObject", "Material has blending - opacity: " + blending.opacity +
                                ", src: " + blending.sourceFunction + ", dest: " + blending.destFunction);
                        }

                        // Force material to be completely opaque and visible
                        material.set(ColorAttribute.createDiffuse(Color.WHITE));
                        material.set(ColorAttribute.createSpecular(Color.WHITE));

                        // Remove any blending that might make it transparent
                        material.remove(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type);

                        // Add strong blending to ensure visibility
                        com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute blending =
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f);
                        material.set(blending);

                        Log.info("PlayerObject", "Forced GLTF material to be opaque white with full opacity");
                    }
                } else if (PLAYER_MODEL_PATH.endsWith(".glb")) {
                    GLTFLoader gltfLoader = new GLTFLoader();
                    Log.info("PlayerObject", "Loading GLB binary model from: " + PLAYER_MODEL_PATH);

                    // GLB files are binary format and need to be loaded differently
                    SceneAsset sceneAsset = gltfLoader.load(Gdx.files.internal(PLAYER_MODEL_PATH));
                    playerModel = sceneAsset.scene.model;

                    // Try to fix materials for standard LibGDX rendering
                    for (Material material : sceneAsset.scene.model.materials) {
                        Log.info("PlayerObject", "Processing GLTF material with " + material.size() + " attributes");

                        // Check for and log transparency settings
                        if (material.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) {
                            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute blending =
                                (com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute) material.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type);
                            Log.info("PlayerObject", "Material has blending - opacity: " + blending.opacity +
                                ", src: " + blending.sourceFunction + ", dest: " + blending.destFunction);
                        }

                        // Force material to be completely opaque and visible
                        material.set(ColorAttribute.createDiffuse(Color.WHITE));
                        material.set(ColorAttribute.createSpecular(Color.WHITE));

                        // Remove any blending that might make it transparent
                        material.remove(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type);

                        // Add strong blending to ensure visibility
                        com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute blending =
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f);
                        material.set(blending);

                        Log.info("PlayerObject", "Forced GLTF material to be opaque white with full opacity");
                    }

                } else {
                    Log.warn("PlayerObject", "Unsupported model format: " + PLAYER_MODEL_PATH + ". Supported formats: .obj, .gltf, .glb, .fbx (with conversion)");
                    throw new RuntimeException("Unsupported model format: " + PLAYER_MODEL_PATH);
                }



                // Create model instance through the asset manager
                setModelInstance(modelAssetManager.createModelInstance(PLAYER_MODEL_PATH, playerModel));

                // Get model bounds for proper positioning
                modelBounds = modelAssetManager.getModelBounds(PLAYER_MODEL_PATH);

                // Fallback: create bounds directly if not available from asset manager
                if (modelBounds == null) {
                    Log.warn("PlayerObject", "ModelBounds not available from asset manager, creating directly");
                    modelBounds = new ModelAssetManager.ModelBounds(playerModel);
                }

                Log.info("PlayerObject", "Loaded snowman model for player: " + entityId);
            } catch (Exception objException) {
                Log.warn("PlayerObject", "Failed to load snowman model, falling back to default sphere", objException);
                // Fall back to default sphere if snowman model fails to load
                ModelBuilder modelBuilder = new ModelBuilder();
                playerModel = modelBuilder.createSphere(3f, 3f, 3f, 16, 16,
                    new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

                setModelInstance(new ModelInstance(playerModel));
                // Create bounds for procedural model to ensure consistent positioning
                modelBounds = new ModelAssetManager.ModelBounds(playerModel);
            }

            graphicsInitialized = true;

            // Apply current position to the newly created model instance
            if (position != null) {
                updateModelTransform();
            }

            Log.info("PlayerObject", "Graphics initialized for player: " + entityId);
        } catch (Exception e) {
            Log.error("PlayerObject", "Failed to initialize graphics for player: " + entityId, e);
        }
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        // Update current tile information
        updateCurrentTile();

        if (flyModeEnabled) {
            // Fly mode: direct position updates without physics
            updateFlyMode(delta);

            // CRITICAL: Ensure physics doesn't override our fly position
            // Force the character controller to match our current position while maintaining upright orientation
            if (characterController != null && position != null) {
                com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
                transform.setToTranslation(position);
                // Ensure the capsule remains upright (identity rotation = upright)
                // Don't apply any rotation to keep the capsule standing vertically
                characterController.getGhostObject().setWorldTransform(transform);
                // Stop any physics movement
                characterController.setWalkDirection(tempVector.set(0, 0, 0));
            }
        } else {
            // Normal physics-based movement
            updatePhysicsMode(delta);
        }
    }

    private void updateFlyMode(float delta) {
        // Apply fly velocity directly to position
        if (flyVelocity.len2() > 0 && position != null) {
            position.add(tempVector.set(flyVelocity).scl(delta));
            updateModelTransform();
        }

        // Light damping to gradually stop movement when no input
        flyVelocity.scl(0.95f);
    }

    private void updatePhysicsMode(float delta) {
        // Use character controller for physics-based movement
        if (characterController != null) {
            // Apply horizontal movement via character controller
            // setWalkDirection expects velocity (units per second), not displacement
            tempVector.set(velocity.x, 0, velocity.z);
            characterController.setWalkDirection(tempVector);

            // Check if we can jump (character controller handles ground detection)
            onGround = characterController.canJump();

            // Apply jump if requested (ground check already done in jump() method)
            if (velocity.y > 0) {
                characterController.jump(tempVector.set(0, velocity.y, 0));
                velocity.y = 0; // Reset jump velocity after applying
            }

            // Sync position from physics
            if (position != null) {
                position.set(characterController.getGhostObject().getWorldTransform().getTranslation(tempVector));

                // Ensure the character controller's ghost object maintains upright orientation
                // This prevents the capsule from tilting during physics updates
                com.badlogic.gdx.math.Matrix4 currentTransform = characterController.getGhostObject().getWorldTransform();
                com.badlogic.gdx.math.Matrix4 uprightTransform = new com.badlogic.gdx.math.Matrix4();
                uprightTransform.setToTranslation(currentTransform.getTranslation(tempVector));
                characterController.getGhostObject().setWorldTransform(uprightTransform);

                updateModelTransform();
            }
        }
    }

    private void updateCurrentTile() {
        if (gameMap != null && position != null) {
            // Update feet tile (at base position)
            currentTile = gameMap.getTileFromWorldCoordinates(position.x, position.y, position.z);
            if (currentTile != null) {
                currentTileFillType = currentTile.fillType;
            } else {
                currentTileFillType = MapTileFillType.AIR;
            }

            // Update head/camera tile (at position + PLAYER_HEIGHT)
            headTile = gameMap.getTileFromWorldCoordinates(position.x, position.y + PLAYER_HEIGHT, position.z);
            if (headTile != null) {
                headTileFillType = headTile.fillType;
            } else {
                headTileFillType = MapTileFillType.AIR;
            }
        }
    }


    @Override
    public void move(Vector3 direction) {
        if (flyModeEnabled) {
            // In fly mode, convert the input direction to 3D camera-relative movement
            Vector3 cameraDir = getCameraDirection();
            Vector3 cameraRight = new Vector3(cameraDir).crs(0, 1, 0).nor();

            // Build 3D movement vector from camera-relative directions
            Vector3 movement = new Vector3();

            // direction.z = forward/backward (W=1, S=-1)
            if (direction.z != 0) {
                movement.add(new Vector3(cameraDir).scl(direction.z));
            }
            // direction.x = left/right (A=-1, D=1)
            if (direction.x != 0) {
                movement.add(new Vector3(cameraRight).scl(direction.x));
            }

            float currentFlySpeed = getFlySpeed();
            if (movement.len2() > 0) {
                flyVelocity.set(movement.nor().scl(currentFlySpeed));
            } else {
                flyVelocity.setZero();
            }
        } else {
            // Normal ground-based movement
            this.velocity.x = direction.x * PLAYER_SPEED;
            this.velocity.z = direction.z * PLAYER_SPEED;
        }
    }

    @Override
    public void jump() {
        if (flyModeEnabled) {
            // In fly mode, jump means move directly up (world Y axis)
            flyVelocity.y = getFlySpeed();
        } else {
            // Only allow jumping if player is on ground - no queuing of jump actions
            if (characterController != null && characterController.canJump()) {
                this.velocity.y = JUMP_FORCE;
            }
            // If not on ground, ignore the jump input completely
        }
    }

    @Override
    public void stopMovement() {
        if (flyModeEnabled) {
            // In fly mode, stop all movement
            flyVelocity.setZero();
        } else {
            // Stop horizontal movement only
            this.velocity.x = 0;
            this.velocity.z = 0;
        }
    }

    @Override
    public void rotateLook(float deltaYaw, float deltaPitch) {
        addYaw(deltaYaw);
        addPitch(deltaPitch);
    }

    @Override
    public void onPossessionStart() {
        possessed = true;
        Log.info("PlayerObject", "Player " + entityId + " possessed");
    }

    @Override
    public void onPossessionEnd() {
        possessed = false;
        Log.info("PlayerObject", "Player " + entityId + " possession ended");
    }

    @Override
    public boolean isPossessed() {
        return possessed;
    }

    @Override
    public Vector3 getCameraPosition() {
        if (position != null) {
            return tempVector.set(position).add(0, PLAYER_HEIGHT, 0).cpy();
        }
        return new Vector3(0, PLAYER_HEIGHT, 0);
    }

    @Override
    public Vector3 getCameraDirection() {
        // Calculate direction from yaw/pitch
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        return tempVector.set(
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) -Math.sin(pitchRad),
            (float) (Math.cos(pitchRad) * Math.cos(yawRad))
        ).nor().cpy();
    }

    private void updateModelTransform() {
        if (getModelInstance() != null && position != null) {
            if (PLAYER_MODEL_PATH.endsWith(".gltf") || PLAYER_MODEL_PATH.endsWith(".glb")) {
                // For GLTF models, use simple positioning that respects the original model positions
                updateGltfModelPosition();
            } else if (modelBounds != null) {
                // For OBJ models, use bounds-based positioning
                updateModelPositionWithBounds(modelBounds, PLAYER_HEIGHT, PLAYER_MODEL_SCALE, yaw);
            }
        }
    }

    private void updateGltfModelPosition() {
        // Simple positioning for GLTF models that preserves their original positions
        getModelInstance().transform.idt();
        getModelInstance().transform.setToTranslation(position);
        getModelInstance().transform.scl(PLAYER_MODEL_SCALE);
        getModelInstance().transform.rotate(Vector3.Y, yaw);
    }

    @Override
    public void setPosition(Vector3 newPosition) {
        super.setPosition(newPosition);
        // Update model position only if graphics are initialized
        if (graphicsInitialized) {
            updateModelTransform();
        }
    }

    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public void setCharacterController(btKinematicCharacterController characterController) {
        this.characterController = characterController;
    }

    public MapTileFillType getCurrentTileFillType() {
        return currentTileFillType;
    }

    public MapTile getHeadTile() {
        return headTile;
    }

    public MapTileFillType getHeadTileFillType() {
        return headTileFillType;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void addYaw(float deltaYaw) {
        this.yaw += deltaYaw;
        this.yaw = this.yaw % 360f; // Keep in 0-360 range
    }

    public void addPitch(float deltaPitch) {
        setPitch(this.pitch + deltaPitch);
    }

    public boolean isGraphicsInitialized() {
        return graphicsInitialized;
    }

    @Override
    public boolean canBePossessed() {
        if (!graphicsInitialized) {
            // Graphics should be initialized by GameObjectManager when added
            // Don't create duplicate models here
            return false;
        }
        return true;
    }

    /**
     * Resets the player's physics state for map regeneration.
     * Clears velocity, resets position to current coordinates, and reinitializes physics.
     */
    public void resetPhysicsState() {
        try {
            // Reset velocity
            if (velocity != null) {
                velocity.setZero();
            }

            // Reset fly velocity
            if (flyVelocity != null) {
                flyVelocity.setZero();
            }

            // Reset ground state
            onGround = false;

            // If character controller exists, reset its state
            if (characterController != null && gameMap != null) {
                // The character controller will be reinitialized when the new map's physics is set up
                // Just ensure we're in a clean state
                Log.info("PlayerObject", "Physics state reset for player: " + entityId);
            }

        } catch (Exception e) {
            Log.error("PlayerObject", "Error resetting physics state for player " + entityId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Fly mode controls

    /**
     * Toggles fly mode on/off for debugging purposes.
     */
    public void toggleFlyMode() {
        flyModeEnabled = !flyModeEnabled;

        if (flyModeEnabled) {
            // Stop all physics movement when entering fly mode
            velocity.setZero();
            flyVelocity.setZero();

            // Disable character controller physics influence
            if (characterController != null) {
                characterController.setWalkDirection(tempVector.set(0, 0, 0));
                // Sync our position with the current physics position before disabling
                position.set(characterController.getGhostObject().getWorldTransform().getTranslation(tempVector));
                updateModelTransform();
            }

            Log.info("PlayerObject", "Fly mode ENABLED for player: " + entityId);
        } else {
            // Stop all fly movement when exiting fly mode
            flyVelocity.setZero();

            // Re-sync character controller position with our current position
            if (characterController != null && position != null) {
                // Update physics body to match our current fly position while maintaining upright orientation
                com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4();
                transform.setToTranslation(position);
                // Ensure the capsule remains upright (identity rotation = upright)
                characterController.getGhostObject().setWorldTransform(transform);
            }

            Log.info("PlayerObject", "Fly mode DISABLED for player: " + entityId);
        }
    }

    /**
     * Enables fly mode.
     */
    public void enableFlyMode() {
        if (!flyModeEnabled) {
            toggleFlyMode();
        }
    }

    /**
     * Disables fly mode.
     */
    public void disableFlyMode() {
        if (flyModeEnabled) {
            toggleFlyMode();
        }
    }

    /**
     * Checks if fly mode is currently enabled.
     */
    public boolean isFlyModeEnabled() {
        return flyModeEnabled;
    }

    /**
     * Moves down in fly mode (directly down on world Y axis).
     */
    public void flyDown() {
        if (flyModeEnabled) {
            flyVelocity.y = -getFlySpeed();
        }
    }

    /**
     * Gets the current fly speed, accounting for speed boost modifiers.
     */
    public float getFlySpeed() {
        // Check if shift is held for fast flying (handled in input controller)
        return FLY_SPEED;
    }

    /**
     * Sets the vertical component of fly velocity directly.
     * Used for continuous vertical movement while keys are held.
     */
    public void setVerticalFlyVelocity(float verticalVelocity) {
        if (flyModeEnabled) {
            flyVelocity.y = verticalVelocity;
        }
    }

    /**
     * Gets the fast fly speed for when shift is held.
     */
    public float getFastFlySpeed() {
        return FLY_SPEED_FAST;
    }

    /**
     * Sets fly speed multiplier for fast flying.
     */
    public void setFlySpeedMultiplier(float multiplier) {
        if (flyModeEnabled) {
            flyVelocity.scl(multiplier / FLY_SPEED * getFlySpeed());
        }
    }
}
