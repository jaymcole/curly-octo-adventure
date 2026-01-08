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


        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            tempDirection.add(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            tempDirection.sub(forward);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            tempDirection.add(right);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tempDirection.sub(right);
        }

        // Only normalize if the vector has length (prevent NaN from zero vector)
        if (tempDirection.len2() > 0.0001f) {
            character.setWalkDirection(tempDirection.nor());
        } else {
            character.setWalkDirection(new Vector3(0, 0, 0));
        }

        boolean spaceIsPressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (spaceIsPressed && !spaceWasPressed && character.canJump()) {
            character.jump();
        }
        spaceWasPressed = spaceIsPressed;
    }
}
