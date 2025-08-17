package curly.octo.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import curly.octo.gameobjects.Possessable;

public class PlayerInputController implements InputController {
    
    private Possessable possessionTarget;
    private Vector3 tempDirection = new Vector3();
    private Vector3 tempCameraPos = new Vector3();
    private Vector3 tempCameraDir = new Vector3();
    
    // Mouse sensitivity for camera control
    private float mouseSensitivity = 0.1f;
    private boolean mouseCaptured = false;
    private int lastX, lastY;
    
    @Override
    public void handleInput(float delta, Possessable target, PerspectiveCamera camera) {
        if (target == null || !target.canBePossessed()) {
            return;
        }
        
        handleMouseLook(camera);
        handleMovementInput(target, camera);
    }
    
    private void handleMouseLook(PerspectiveCamera camera) {
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (!mouseCaptured) {
                mouseCaptured = true;
                lastX = Gdx.input.getX();
                lastY = Gdx.input.getY();
                Gdx.input.setCursorCatched(true);
            } else {
                int deltaX = Gdx.input.getX() - lastX;
                int deltaY = Gdx.input.getY() - lastY;
                
                // Rotate camera based on mouse movement
                camera.direction.rotate(camera.up, -deltaX * mouseSensitivity);
                
                // Calculate right vector for pitch rotation
                tempDirection.set(camera.direction).crs(camera.up).nor();
                camera.direction.rotate(tempDirection, deltaY * mouseSensitivity);
                
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
        if (target.isOnCooldown()) {
            return;
        }
        
        // Reset movement direction
        tempDirection.set(0, 0, 0);
        
        // WASD movement in camera-relative directions
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            tempDirection.add(camera.direction.x, 0, camera.direction.z);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            tempDirection.sub(camera.direction.x, 0, camera.direction.z);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            tempDirection.sub(camera.direction).crs(camera.up);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tempDirection.add(camera.direction).crs(camera.up);
        }
        
        // Apply jump force if any movement key was pressed
        if (tempDirection.len2() > 0) {
            tempDirection.nor();
            target.applyJumpForce(tempDirection, 1.0f);
        }
        
        // Space for upward jump
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            tempDirection.set(0, 1, 0);
            target.applyJumpForce(tempDirection, 1.0f);
        }
    }
    
    @Override
    public void setPossessionTarget(Possessable target) {
        if (possessionTarget != null) {
            possessionTarget.onPossessionEnd();
        }
        
        this.possessionTarget = target;
        
        if (target != null) {
            target.onPossessionStart();
        }
    }
    
    @Override
    public Possessable getPossessionTarget() {
        return possessionTarget;
    }
    
    @Override
    public void releasePossession() {
        setPossessionTarget(null);
    }
    
    @Override
    public boolean hasPossessionTarget() {
        return possessionTarget != null;
    }
    
    @Override
    public void updateCamera(PerspectiveCamera camera, float delta) {
        if (possessionTarget == null) {
            return;
        }
        
        // Get target camera position and direction
        tempCameraPos.set(possessionTarget.getCameraPosition());
        tempCameraDir.set(possessionTarget.getCameraDirection());
        
        // Smoothly interpolate camera position and direction
        float lerpFactor = Math.min(delta * 5.0f, 1.0f); // 5x speed, clamped to 1.0
        
        camera.position.lerp(tempCameraPos, lerpFactor);
        camera.direction.lerp(tempCameraDir, lerpFactor).nor();
        camera.up.set(0, 1, 0); // Keep up vector consistent
        
        camera.update();
    }
}