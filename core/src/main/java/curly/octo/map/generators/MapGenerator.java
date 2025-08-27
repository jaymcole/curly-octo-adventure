package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import curly.octo.Constants;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;

import java.util.ArrayList;
import java.util.Random;

public abstract class MapGenerator {
    protected Random random;
    protected GameMap map;

    public MapGenerator(Random random, GameMap map) {
        this.map = map;
        this.random = random;
    }
    public abstract void generate();

    protected void closeMap() {
        ArrayList<MapTile> currentTiles = new ArrayList<>(map.getAllTiles());
        for(MapTile tile : currentTiles) {
            if (tile.geometryType != MapTileGeometryType.FULL) {
                // Convert world coordinates back to tile indices
                int tileX = (int)(tile.x / Constants.MAP_TILE_SIZE);
                int tileY = (int)(tile.y / Constants.MAP_TILE_SIZE);
                int tileZ = (int)(tile.z / Constants.MAP_TILE_SIZE);

                // Close neighboring tiles
                closeTile(tileX, tileY+1, tileZ);
                closeTile(tileX, tileY-1, tileZ);

                closeTile(tileX+1, tileY, tileZ);
                closeTile(tileX-1, tileY, tileZ);

                closeTile(tileX, tileY, tileZ+1);
                closeTile(tileX, tileY, tileZ-1);
            }
        }
    }

    private void closeTile(int x, int y, int z) {
        if (map.getTile(x, y, z) == null) {
            MapTile tile = map.touchTile(x, y, z);
            tile.geometryType = MapTileGeometryType.FULL;
        }
    }

}
