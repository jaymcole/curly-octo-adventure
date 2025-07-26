package curly.octo.map.generators;

import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;

import java.util.Random;

public abstract class MapGenerator {
    protected int width;
    protected int height;
    protected int depth;
    protected Random random;

    protected MapTile[][][] map;

    public MapGenerator(Random random, int width, int height, int depth) {
        this.random = random;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.map = new MapTile[width][height][depth];
        initialWorldWithAir();
    }

    protected void initialWorldWithAir() {
        for(int x = 0; x < map.length; x++) {
            for(int y = 0; y < map[x].length; y++) {
                for(int z = 0; z < map[x][y].length; z++) {
                    map[x][y][z] = new MapTile();
                    map[x][y][z].x = x * MapTile.TILE_SIZE;
                    map[x][y][z].y = y * MapTile.TILE_SIZE;
                    map[x][y][z].z = z * MapTile.TILE_SIZE;
                    map[x][y][z].fillType = MapTileFillType.AIR;
                    map[x][y][z].geometryType = MapTileGeometryType.EMPTY;
                }
            }
        }
    }

    public abstract MapTile[][][] generate();
}
