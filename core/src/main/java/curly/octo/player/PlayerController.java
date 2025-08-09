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
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;
import com.esotericsoftware.minlog.Log;
import curly.octo.gameobjects.GameObject;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class PlayerController extends InputAdapter {

    // TODO: Make this a percentage of the max player height
    private static final float playerHeight = 2.5f; // Camera offset from physics body center
    private static final float sensitivity = 1f;
    private static final float velocityLen = 25f; // Player movement speed

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

    private String playerId;
    private transient GameMap gameMap;
//    private transient PointLight playerLight;


    private MapTileFillType currentTileFillType = MapTileFillType.AIR;
    private MapTile currentTile = null;

    public PlayerController() {
        // Initialize camera with default values
        yaw = 0f;
        pitch = 0f;
        updateDirectionFromAngles();

        // Initialize camera on the OpenGL thread
        Gdx.app.postRunnable(this::initialize);
    }

    /**
     * Server-only constructor that skips graphics initialization.
     * Used by GameServer to track player positions without rendering overhead.
     */
    public PlayerController(boolean serverOnly) {
        // Initialize basic state
        yaw = 0f;
        pitch = 0f;
        updateDirectionFromAngles();

        if (serverOnly) {
            // Skip graphics initialization - server only needs position tracking
            initialized = false;
            Log.info("PlayerController", "Created server-only PlayerController (no graphics)");
        } else {
            // Normal client initialization
            Gdx.app.postRunnable(this::initialize);
        }
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public void setPlayerPosition(float x, float y, float z, float delta) {
        position.set(x, y, z);
        if (camera != null) {
            camera.position.set(position);
            camera.position.y += playerHeight; // Add camera height offset here too
            camera.lookAt(position.x + direction.x, position.y + direction.y + playerHeight, position.z + direction.z);
            camera.update();
        }
    }

    public String getPlayerId() {
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
            camera.position.y += playerHeight; // Add camera height offset for initialization too
            camera.lookAt(position.x + direction.x, position.y + direction.y + playerHeight, position.z + direction.z);
            camera.up.set(Vector3.Y);
            camera.near = 0.1f;
            camera.far = 300f;
            camera.update();

            // Create model on OpenGL thread
            ModelBuilder modelBuilder = new ModelBuilder();
            placeholderModel = modelBuilder.createSphere(3f, 3f, 3f, 16, 16,
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
            modelPosition.y += playerHeight;
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
        checkCameraLocation();
    }

    private void checkCameraLocation() {
        if (gameMap == null || camera == null) {
            return;
        }

        Vector3 cameraPos = camera.position;
        MapTile tile = gameMap.getTileFromWorldCoordinates(cameraPos.x, cameraPos.y, cameraPos.z);

        if (tile != null) {
            currentTile = tile;
            MapTileFillType newFillType = tile.fillType;

            if (currentTileFillType != newFillType) {
                currentTileFillType = newFillType;
                onTileFillTypeChanged(newFillType);
            }
        } else {
            if (currentTile != null || currentTileFillType != MapTileFillType.AIR) {
                currentTile = null;
                currentTileFillType = MapTileFillType.AIR;
                onTileFillTypeChanged(MapTileFillType.AIR);
            }
        }
    }

    private void onTileFillTypeChanged(MapTileFillType newType) {
        switch (newType) {
            case FOG:
                Log.info("PlayerController", "Player " + playerId + " entered FOG tile");
                break;
            case WATER:
                Log.info("PlayerController", "Player " + playerId + " entered WATER tile");
                break;
            case LAVA:
                Log.info("PlayerController", "Player " + playerId + " entered LAVA tile");
                break;
            case AIR:
                Log.info("PlayerController", "Player " + playerId + " entered AIR tile");
                break;
            default:
                Log.info("PlayerController", "Player " + playerId + " entered " + newType + " tile");
                break;
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

//    private void createPlayerLight() {
//        playerLight = new PointLight();
//        playerLight.set(1f, 0.9f, 0.7f, position.x, position.y + 3f, position.z, 1); // Warm lantern light
//        Log.info("PlayerController", "Created player light for player " + playerId);
//    }

//    private void updatePlayerLightPosition(float delta) {
        // Ensure light exists (recreate if needed after network deserialization)
//        if (playerLight == null) {
//            createPlayerLight();
//        }
//        if (playerLight != null) {
//            timeSinceLastLightFlicker+=delta;
//
//            if (timeSinceLastLightFlicker > timeToFlicker) {
//                lightXOffset = random.nextFloat() * 2.25f;
//                lightZOffset = random.nextFloat() * 2.25f;
//                timeSinceLastLightFlicker = 0;
//            }
//
//            // Position light slightly above player
//            playerLight.position.set(position.x + lightXOffset - (direction.x * 2), position.y + 3f, position.z + lightZOffset- (direction.z * 2));
//        }
//    }

//    public PointLight getPlayerLight() {
//        // Recreate light if it's null (happens after network deserialization)
//        if (playerLight == null) {
//            createPlayerLight();
//        }
//        return playerLight;
//    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
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
}
