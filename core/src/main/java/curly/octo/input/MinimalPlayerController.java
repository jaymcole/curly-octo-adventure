package curly.octo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import curly.octo.gameobjects.PlayerObject;
import curly.octo.gameobjects.Possessable;

/**
 * Minimal input controller that handles keyboard/mouse input and applies it to possessable objects.
 * No longer contains player state - that's now in PlayerObject.
 */
public class MinimalPlayerController extends InputAdapter implements InputController {

    private Possessable currentTarget;
    private Vector3 tempDirection = new Vector3();
    private Vector3 tempVelocity = new Vector3();

    // Mouse control
    private float mouseSensitivity = 0.1f;
    private boolean mouseCaptured = false;
    private int lastX, lastY;

    // Jump control
    private boolean spaceWasPressed = false;

    @Override
    public void handleInput(float delta, Possessable target, PerspectiveCamera camera) {
        if (target == null) {
            return;
        }

        handleMouseLook(target, camera);
        handleMovementInput(target, camera);
    }

    private void handleMouseLook(Possessable target, PerspectiveCamera camera) {
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (!mouseCaptured) {
                mouseCaptured = true;
                lastX = Gdx.input.getX();
                lastY = Gdx.input.getY();
                Gdx.input.setCursorCatched(true);
            } else {
                int deltaX = Gdx.input.getX() - lastX;
                int deltaY = Gdx.input.getY() - lastY;

                // Let the target handle its own look rotation
                target.rotateLook(-deltaX * mouseSensitivity, deltaY * mouseSensitivity);

                lastX = Gdx.input.getX();
                lastY = Gdx.input.getY();
            }
        } else {
            if (mouseCaptured) {
                mouseCaptured = false;
                Gdx.input.setCursorCatched(false);
            }
        }
    }

    private void handleMovementInput(Possessable target, PerspectiveCamera camera) {
        // Reset movement direction
        tempDirection.set(0, 0, 0);

        // Get camera-relative directions
        Vector3 forward = camera.direction.cpy();
        forward.y = 0; // Remove vertical component for ground movement
        forward.nor();

        Vector3 right = tempVelocity.set(forward).crs(0, 1, 0).nor();

        // WASD movement
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            tempDirection.add(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            tempDirection.sub(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            tempDirection.sub(right);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tempDirection.add(right);
        }

        // Apply movement - let the target decide how to handle it
        if (tempDirection.len2() > 0) {
            tempDirection.nor();
            target.move(tempDirection);
        } else {
            target.stopMovement();
        }

        // Jump/space - only trigger on key press, not hold
        boolean spaceIsPressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (spaceIsPressed && !spaceWasPressed) {
            target.jump();
        }
        spaceWasPressed = spaceIsPressed;
    }

    @Override
    public void setPossessionTarget(Possessable target) {
        if (currentTarget != null) {
            currentTarget.onPossessionEnd();
        }

        this.currentTarget = target;

        if (target != null) {
            target.onPossessionStart();
        }
    }

    @Override
    public Possessable getPossessionTarget() {
        return currentTarget;
    }

    @Override
    public void releasePossession() {
        setPossessionTarget(null);
    }

    @Override
    public boolean hasPossessionTarget() {
        return currentTarget != null;
    }

    @Override
    public void updateCamera(PerspectiveCamera camera, float delta) {
        if (currentTarget == null) {
            return;
        }

        // Get target camera position and direction
        Vector3 targetPos = currentTarget.getCameraPosition();
        Vector3 targetDir = currentTarget.getCameraDirection();

        // Set camera directly for crisp movement
        camera.position.set(targetPos);
        camera.direction.set(targetDir).nor();
        camera.up.set(0, 1, 0);

        camera.update();
    }
}
