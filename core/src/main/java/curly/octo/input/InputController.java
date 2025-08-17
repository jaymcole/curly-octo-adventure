package curly.octo.input;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import curly.octo.gameobjects.Possessable;

public interface InputController {
    
    void handleInput(float delta, Possessable target, PerspectiveCamera camera);
    
    void setPossessionTarget(Possessable target);
    
    Possessable getPossessionTarget();
    
    void releasePossession();
    
    boolean hasPossessionTarget();
    
    void updateCamera(PerspectiveCamera camera, float delta);
}