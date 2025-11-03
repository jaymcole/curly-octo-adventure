package curly.octo.common;

import com.badlogic.gdx.graphics.PerspectiveCamera;

public interface InputController {

    void handleInput(float delta, Possessable target, PerspectiveCamera camera);

    void setPossessionTarget(Possessable target);

    Possessable getPossessionTarget();

    void releasePossession();

    boolean hasPossessionTarget();

    void updateCamera(PerspectiveCamera camera, float delta);
}
