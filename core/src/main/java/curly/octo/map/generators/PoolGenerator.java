package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class PoolGenerator extends MapGenerator{

    private ArrayList<Vector3> indoorSpace;
    private HashSet<MapTile> allIndoorSpaces;

    private ArrayList<MapTile> ceilingTiles;

    public PoolGenerator(Random random, int width, int height, int depth) {
        super(random, width, height, depth);
        indoorSpace = new ArrayList<>();
        allIndoorSpaces = new HashSet<>();
        ceilingTiles = new ArrayList<>();
    }

    @Override
    public MapTile[][][] generate() {
        Vector3 center = getMapCenter();

        int centerX = (int) center.x;
        int centerY = (int) center.y;
        int centerZ = (int) center.z;

        fillHorizontalRectangle(centerX, centerY, centerZ,15,2, MapTileGeometryType.FULL, MapTileFillType.AIR);
        for(int i = 0; i < 3; i++) {
            fillHorizontalRectangle(centerX, centerY + i, centerZ,15,2, MapTileGeometryType.EMPTY, MapTileFillType.AIR);
        }

        int zOffset = 2;
        createPoolRoom(centerX, centerY, centerZ + zOffset, 5, 3, 15, MapTileFillType.LAVA);
        createPoolRoom(centerX + 5, centerY, centerZ + zOffset, 5, 3, 15, MapTileFillType.WATER);
        createPoolRoom(centerX + 10, centerY, centerZ + zOffset, 5, 3, 15, MapTileFillType.FOG);

        map[centerX + 1][centerY][centerZ + 1].AddHint(new SpawnPointHint());
        encloseIndoorSpace();

        for(int i = 0; i < 3; i++) {

            LightHint light = new LightHint();
            light.intensity = 2; // Increase intensity for visibility

            // Set different colors for variety
            switch(random.nextInt(3)) {
                case 0: // Warm torch light
                    light.color_r = 0.3f;
                    light.color_g = 1.0f;
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
            ceilingTiles.get(random.nextInt(ceilingTiles.size())).AddHint(light);
        }

        return map;
    }



    private void createPoolRoom(int startX, int startY, int startZ, int roomWidth, int roomHeight, int roomDepth, MapTileFillType poolType) {
        fillHorizontalRectangle(startX, startY, startZ,15,2, MapTileGeometryType.FULL, MapTileFillType.AIR);
        for(int i = 0; i < roomHeight; i++) {
            fillHorizontalRectangle(startX, startY + i, startZ,roomWidth,roomDepth, MapTileGeometryType.EMPTY, MapTileFillType.AIR);
        }

        startX++;
        startZ++;

        for(int poolDepth = 1; poolDepth < roomWidth; poolDepth++) {
            fillHorizontalRectangle(startX , startY - poolDepth, startZ + poolDepth, roomWidth-2, roomDepth-2 - poolDepth, MapTileGeometryType.EMPTY, poolType);
        }
    }

    private void fillHorizontalRectangle(int startX, int startY, int startZ, int poolWidth, int poolDepth, MapTileGeometryType geometry, MapTileFillType fill) {
        for(int x = startX; x < startX +poolWidth; x++) {
            for(int z = startZ; z < startZ + poolDepth; z++) {
                if (inBounds(x,startY,z)) {
                    map[x][startY][z].geometryType = geometry;
                    if (geometry == MapTileGeometryType.EMPTY) {
                        indoorSpace.add(new Vector3(x,startY,z));
                        allIndoorSpaces.add(map[x][startY][z]);
                    }
                    map[x][startY][z].fillType = fill;
                }
            }
        }
    }

    private void encloseIndoorSpace() {
        for(Vector3 indoor : indoorSpace) {
            int x = (int) indoor.x;
            int y = (int) indoor.y;
            int z = (int) indoor.z;
            checkTileAndMakeWall(x+1,y,z);
            checkTileAndMakeWall(x-1,y,z);
            checkTileAndMakeWall(x,y,z+1);
            checkTileAndMakeWall(x,y,z-1);

            if (checkTileAndMakeWall(x,y+1,z)) {
                ceilingTiles.add(map[x][y][z]);
            }
            checkTileAndMakeWall(x,y-1,z);
        }
    }

    private boolean checkTileAndMakeWall(int x, int y, int z) {
        if (inBounds(x,y,z)) {
            MapTile tile = map[x][y][z];
            if (!allIndoorSpaces.contains(tile) && tile.geometryType == MapTileGeometryType.EMPTY) {
                tile.geometryType = MapTileGeometryType.FULL;
                return true;
            }
        }
        return false;
    }

}
