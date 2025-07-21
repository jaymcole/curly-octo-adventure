package curly.octo.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class PlayerController extends InputAdapter {

    private static final float sensitivity = 1f;
    private transient final PerspectiveCamera camera;
    private transient final Vector3 tmp = new Vector3();
    private transient boolean mouseCaptured = false;
    private transient int lastX, lastY;
    private transient Model placeholderModel;
    private transient ModelInstance placeHolderModelInstance;

    private final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();

    private long playerId;
    private float velocity = 10f;

    public PlayerController() {
        this.camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        position.set(0, 10, 10);
        direction.set(0, 0, -1);
        camera.position.set(position);
        camera.lookAt(position.x + direction.x, position.y + direction.y, position.z + direction.z);
        camera.up.set(Vector3.Y);
        camera.near = 0.1f;
        camera.far = 300f;
        camera.update();

        ModelBuilder modelBuilder = new ModelBuilder();
        placeholderModel = modelBuilder.createSphere(10f, 10f, 10f, 20, 20,
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        placeHolderModelInstance = new ModelInstance(placeholderModel);
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public void render(ModelBatch modelBatch, Environment environment) {
//        Log.info("PlayerController.render", "Rendering player model");
        modelBatch.render(placeHolderModelInstance, environment);
    }

    public void update(float delta) {
        // Handle keyboard movement
        float moveSpeed = velocity * delta;
        boolean moved = false;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            moveForward(moveSpeed);
            moved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            moveForward(-moveSpeed);
            moved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            moveLeft(moveSpeed);
            moved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            moveRight(moveSpeed);
            moved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            moveUp(moveSpeed);
            moved = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            moveUp(-moveSpeed);
            moved = true;
        }

        // Update camera position and direction
        if (moved) {
            updateCamera();
            updateModel();
        }

    }

    private void moveForward(float distance) {
        tmp.set(direction).nor().scl(distance);
        position.add(tmp);
    }

    private void moveLeft(float distance) {
        // Calculate right vector and move left
        tmp.set(direction).crs(Vector3.Y).nor().scl(-distance);
        position.add(tmp);
    }

    private void moveRight(float distance) {
        // Calculate right vector and move right
        tmp.set(direction).crs(Vector3.Y).nor().scl(distance);
        position.add(tmp);
    }

    private void moveUp(float distance) {
        position.y += distance;
    }

    private void updateModel() {
        Vector3 modelPosition =  new Vector3(position);
        modelPosition.x += 10;
        modelPosition.y += 10;
        modelPosition.z += 10;
        placeHolderModelInstance.transform.set(modelPosition, new Quaternion());
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

    public boolean isMouseCaptured() {
        return mouseCaptured;
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public void dispose() {
        if (placeholderModel != null) {
            placeholderModel.dispose();
        }
    }

}
