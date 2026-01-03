package curly.octo.common.character;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import curly.octo.common.PlayerObject;

public class PlayerBrain implements ICharacterBrain {

    private GameCharacter character;
    private final Vector3 tempDirection = new Vector3();
    private final Vector3 tempVelocity = new Vector3();

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

                // Reverted horizontal control (subtracting deltaX)
                character.setYaw(character.getYaw() - deltaX * mouseSensitivity);
                // Kept vertical control fix (adding deltaY)
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
            tempDirection.add(right); // Fixed: Add for Left (since right vector was inverted relative to desired strafe)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tempDirection.sub(right); // Fixed: Sub for Right
        }

        character.setWalkDirection(tempDirection.nor().scl(PlayerObject.PLAYER_SPEED));

        boolean spaceIsPressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (spaceIsPressed && !spaceWasPressed && character.canJump()) {
            character.jump(new Vector3(0, PlayerObject.JUMP_FORCE, 0));
        }
        spaceWasPressed = spaceIsPressed;
    }
}
