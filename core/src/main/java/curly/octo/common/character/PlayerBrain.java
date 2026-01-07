package curly.octo.common.character;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;

public class PlayerBrain implements ICharacterBrain {

    private GameCharacter character;
    private final Vector3 tempDirection = new Vector3();

    private float mouseSensitivity = 0.1f;
    private boolean mouseCaptured = false;
    private int lastX, lastY;

    private boolean spaceWasPressed = false;

    @Override
    public void setCharacter(GameCharacter character) {
        this.character = character;
        if (character != null) {
            Log.info("PlayerBrain", "[BRAIN_DEBUG] Possessed character: " + character.entityId);
        } else {
            Log.info("PlayerBrain", "[BRAIN_DEBUG] Possessed character set to NULL");
        }
    }

    @Override
    public void update(float delta) {
        if (character == null) {
            return;
        }
        handleMouseLook();
        handleMovementInput();
    }

    private void handleMouseLook() {
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (!mouseCaptured) {
                mouseCaptured = true;
                lastX = Gdx.input.getX();
                lastY = Gdx.input.getY();
                Gdx.input.setCursorCatched(true);
            } else {
                int deltaX = Gdx.input.getX() - lastX;
                int deltaY = Gdx.input.getY() - lastY;

                character.setYaw(character.getYaw() - deltaX * mouseSensitivity);
                character.setPitch(character.getPitch() + deltaY * mouseSensitivity);


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

    private void handleMovementInput() {
        tempDirection.set(0, 0, 0);

        float yawRad = (float) Math.toRadians(character.getYaw());
        Vector3 forward = new Vector3((float)Math.sin(yawRad), 0, (float)Math.cos(yawRad));
        Vector3 right = new Vector3(forward.z, 0, -forward.x);

        boolean inputDetected = false;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            tempDirection.add(forward);
            inputDetected = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            tempDirection.sub(forward);
            inputDetected = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            tempDirection.add(right);
            inputDetected = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tempDirection.sub(right);
            inputDetected = true;
        }

        if (inputDetected) {
            // Log only when moving to avoid spam, throttled
            if (System.currentTimeMillis() % 1000 < 50) {
                Log.info("PlayerBrain", "[MOVE_DEBUG] Input keys detected for " + character.entityId + ". Direction: " + tempDirection);
            }
        }

        character.setWalkDirection(tempDirection.nor());

        boolean spaceIsPressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (spaceIsPressed && !spaceWasPressed && character.canJump()) {
            Log.info("PlayerBrain", "[MOVE_DEBUG] Jump requested for " + character.entityId);
            character.jump();
        }
        spaceWasPressed = spaceIsPressed;
    }
}
