package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.SpawnPointHint;
import lights.LightPresets;

import java.util.ArrayList;
import java.util.HashSet;
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

    protected void addSpawn(Vector3 spawnPosition) {
        map.touchTile(spawnPosition);
        map.registerHint(new SpawnPointHint(map.constructKeyFromIndexCoordinates((int)spawnPosition.x,(int)spawnPosition.y,(int)spawnPosition.z)));
    }

    protected void addLight(Vector3 lightPos) {
        if (random.nextBoolean()) {
            return;
        }
        // Ensure light tile exists
        map.touchTile(lightPos);

        // Create light hint at position
        LightHint lightHint = new LightHint(map.constructKeyFromIndexCoordinates(
            (int)lightPos.x, (int)lightPos.y, (int)lightPos.z));
        lightHint.color_r = random.nextFloat();  // Warm white light
        lightHint.color_g = random.nextFloat();
        lightHint.color_b = random.nextFloat();
        lightHint.intensity = random.nextInt(5) + 1;  // Much lower intensity
        lightHint.flicker = LightPresets.LIGHT_FLICKER_1;

        map.registerHint(lightHint);
    }


    private HashSet<MapTile> visitedFloodTiles;
    private ArrayList<Vector3> tilesToFlood;
    protected void initiateFlood(Vector3 tilePosition, MapTileFillType fill) {
        visitedFloodTiles = new HashSet<>();
        tilesToFlood = new ArrayList<>();
        addTileToFlood(tilePosition);
        flood(fill);
        Log.info("flood", "Flooded " + visitedFloodTiles.size() + " tiles");
    }

    private void flood(MapTileFillType fill) {
        while (!tilesToFlood.isEmpty()) {
            Vector3 coordinate = tilesToFlood.remove(0);
            MapTile tile = map.getTile(coordinate);

            Log.info("flood", "flooding: " + coordinate);
            visitedFloodTiles.add(tile);
            tile.fillType = fill;

            addTileToFlood(Direction.advanceVector(Direction.NORTH, coordinate.cpy()));
            addTileToFlood(Direction.advanceVector(Direction.EAST, coordinate.cpy()));
            addTileToFlood(Direction.advanceVector(Direction.SOUTH, coordinate.cpy()));
            addTileToFlood(Direction.advanceVector(Direction.WEST, coordinate.cpy()));
            addTileToFlood(Direction.advanceVector(Direction.DOWN, coordinate.cpy()));
        }
    }

    private void addTileToFlood(Vector3 tileCoordinate) {
        MapTile tile = map.getTile(tileCoordinate);
        if (tile == null || tile.geometryType == MapTileGeometryType.FULL || visitedFloodTiles.contains(tile)) {
            return;
        }
        visitedFloodTiles.add(tile);
        tilesToFlood.add(tileCoordinate);
    }
}
