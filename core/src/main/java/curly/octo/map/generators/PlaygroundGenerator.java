package curly.octo.map.generators;

import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;

import java.util.Random;

public class PlaygroundGenerator extends MapGenerator{

    public PlaygroundGenerator(Random random) {
        super(random, 64, 6, 64);
    }

    @Override
    public MapTile[][][] generate() {
        for(int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                map[x][0][z].geometryType = MapTileGeometryType.FULL;
                if (random.nextBoolean()) {
                    map[x][0][z].material = MapTileMaterial.GRASS;
                } else {
                    map[x][0][z].material = MapTileMaterial.DIRT;
                }
            }
        }

        int xIndex = 6;
        int zIndex = 0;
        for (MapTileGeometryType type : MapTileGeometryType.values()) {
            for (MapTileFillType fill : MapTileFillType.values()) {
                map[xIndex][1][zIndex].geometryType = type;
                map[xIndex][1][zIndex].fillType = fill;
                zIndex += 2;
            }
            zIndex = 0;
            xIndex += 2;
        }

        map[2][1][1].direction = CardinalDirection.NORTH;
        map[2][1][1].geometryType = MapTileGeometryType.SLAT;

        map[1][1][2].direction = CardinalDirection.EAST;
        map[1][1][2].geometryType = MapTileGeometryType.SLAT;

        map[0][1][1].direction = CardinalDirection.SOUTH;
        map[0][1][1].geometryType = MapTileGeometryType.SLAT;

        map[1][1][0].direction = CardinalDirection.WEST;
        map[1][1][0].geometryType = MapTileGeometryType.SLAT;


        map[1][1][1].geometryType = MapTileGeometryType.FULL;

        for(int i = 0; i < depth - 4 && i < height -1; i++) {
            map[xIndex][i+1][i+4].direction = CardinalDirection.EAST;
            map[xIndex][i+1][i+4].geometryType = MapTileGeometryType.SLAT;

        }
        return map;
    }
}
