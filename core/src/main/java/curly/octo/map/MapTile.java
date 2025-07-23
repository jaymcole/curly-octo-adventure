package curly.octo.map;

import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;

public class MapTile {
    public static final float TILE_SIZE = 5;
    public float x,y,z;
    public MapTileFillType fillType;
    public MapTileGeometryType geometryType;
    public CardinalDirection direction;
    public MapTileMaterial material;

    public MapTile() {
        fillType = MapTileFillType.AIR;
        geometryType = MapTileGeometryType.EMPTY;
        direction = CardinalDirection.NORTH;
        material = MapTileMaterial.STONE;
    }
}
