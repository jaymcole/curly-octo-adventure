package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;

/**
 * Interface for different game modes (Server/Client).
 * This allows the main class to delegate game logic to specific implementations.
 */
public interface GameMode {
    
    /**
     * Initialize the game mode
     */
    void initialize();
    
    /**
     * Update game logic for this frame
     * @param deltaTime time since last frame
     */
    void update(float deltaTime);
    
    /**
     * Render the game world
     * @param modelBatch the model batch for 3D rendering
     * @param environment the lighting environment
     */
    void render(ModelBatch modelBatch, Environment environment);
    
    /**
     * Handle window resize
     * @param width new width
     * @param height new height
     */
    void resize(int width, int height);
    
    /**
     * Clean up resources
     */
    void dispose();
    
    /**
     * Check if the game mode is active
     * @return true if active
     */
    boolean isActive();
    
    /**
     * Get the local player ID
     * @return local player ID
     */
    long getLocalPlayerId();
} 