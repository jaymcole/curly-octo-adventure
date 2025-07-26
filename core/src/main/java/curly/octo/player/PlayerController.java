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
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class PlayerController extends InputAdapter  {

    private static final float playerHeight = 5;
    private static final float sensitivity = 1f;
    private static final float velocityLen = 100f; // Player movement speed

    private transient PerspectiveCamera camera;
    private transient final Vector3 tmp = new Vector3();
    private transient boolean mouseCaptured = false;
    private transient int lastX, lastY;
    private transient Model placeholderModel;
    private transient ModelInstance placeHolderModelInstance;
    private transient boolean initialized = false;

    private final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();

    // Add these fields to track yaw and pitch
    private float yaw = 0f;   // Horizontal angle, in degrees
    private float pitch = 0f; // Vertical angle, in degrees
    private static final float MAX_PITCH = 89f;
    private static final float MIN_PITCH = -89f;

    private long playerId;
    private transient GameMap gameMap;

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

    public long getPlayerId() {
        return playerId;
    }

    public Vector3 getPosition() {
        return position.cpy();
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
        if (gameMap != null) {
            btKinematicCharacterController controller = gameMap.getPlayerController();
            if (controller != null) {
                Vector3 walkDirection = new Vector3();
                float moveSpeed = velocityLen; // Use existing movement speed

                // Calculate movement direction based on input
                if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                    walkDirection.add(direction.x, 0, direction.z);
                }
                if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                    walkDirection.add(-direction.x, 0, -direction.z);
                }
                Vector3 right = new Vector3(direction).crs(Vector3.Y).nor();
                if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                    walkDirection.add(-right.x, 0, -right.z);
                }
                if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                    walkDirection.add(right.x, 0, right.z);
                }

                // Normalize and scale movement
                if (walkDirection.len2() > 0) {
                    walkDirection.nor().scl(moveSpeed * delta); // Character controller expects per-frame distance
                }

                // Always set walk direction to ensure stopping works
                controller.setWalkDirection(walkDirection);

                // Jump handling
                boolean isOnGround = controller.onGround();
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && isOnGround) {
                    controller.jump(new Vector3(0, 30f, 0));
                }
            }
        }
        updateCamera();
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
}
