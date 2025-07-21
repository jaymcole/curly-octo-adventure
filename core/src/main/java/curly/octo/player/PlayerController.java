package curly.octo.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class PlayerController extends InputAdapter {


    private transient final PerspectiveCamera camera;

    private final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 tmp = new Vector3();

    private float velocity = 10f;
    private float sensitivity = 0.2f;
    private boolean mouseCaptured = false;
    private int lastX, lastY;

    public PlayerController() {
        this.camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 10, 10);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 300f;
        camera.update();
    }

    public void update(float delta) {
        // Handle keyboard movement
        float moveSpeed = velocity * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            Log.info("PlayerController.update", "W Pressed");
            moveForward(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            Log.info("PlayerController.update", "S Pressed");
            moveForward(-moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            Log.info("PlayerController.update", "A Pressed");
            moveLeft(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            Log.info("PlayerController.update", "D Pressed");
            moveRight(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            Log.info("PlayerController.update", "Space Pressed");
            moveUp(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            Log.info("PlayerController.update", "Left Shift Pressed");
            moveUp(-moveSpeed);
        }

        // Update camera position and rotation
        camera.position.set(position);
        Vector3 target = new Vector3(position).add(direction);
        camera.lookAt(target);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private void moveForward(float distance) {
        tmp.set(direction).scl(distance);
        position.add(tmp);
    }

    private void moveLeft(float distance) {
        tmp.set(direction).crs(camera.up).nor().scl(-distance);
        position.add(tmp);
    }

    private void moveRight(float distance) {
        tmp.set(direction).crs(camera.up).nor().scl(distance);
        position.add(tmp);
    }

    private void moveUp(float distance) {
        position.y += distance;
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
        Log.info("PlayerController.touchDragged", "Dragging");
        if (mouseCaptured) {
            // Calculate delta from last position
            float deltaX = (screenX - lastX) * sensitivity;
            float deltaY = (lastY - screenY) * sensitivity;

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

    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    public boolean isMouseCaptured() {
        return mouseCaptured;
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public void render() {

    }
}
