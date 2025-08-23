package curly.octo.map;

import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;
import curly.octo.map.hints.MapHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.ArrayList;

public class MapTile {
    public static final float TILE_SIZE = 5;
    public float x,y,z;
    public MapTileFillType fillType;
    public MapTileGeometryType geometryType;
    public Direction direction;
    public MapTileMaterial material;


    private final ArrayList<MapHint> hints;
    private boolean isSpawn;

    public MapTile() {
        hints = new ArrayList<>();
        fillType = MapTileFillType.AIR;
        geometryType = MapTileGeometryType.EMPTY;
        direction = Direction.NORTH;
        material = MapTileMaterial.STONE;
        isSpawn = false;
    }

    public void AddHint(MapHint hint) {
        hints.add(hint);
        if (hint instanceof SpawnPointHint) {
            isSpawn = true;
        }
    }

    public ArrayList<MapHint> getHints() {
        return hints;
    }

    public boolean isSpawnTile() {
        return isSpawn;
    }
}
