package curly.octo.map.physics;

import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import curly.octo.map.GameMap;

/**
 * Abstract interface for building physics bodies from GameMap data.
 * Allows different strategies for creating collision geometry.
 */
public abstract class PhysicsBodyBuilder {
    
    protected GameMap gameMap;
    protected long totalTriangleCount = 0;
    
    public PhysicsBodyBuilder(GameMap gameMap) {
        this.gameMap = gameMap;
    }
    
    /**
     * Build the triangle mesh for physics collision.
     * @return The built triangle mesh
     */
    public abstract btTriangleMesh buildTriangleMesh();
    
    /**
     * Get the total number of triangles added to the mesh.
     * @return Triangle count
     */
    public long getTotalTriangleCount() {
        return totalTriangleCount;
    }
    
    /**
     * Get a description of this builder strategy for logging.
     * @return Description string
     */
    public abstract String getStrategyDescription();
}