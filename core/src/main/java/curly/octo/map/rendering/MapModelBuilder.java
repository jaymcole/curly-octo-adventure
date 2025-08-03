package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import curly.octo.map.GameMap;

/**
 * Abstract interface for building 3D models from GameMap data.
 * Allows different strategies for creating visible geometry.
 */
public abstract class MapModelBuilder {
    
    protected GameMap gameMap;
    protected long totalFacesBuilt = 0;
    protected long totalTilesProcessed = 0;
    
    public MapModelBuilder(GameMap gameMap) {
        this.gameMap = gameMap;
    }
    
    /**
     * Build the 3D model geometry using the selected strategy.
     * @param modelBuilder The LibGDX ModelBuilder to use
     * @param stoneMaterial Material for stone tiles
     * @param dirtMaterial Material for dirt tiles
     * @param grassMaterial Material for grass tiles
     * @param spawnMaterial Material for spawn markers
     * @param wallMaterial Material for wall tiles
     * @param waterMaterial Material for water surfaces (can be null to skip water)
     */
    public abstract void buildGeometry(ModelBuilder modelBuilder, 
                                     Material stoneMaterial, Material dirtMaterial, 
                                     Material grassMaterial, Material spawnMaterial, 
                                     Material wallMaterial, Material waterMaterial);
    
    /**
     * Build only water surface geometry for transparent rendering.
     * @param modelBuilder The LibGDX ModelBuilder to use
     * @param waterMaterial Material for water surfaces
     */
    public abstract void buildWaterGeometry(ModelBuilder modelBuilder, Material waterMaterial);
    
    /**
     * Get the total number of faces/triangles added to the model.
     * @return Face count
     */
    public long getTotalFacesBuilt() {
        return totalFacesBuilt;
    }
    
    /**
     * Get the total number of tiles that were processed.
     * @return Tile count
     */
    public long getTotalTilesProcessed() {
        return totalTilesProcessed;
    }
    
    /**
     * Get a description of this builder strategy for logging.
     * @return Description string
     */
    public abstract String getStrategyDescription();
}