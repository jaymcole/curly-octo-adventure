package curly.octo.map;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.SpawnPointHint;
import curly.octo.map.rendering.ChunkedMapModelBuilder;

import java.util.ArrayList;
import java.util.Set;

/**
 * Debug utility to help diagnose issues with the chunked map model builder.
 */
public class ChunkDebugger {

    /**
     * Perform comprehensive debugging of the chunk system for a given GameMap.
     */
    public static void debugChunkSystem(GameMap gameMap) {
        Log.info("ChunkDebugger", "=== CHUNK SYSTEM DEBUGGING ===");

        // Step 1: Debug the raw map data
        debugRawMapData(gameMap);

        // Step 2: Debug chunk organization
        debugChunkOrganization(gameMap);

        // Step 3: Debug chunked model builder
        debugChunkedModelBuilder(gameMap);

        Log.info("ChunkDebugger", "=== DEBUG COMPLETE ===");
    }

    private static void debugRawMapData(GameMap gameMap) {
        Log.info("ChunkDebugger", "--- Raw Map Data ---");

        ArrayList<MapTile> allTiles = gameMap.getAllTiles();
        Log.info("ChunkDebugger", "Total tiles in GameMap: " + allTiles.size());

        if (allTiles.isEmpty()) {
            Log.warn("ChunkDebugger", "WARNING: GameMap has no tiles!");
            return;
        }

        // Count tiles by geometry type
        int emptyCount = 0;
        int fullCount = 0;
        int halfCount = 0;
        int otherCount = 0;

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        for (MapTile tile : allTiles) {
            // Count by geometry type
            switch (tile.geometryType) {
                case EMPTY:
                    emptyCount++;
                    break;
                case FULL:
                    fullCount++;
                    break;
                case HALF:
                    halfCount++;
                    break;
                default:
                    otherCount++;
                    break;
            }

            // Track world bounds
            minX = Math.min(minX, tile.x);
            maxX = Math.max(maxX, tile.x);
            minY = Math.min(minY, tile.y);
            maxY = Math.max(maxY, tile.y);
            minZ = Math.min(minZ, tile.z);
            maxZ = Math.max(maxZ, tile.z);
        }

        Log.info("ChunkDebugger", String.format(
            "Tile counts: EMPTY=%d, FULL=%d, HALF=%d, OTHER=%d",
            emptyCount, fullCount, halfCount, otherCount
        ));

        Log.info("ChunkDebugger", String.format(
            "World bounds: X[%.1f to %.1f], Y[%.1f to %.1f], Z[%.1f to %.1f]",
            minX, maxX, minY, maxY, minZ, maxZ
        ));

        // Convert to tile coordinates and show bounds
        int minTileX = (int)(minX / Constants.MAP_TILE_SIZE);
        int maxTileX = (int)(maxX / Constants.MAP_TILE_SIZE);
        int minTileY = (int)(minY / Constants.MAP_TILE_SIZE);
        int maxTileY = (int)(maxY / Constants.MAP_TILE_SIZE);
        int minTileZ = (int)(minZ / Constants.MAP_TILE_SIZE);
        int maxTileZ = (int)(maxZ / Constants.MAP_TILE_SIZE);

        Log.info("ChunkDebugger", String.format(
            "Tile coordinate bounds: X[%d to %d], Y[%d to %d], Z[%d to %d]",
            minTileX, maxTileX, minTileY, maxTileY, minTileZ, maxTileZ
        ));

        // Show first few tiles for debugging
        Log.info("ChunkDebugger", "First 5 tiles:");
        for (int i = 0; i < Math.min(5, allTiles.size()); i++) {
            MapTile tile = allTiles.get(i);
            Vector3 tileCoords = new Vector3(
                (int)(tile.x / Constants.MAP_TILE_SIZE),
                (int)(tile.y / Constants.MAP_TILE_SIZE),
                (int)(tile.z / Constants.MAP_TILE_SIZE)
            );
            Log.info("ChunkDebugger", String.format(
                "  Tile %d: world(%.1f,%.1f,%.1f) -> tile(%d,%d,%d) -> %s",
                i, tile.x, tile.y, tile.z,
                (int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z,
                tile.geometryType.name()
            ));
        }

        // Check spawn points
        ArrayList<curly.octo.map.hints.MapHint> spawnHints = gameMap.getAllHintsOfType(SpawnPointHint.class);
        Log.info("ChunkDebugger", "Spawn points found: " + spawnHints.size());

        if (spawnHints.isEmpty()) {
            Log.warn("ChunkDebugger", "WARNING: No spawn points found!");
        } else {
            for (curly.octo.map.hints.MapHint hint : spawnHints) {
                MapTile spawnTile = gameMap.getTile(hint.tileLookupKey);
                if (spawnTile != null) {
                    Log.info("ChunkDebugger", String.format(
                        "  Spawn at world(%.1f,%.1f,%.1f) -> %s",
                        spawnTile.x, spawnTile.y, spawnTile.z,
                        spawnTile.geometryType.name()
                    ));
                }
            }
        }
    }

    private static void debugChunkOrganization(GameMap gameMap) {
        Log.info("ChunkDebugger", "--- Chunk Organization ---");

        try {
            ChunkManager chunkManager = new ChunkManager(gameMap);

            Log.info("ChunkDebugger", "ChunkManager created successfully");
            Log.info("ChunkDebugger", String.format(
                "Created chunks: %d, Max possible chunks: %d",
                chunkManager.getTotalChunkCount(),
                chunkManager.getMaxPossibleChunkCount()
            ));

            Set<LevelChunk> populatedChunks = chunkManager.organizeIntoChunks();
            Log.info("ChunkDebugger", "Populated chunks: " + populatedChunks.size());

            if (populatedChunks.isEmpty()) {
                Log.warn("ChunkDebugger", "WARNING: No populated chunks created!");
            } else {
                int totalTilesInChunks = 0;
                for (LevelChunk chunk : populatedChunks) {
                    totalTilesInChunks += chunk.getSolidTileCount();
                    Log.info("ChunkDebugger", String.format(
                        "  Chunk (%d,%d,%d): %d solid tiles (%.1f%% full)",
                        (int)chunk.getChunkCoordinates().x,
                        (int)chunk.getChunkCoordinates().y,
                        (int)chunk.getChunkCoordinates().z,
                        chunk.getSolidTileCount(),
                        chunk.getLoadFactor() * 100f
                    ));
                }
                Log.info("ChunkDebugger", "Total solid tiles in chunks: " + totalTilesInChunks);
            }

        } catch (Exception e) {
            Log.error("ChunkDebugger", "Exception during chunk organization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void debugChunkedModelBuilder(GameMap gameMap) {
        Log.info("ChunkDebugger", "--- Chunked Model Builder ---");

        try {
            ChunkedMapModelBuilder builder = new ChunkedMapModelBuilder(gameMap);

            Log.info("ChunkDebugger", "ChunkedMapModelBuilder created successfully");
            Log.info("ChunkDebugger", "Strategy description: " + builder.getStrategyDescription());
            Log.info("ChunkDebugger", "Initial stats - Faces: " + builder.getTotalFacesBuilt() + ", Tiles: " + builder.getTotalTilesProcessed());

            // Try to access populated chunks without building geometry
            Set<LevelChunk> chunks = builder.getPopulatedChunks();
            if (chunks == null) {
                Log.info("ChunkDebugger", "Populated chunks not yet initialized (normal for new builder)");
            } else {
                Log.info("ChunkDebugger", "Builder already has " + chunks.size() + " populated chunks");
            }

        } catch (Exception e) {
            Log.error("ChunkDebugger", "Exception with ChunkedMapModelBuilder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Quick debug method that can be called from anywhere to check chunk system status.
     */
    public static void quickDebug(GameMap gameMap, String context) {
        Log.info("ChunkDebugger", "=== QUICK DEBUG: " + context + " ===");

        ArrayList<MapTile> allTiles = gameMap.getAllTiles();
        Log.info("ChunkDebugger", "GameMap has " + allTiles.size() + " tiles");

        if (!allTiles.isEmpty()) {
            int solidTiles = 0;
            for (MapTile tile : allTiles) {
                if (tile.geometryType != MapTileGeometryType.EMPTY) {
                    solidTiles++;
                }
            }
            Log.info("ChunkDebugger", solidTiles + " solid tiles out of " + allTiles.size());

            try {
                ChunkManager chunkManager = new ChunkManager(gameMap);
                Set<LevelChunk> populatedChunks = chunkManager.organizeIntoChunks();
                Log.info("ChunkDebugger", "ChunkManager created " + populatedChunks.size() + " populated chunks");
            } catch (Exception e) {
                Log.error("ChunkDebugger", "ChunkManager failed: " + e.getMessage());
            }
        }

        Log.info("ChunkDebugger", "=== END QUICK DEBUG ===");
    }
}
