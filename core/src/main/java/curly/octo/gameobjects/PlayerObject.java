package curly.octo.gameobjects;

import curly.octo.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;

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
    private Vector3 velocity = new Vector3();
    private Vector3 tempVector = new Vector3();
    private boolean possessed = false;

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
            Model playerModel;

            // Try to load the snowman model first
            try {
                ObjLoader objLoader = new ObjLoader();
                playerModel = objLoader.loadModel(Gdx.files.internal(PLAYER_MODEL_PATH));

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
            if (getPosition() != null) {
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

        // Use character controller for physics-based movement
        if (characterController != null) {
            // Apply horizontal movement via character controller
            // setWalkDirection expects velocity (units per second), not displacement
            tempVector.set(velocity.x, 0, velocity.z);
            characterController.setWalkDirection(tempVector);

            // Check if we can jump (character controller handles ground detection)
            onGround = characterController.canJump();

            // Apply jump if requested and on ground
            if (velocity.y > 0 && onGround) {
                characterController.jump(tempVector.set(0, velocity.y, 0));
                velocity.y = 0; // Reset jump velocity after applying
            }

            // Sync position from physics
            if (getPosition() != null) {

                getPosition().set(characterController.getGhostObject().getWorldTransform().getTranslation(tempVector));

                // Update ModelInstance position using consistent transform logic
                updateModelTransform();
            }
        }
    }

    private void updateCurrentTile() {
        if (gameMap != null && getPosition() != null) {
            currentTile = gameMap.getTileFromWorldCoordinates(getPosition().x, getPosition().y, getPosition().z);
            if (currentTile != null) {
                currentTileFillType = currentTile.fillType;
            } else {
                currentTileFillType = MapTileFillType.AIR;
            }
        }
    }


    // Implementation of Possessable movement interface
    @Override
    public void move(Vector3 direction) {
        // Set horizontal velocity based on movement direction
        this.velocity.x = direction.x * PLAYER_SPEED;
        this.velocity.z = direction.z * PLAYER_SPEED;
    }

    @Override
    public void jump() {
        // Set jump velocity - will be applied in update() if on ground
        this.velocity.y = JUMP_FORCE;
    }

    @Override
    public void stopMovement() {
        // Stop horizontal movement
        this.velocity.x = 0;
        this.velocity.z = 0;
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
        if (getPosition() != null) {
            return tempVector.set(getPosition()).add(0, PLAYER_HEIGHT, 0).cpy();
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
        if (getModelInstance() != null && getPosition() != null && modelBounds != null) {
            // All players now have modelBounds, so use precise bounds-based positioning
            updateModelPositionWithBounds(modelBounds, PLAYER_HEIGHT, PLAYER_MODEL_SCALE, yaw);
        }
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
}
