package curly.octo.gameobjects;

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
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;

public class PlayerObject extends WorldObject {

    private static final float PLAYER_HEIGHT = 2.5f;
    private static final float PLAYER_SPEED = 25f;

    private String playerId;
    private transient GameMap gameMap;
    private transient btKinematicCharacterController characterController;
    private transient boolean graphicsInitialized = false;

    // Player-specific state
    private MapTileFillType currentTileFillType = MapTileFillType.AIR;
    private MapTile currentTile = null;
    private Vector3 velocity = new Vector3();
    private Vector3 tempVector = new Vector3();
    private boolean possessed = false;

    // Physics constants (matching old PlayerController behavior)
    private static final float GRAVITY = -50f;
    private static final float JUMP_FORCE = 25f;
    private boolean onGround = false;

    // Camera angles for smooth movement
    private float yaw = 0f;
    private float pitch = 0f;
    private static final float MAX_PITCH = 89f;
    private static final float MIN_PITCH = -89f;

    // No-arg constructor for Kryo serialization
    public PlayerObject() {
        super();
        this.playerId = "unknown";
        // Don't initialize graphics for serialized objects
    }

    public PlayerObject(String playerId) {
        super(playerId);
        this.playerId = playerId;

        // Initialize graphics on OpenGL thread
        Gdx.app.postRunnable(this::initializeGraphics);
    }

    public PlayerObject(String playerId, boolean serverOnly) {
        super(playerId);
        this.playerId = playerId;

        if (!serverOnly) {
            Gdx.app.postRunnable(this::initializeGraphics);
        }
        Log.info("PlayerObject", "Created " + (serverOnly ? "server-only" : "client") + " PlayerObject: " + playerId);
    }

    private void initializeGraphics() {
        try {
            // Create player model
            ModelBuilder modelBuilder = new ModelBuilder();
            Model playerModel = modelBuilder.createSphere(3f, 3f, 3f, 16, 16,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

            setModelInstance(new ModelInstance(playerModel));
            graphicsInitialized = true;

            Log.info("PlayerObject", "Graphics initialized for player: " + playerId);
        } catch (Exception e) {
            Log.error("PlayerObject", "Failed to initialize graphics for player: " + playerId, e);
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
            tempVector.set(velocity.x, 0, velocity.z).scl(delta);
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

    // Legacy methods for backward compatibility
    public void setVelocity(Vector3 velocity) {
        move(velocity);
    }

    public void addVelocity(Vector3 velocity) {
        if (velocity.y > 0) {
            jump();
        } else if (velocity.len2() > 0) {
            move(velocity);
        }
    }

    public Vector3 getVelocity() {
        return velocity.cpy();
    }

    @Override
    public void onPossessionStart() {
        possessed = true;
        Log.info("PlayerObject", "Player " + playerId + " possessed");
    }

    @Override
    public void onPossessionEnd() {
        possessed = false;
        Log.info("PlayerObject", "Player " + playerId + " possession ended");
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

    // Player-specific getters/setters
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public btKinematicCharacterController getCharacterController() {
        return characterController;
    }

    public void setCharacterController(btKinematicCharacterController characterController) {
        this.characterController = characterController;
    }

    public MapTileFillType getCurrentTileFillType() {
        return currentTileFillType;
    }

    public MapTile getCurrentTile() {
        return currentTile;
    }

    public boolean isInFog() {
        return currentTileFillType == MapTileFillType.FOG;
    }

    public boolean isInWater() {
        return currentTileFillType == MapTileFillType.WATER;
    }

    public boolean isInLava() {
        return currentTileFillType == MapTileFillType.LAVA;
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
        this.pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
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
            // Try to initialize graphics if they haven't been initialized yet
            if (Gdx.app != null) {
                Gdx.app.postRunnable(this::initializeGraphics);
            }
            return false;
        }
        return true;
    }
}
