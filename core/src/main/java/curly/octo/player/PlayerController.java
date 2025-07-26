package curly.octo.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.PhysicsManager;
import curly.octo.map.enums.MapTileGeometryType;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class PlayerController extends InputAdapter  {

    private static final float playerHeight = 5;
    private static final float sensitivity = 1f;
    private static final float ACCELERATION = 10.0f; // Tune as needed
    private static final float MAX_SPEED = 20.0f; // Optional: clamp max speed

    private transient PerspectiveCamera camera;
    private transient final Vector3 tmp = new Vector3();
    private transient boolean mouseCaptured = false;
    private transient int lastX, lastY;
    private transient Model placeholderModel;
    private transient ModelInstance placeHolderModelInstance;
    private transient boolean initialized = false;

    private final Vector3 position = new Vector3();
    private final Vector3 momentum = new Vector3();
    private final Vector3 direction = new Vector3();
    private final float dragCoefficient = 0.95f;

    // Add these fields to track yaw and pitch
    private float yaw = 0f;   // Horizontal angle, in degrees
    private float pitch = 0f; // Vertical angle, in degrees
    private static final float MAX_PITCH = 89f;
    private static final float MIN_PITCH = -89f;

    private long playerId;
    private float velocity = 500f;
    private GameMap gameMap;
    private boolean isOnGround = false;
    private static final float GRAVITY = -30f;
    private static final float JUMP_VELOCITY = 20f;
    private static final float velocityLen = 10f; // Player movement speed
    private PhysicsManager physicsManager;
    private final Vector3 lastRayFrom = new Vector3();
    private final Vector3 lastRayTo = new Vector3();

    public PlayerController() {
        // Initialize camera with default values
        position.set(15, 100, 15);
        yaw = 0f;
        pitch = 0f;
        updateDirectionFromAngles();
        // Initialize camera on the OpenGL thread
        Gdx.app.postRunnable(this::initialize);
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public void setPlayerPosition(float x, float y, float z) {
        position.set(x, y, z);
        if (camera != null) {
            camera.position.set(position);
            camera.lookAt(position.x + direction.x, position.y + direction.y, position.z + direction.z);
            camera.update();
        }
    }

    public void setPhysicsManager(PhysicsManager physicsManager) {
        this.physicsManager = physicsManager;
    }

    public long getPlayerId() {
        return playerId;
    }

    public Vector3 getPosition() {
        return position.cpy();
    }

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public void setGameMap(GameMap map) {
        this.gameMap = map;
    }

    private void initialize() {
        if (initialized) return;
        try {
            // Create camera
            this.camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            camera.position.set(position);
            camera.lookAt(position.x + direction.x, position.y + direction.y, position.z + direction.z);
            camera.up.set(Vector3.Y);
            camera.near = 0.1f;
            camera.far = 300f;
            camera.update();

            // Create model on OpenGL thread
            ModelBuilder modelBuilder = new ModelBuilder();
            placeholderModel = modelBuilder.createSphere(1f, 1f, 1f, 16, 16,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            placeHolderModelInstance = new ModelInstance(placeholderModel);

            initialized = true;
        } catch (Exception e) {
            Log.error("PlayerController", "Error initializing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void render(ModelBatch modelBatch, Environment environment, PerspectiveCamera cam) {
        if (!initialized) {
            initialize();
            return;
        }

        try {
            // Position the model 2 units in front of the camera
            Vector3 modelPosition = new Vector3(position);

            // Update model transform
            placeHolderModelInstance.transform.idt();
            placeHolderModelInstance.transform.setToTranslation(modelPosition);

            // Enable depth testing
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);

            // Render the model
            modelBatch.begin(cam);
            modelBatch.render(placeHolderModelInstance, environment);
            modelBatch.end();
        } catch (Exception e) {
            Log.error("PlayerController", "Error in render: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void update(float delta) {
        if (gameMap != null && physicsManager != null) {
            btRigidBody playerBody = physicsManager.getPlayerBody();
            if (playerBody != null) {
                Vector3 desiredVelocity = new Vector3();
                float moveSpeed = 15f; // Target movement speed

                // Calculate desired movement velocity based on input
                if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                    desiredVelocity.add(direction.x, 0, direction.z);
                }
                if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                    desiredVelocity.add(-direction.x, 0, -direction.z);
                }
                Vector3 right = new Vector3(direction).crs(Vector3.Y).nor();
                if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                    desiredVelocity.add(-right.x, 0, -right.z);
                }
                if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                    desiredVelocity.add(right.x, 0, right.z);
                }

                // Get current velocity and ground state
                Vector3 currentVelocity = playerBody.getLinearVelocity();
                boolean isOnGround = isPlayerOnGround();

                // Ground sticking for better slope handling
                if (isOnGround && desiredVelocity.len2() == 0) {
                    // When stopped on ground, gently reduce horizontal movement
                    float horizontalSpeed = new Vector3(currentVelocity.x, 0, currentVelocity.z).len();

                    if (horizontalSpeed > 0.5f) {
                        // Apply moderate stopping force to prevent sliding
                        Vector3 stopForce = new Vector3(-currentVelocity.x, 0, -currentVelocity.z).nor().scl(80f);
                        playerBody.applyCentralForce(stopForce);
                    } else {
                        // When slow enough, gradually reduce movement
                        float dampFactor = 0.7f;
                        playerBody.setLinearVelocity(new Vector3(
                            currentVelocity.x * dampFactor,
                            currentVelocity.y,
                            currentVelocity.z * dampFactor
                        ));
                    }
                } else if (desiredVelocity.len2() > 0) {
                    desiredVelocity.nor().scl(moveSpeed);

                    if (isOnGround) {
                        // On ground: apply slight downward bias to stick to slopes
                        float yVel = Math.min(currentVelocity.y, -2f); // Cap upward velocity on slopes
                        playerBody.setLinearVelocity(new Vector3(desiredVelocity.x, yVel, desiredVelocity.z));
                    } else {
                        // In air: maintain Y velocity for proper gravity
                        playerBody.setLinearVelocity(new Vector3(desiredVelocity.x, currentVelocity.y, desiredVelocity.z));
                    }
                }

                // Jump handling
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && isOnGround) {
                    Log.info("PlayerController.update", "Jumping");
                    Vector3 currentVel = playerBody.getLinearVelocity();
                    playerBody.setLinearVelocity(new Vector3(currentVel.x, 15f, currentVel.z)); // Set Y velocity directly
                }
            }
        }
        updateCamera();
    }

    private void moveForward(float distance) {
        // Add acceleration in the forward direction
        Vector3 tempDirection = new Vector3(direction);
        tempDirection.y = 0;
        Vector3 accel = new Vector3(tempDirection).nor().scl(distance * ACCELERATION);
        momentum.add(accel);
        clampMomentum();
    }

    private void moveLeft(float distance) {
        // Add acceleration to the left (negative right vector)
        Vector3 left = new Vector3(direction).crs(Vector3.Y).nor().scl(-distance * ACCELERATION);
        momentum.add(left);
        clampMomentum();
    }

    private void moveRight(float distance) {
        // Add acceleration to the right (right vector)
        Vector3 right = new Vector3(direction).crs(Vector3.Y).nor().scl(distance * ACCELERATION);
        momentum.add(right);
        clampMomentum();
    }

    // Clamp horizontal momentum to a maximum speed for control
    private void clampMomentum() {
        Vector3 horizontal = new Vector3(momentum.x, 0, momentum.z);
        float speed = horizontal.len();
        if (speed > MAX_SPEED) {
            horizontal.nor().scl(MAX_SPEED);
            momentum.x = horizontal.x;
            momentum.z = horizontal.z;
        }
    }

    private void updateCamera() {
        // Update camera position and direction
        camera.position.set(position);
        camera.position.y += playerHeight;
        // Calculate target point in front of the camera
        tmp.set(direction).add(camera.position);
        camera.lookAt(tmp);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private boolean collidesWithMap(Vector3 pos) {
        if (gameMap == null) return false;
        MapTile tile = gameMap.getTileFromWorldCoordinates(pos.x, pos.y, pos.z);
        if (tile != null && tile.geometryType != MapTileGeometryType.EMPTY) {
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.LEFT) {
            mouseCaptured = true;
            lastX = screenX;
            lastY = screenY;
            Gdx.input.setCursorCatched(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.LEFT) {
            mouseCaptured = false;
            Gdx.input.setCursorCatched(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (mouseCaptured) {
            // Calculate delta from last position
            float deltaX = (screenX - lastX) * sensitivity * 0.1f;
            float deltaY = (lastY - screenY) * sensitivity * 0.1f;

            // Update last position
            lastX = screenX;
            lastY = screenY;

            // Update yaw and pitch
            yaw -= deltaX;
            pitch += deltaY;
            if (pitch > MAX_PITCH) pitch = MAX_PITCH;
            if (pitch < MIN_PITCH) pitch = MIN_PITCH;

            updateDirectionFromAngles();
            updateCamera();
            return true;
        }
        return false;
    }

    // Helper to update the direction vector from yaw and pitch
    private void updateDirectionFromAngles() {
        float yawRad = (float)Math.toRadians(-yaw);
        float pitchRad = (float)Math.toRadians(pitch);
        direction.x = (float)(Math.cos(pitchRad) * Math.sin(yawRad));
        direction.y = (float)(Math.sin(pitchRad));
        direction.z = (float)(-Math.cos(pitchRad) * Math.cos(yawRad));
        direction.nor();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            mouseCaptured = false;
            Gdx.input.setCursorCatched(false);
            return true;
        }
        return false;
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    private boolean isPlayerOnGround() {
        if (physicsManager == null) {
            Log.info("isPlayerOnGround", "physicsManager is null");
            return false;
        }

        btRigidBody playerBody = physicsManager.getPlayerBody();
        Vector3 playerPos = playerBody.getWorldTransform().getTranslation(new Vector3());
        Vector3 from = new Vector3(playerPos.x, playerPos.y - 0.10f, playerPos.z); // just below feet
        Vector3 to = new Vector3(playerPos.x, playerPos.y - 1.5f, playerPos.z);    // a bit further down
        lastRayFrom.set(from);
        lastRayTo.set(to);

        ClosestRayResultCallback rayCallback = new ClosestRayResultCallback(from, to);
        rayCallback.setCollisionFilterGroup(-1);
        physicsManager.getDynamicsWorld().rayTest(from, to, rayCallback);
        boolean onGround = rayCallback.hasHit() ;
        rayCallback.dispose();
        return onGround;
    }
}
