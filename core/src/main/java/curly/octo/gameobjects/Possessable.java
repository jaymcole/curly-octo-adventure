package curly.octo.gameobjects;

import com.badlogic.gdx.math.Vector3;

public interface Possessable {
    
    boolean canBePossessed();
    
    void onPossessionStart();
    
    void onPossessionEnd();
    
    void applyJumpForce(Vector3 direction, float strength);
    
    boolean isOnCooldown();
    
    float getCooldownRemaining();
    
    Vector3 getCameraPosition();
    
    Vector3 getCameraDirection();
    
    boolean isPossessed();
}