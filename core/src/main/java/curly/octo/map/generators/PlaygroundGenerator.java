package curly.octo.map.generators;

import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileMaterial;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.Random;

public class PlaygroundGenerator extends MapGenerator{

    public PlaygroundGenerator(Random random) {
        super(random, 22, 6, 22);
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

            map[xIndex+2][i+1][(i*2)+4].direction = CardinalDirection.EAST;
            map[xIndex+2][i+1][(i*2)+4].geometryType = MapTileGeometryType.HALF_SLANT;
            map[xIndex+2][i+1][(i*2)+5].direction = CardinalDirection.EAST;
            map[xIndex+2][i+1][(i*2)+5].geometryType = MapTileGeometryType.HALF;
        }

        MapTile tile = map[random.nextInt(width)][2][random.nextInt(depth)];
        while (tile.geometryType != MapTileGeometryType.EMPTY) {
            tile = map[random.nextInt(width)][2][random.nextInt(depth)];
        }
        tile.AddHint(new SpawnPointHint());

        addLights();

        return map;
    }

    private void addLights() {
        int numberOfLightsToAdd = 2; // Add more lights for testing
        for(int i = 0; i < numberOfLightsToAdd; i++) {
            MapTile tile = map[random.nextInt(width)][2][random.nextInt(depth)];
            while (tile.geometryType != MapTileGeometryType.EMPTY) {
                tile = map[random.nextInt(width)][2][random.nextInt(depth)];
            }
            LightHint light = new LightHint();
            light.intensity = 2; // Increase intensity for visibility

            // Set different colors for variety
            switch(random.nextInt(3)) {
                case 0: // Warm torch light
                    light.color_r = 1.0f;
                    light.color_g = 0.8f;
                    light.color_b = 0.4f;
                    break;
                case 1: // Cool blue crystal
                    light.color_r = 0.4f;
                    light.color_g = 0.6f;
                    light.color_b = 1.0f;
                    break;
                case 2: // Green mystical light
                    light.color_r = 0.2f;
                    light.color_g = 1.0f;
                    light.color_b = 0.3f;
                    break;
            }

            tile.AddHint(light);
        }
    }
}
