package curly.octo.map.generators;

import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.Random;

public class BasicMap extends MapGenerator{
    public BasicMap(Random random, GameMap map) {
        super(random, map);
    }

    @Override
    public void generate() {

        int roomSize = 10;

        // Create room interior (empty air space for player to move in)
        for(int x = 1; x < roomSize-1; x++) {
            for(int z = 1; z < roomSize-1; z++) {
                for(int y = 1; y < roomSize-1; y++) {
                    map.touchTile(x, y, z);
                }
            }
        }
        
        // Create solid floor at Y=0
        for(int x = 0; x < roomSize; x++) {
            for(int z = 0; z < roomSize; z++) {
                MapTile floorTile = map.touchTile(x, 0, z);
                floorTile.geometryType = MapTileGeometryType.FULL;
            }
        }

        // Create spawn point INSIDE the room (above the floor)
        map.touchTile(roomSize/2, 1, roomSize/2);  // Make sure there's an empty tile to spawn in
        map.registerHint(new SpawnPointHint((map.constructKeyFromIndexCoordinates(roomSize/2,1,roomSize/2))));

        map.touchTile(roomSize/2, 4, roomSize/2);
        LightHint light = new LightHint((map.constructKeyFromIndexCoordinates(roomSize/2,4,roomSize/2)));
        light.color_r = 1;
        light.color_b = 0;
        light.color_g = 0;
        light.intensity = 1;
        map.registerHint(light);

        closeMap();
    }
}
