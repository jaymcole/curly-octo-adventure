package curly.octo.common.map;

import curly.octo.common.map.enums.Direction;
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.map.enums.MapTileGeometryType;
import curly.octo.common.map.enums.MapTileMaterial;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.hints.SpawnPointHint;

import java.util.ArrayList;

public class MapTile {
    public float x,y,z;
    public MapTileFillType fillType;
    public MapTileGeometryType geometryType;
    public Direction direction;
    public MapTileMaterial material;
    public String templateName = "<unknown>";;


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
