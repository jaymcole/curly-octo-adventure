package curly.octo.common;

import com.badlogic.gdx.math.Vector3;

public interface Possessable {

    // Possession lifecycle
    boolean canBePossessed();
    void onPossessionStart();
    void onPossessionEnd();
    boolean isPossessed();

    // Movement methods - input controller calls these
    void move(Vector3 direction);
    void jump();
    void stopMovement();

    // Camera control for first-person view
    Vector3 getCameraPosition();
    Vector3 getCameraDirection();
    void rotateLook(float deltaYaw, float deltaPitch);
}
