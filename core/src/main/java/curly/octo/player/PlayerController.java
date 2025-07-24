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
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
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

    private long playerId;
    private float velocity = 500f;
    private GameMap gameMap;
    private boolean isOnGround = false;
    private static final float GRAVITY = -30f;
    private static final float JUMP_VELOCITY = 20f;

    public PlayerController() {
        // Initialize camera with default values
        position.set(15, 100, 15);
        direction.set(0, 0, -1);

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
            Vector3 modelPosition = new Vector3(camera.position);

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
        // Apply gravity
        momentum.y += GRAVITY * delta;

        // Handle keyboard movement
        momentum.x *= dragCoefficient;
        momentum.z *= dragCoefficient;
        float moveSpeed = velocity * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            moveForward(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            moveForward(-moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            moveLeft(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            moveRight(moveSpeed);
        }
        // Jumping
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && isOnGround) {
            momentum.y = JUMP_VELOCITY;
            isOnGround = false;
        }

        // Predict new position
        Vector3 intendedPosition = new Vector3(position).add(momentum.cpy().scl(delta));
        isOnGround = false;
        if (gameMap != null) {
            // Check collision for each axis separately (simple AABB)
            // X axis
            Vector3 testPos = new Vector3(intendedPosition.x, position.y, position.z);
            if (collidesWithMap(testPos)) {
                momentum.x = 0;
            } else {
                position.x = testPos.x;
            }
            // Z axis
            testPos.set(position.x, position.y, intendedPosition.z);
            if (collidesWithMap(testPos)) {
                momentum.z = 0;
            } else {
                position.z = testPos.z;
            }
            // Y axis
            testPos.set(position.x, intendedPosition.y, position.z);
            if (collidesWithMap(testPos)) {
                if (momentum.y < 0) {
                    isOnGround = true;
                }
                momentum.y = 0;
            } else {
                position.y = testPos.y;
            }
        } else {
            position.add(momentum.cpy().scl(delta));
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
        // Calculate target point in front of the camera
        tmp.set(direction).add(position);
        camera.lookAt(tmp);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private boolean collidesWithMap(Vector3 pos) {
        if (gameMap == null) return false;
        MapTile tile = gameMap.getTileFromWorldCoordinates(pos.x, pos.y - playerHeight, pos.z);
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

            // Rotate horizontally (around Y axis)
            direction.rotate(Vector3.Y, -deltaX);

            // Calculate right vector for vertical rotation
            Vector3 right = new Vector3().set(direction).crs(Vector3.Y).nor();

            // Rotate vertically (limit to prevent over-rotation)
            float newAngle = (float)Math.acos(Vector3.Y.dot(direction.nor()));
            if ((deltaY < 0 && newAngle > 0.1f) || (deltaY > 0 && newAngle < Math.PI - 0.1f)) {
                direction.rotate(right, deltaY);
            }

            // Normalize direction to prevent drift
            direction.nor();

            // Update camera after rotation
            updateCamera();
            return true;
        }
        return false;
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
}
