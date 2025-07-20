package curly.octo.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;

/**
 * Handles camera movement and input for 3D navigation.
 */
public class CameraController extends InputAdapter {
    private final PerspectiveCamera camera;
    private final Quaternion rotation = new Quaternion();
    private final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 tmp = new Vector3();

    private float velocity = 10f;
    private float sensitivity = 0.2f;
    private boolean mouseCaptured = false;
    private int lastX, lastY;

    public CameraController(PerspectiveCamera camera) {
        this.camera = camera;
        this.position.set(camera.position);
        this.direction.set(camera.direction);
    }

    public void update(float delta) {
        // Handle keyboard movement
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
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            moveUp(moveSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            moveUp(-moveSpeed);
        }

        // Update camera position and direction
        camera.position.set(position);
        camera.direction.set(direction);
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
        if (mouseCaptured) {
            Log.info("CameraController", "Dragging mouse");
            float deltaX = (screenX - lastX) * sensitivity;
            float deltaY = (lastY - screenY) * sensitivity;

            // Rotate horizontally
             direction.rotate(camera.up, -deltaX);

            // Rotate vertically (limit to prevent over-rotation)
            tmp.set(direction).crs(camera.up).nor();
            if (!(direction.y > 0.9f && deltaY < 0) && !(direction.y < -0.9f && deltaY > 0)) {
                direction.rotate(tmp, -deltaY);
            }

            lastX = screenX;
            lastY = screenY;
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
}
