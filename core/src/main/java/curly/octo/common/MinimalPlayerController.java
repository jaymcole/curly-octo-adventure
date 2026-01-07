package curly.octo.common;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import curly.octo.common.character.GameCharacter;
import curly.octo.common.character.PlayerBrain;
import curly.octo.common.character.WalkingCharacter;

public class MinimalPlayerController implements InputController {

    private Possessable currentTarget;
    private PlayerBrain playerBrain;

    public MinimalPlayerController() {
        this.playerBrain = new PlayerBrain();
    }

    @Override
    public void handleInput(float delta, Possessable target, PerspectiveCamera camera) {
        if (target instanceof WalkingCharacter) {
            playerBrain.update(delta);
        }
    }

    @Override
    public void setPossessionTarget(Possessable target) {
        if (this.currentTarget != null) {
            this.currentTarget.onPossessionEnd();
        }
        this.currentTarget = target;
        if (this.currentTarget != null) {
            this.currentTarget.onPossessionStart();
            if (target instanceof WalkingCharacter) {
                playerBrain.setCharacter((WalkingCharacter) target);
            }
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
        camera.position.set(currentTarget.getCameraPosition());
        camera.direction.set(currentTarget.getCameraDirection()).nor();
        camera.up.set(0, 1, 0);
        camera.update();
    }
}
