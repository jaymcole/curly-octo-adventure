package curly.octo.common.map.exploration;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileGeometryType;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.hints.SpawnPointHint;

import java.util.*;

/**
 * Manages tile exploration and discovery using multi-pass BFS to ensure all
 * map areas (including disjointed regions) are explored and processed.
 *
 * This class provides:
 * - Multi-pass BFS starting from spawn points, then from unvisited tiles
 * - Tile tracking to prevent duplicate processing
 * - Discovery of isolated map regions that aren't connected to spawn areas
 * - Utilities for checking tile connectivity and reachability
 */
public class TileExplorationManager {

    private final GameMap gameMap;
    private final Set<MapTile> visitedTiles;
    private final Set<MapTile> allTiles;
    private final List<Set<MapTile>> explorationPasses;

    public TileExplorationManager(GameMap gameMap) {
        this.gameMap = gameMap;
        this.visitedTiles = new HashSet<>();
        this.allTiles = new HashSet<>(gameMap.getAllTiles());
        this.explorationPasses = new ArrayList<>();
    }

    /**
     * Performs multi-pass BFS exploration starting from spawn points,
     * then continuing with additional passes from unvisited tiles until
     * all tiles have been explored.
     *
     * @return List of tile sets, each representing a connected region discovered in a BFS pass
     */
    public List<Set<MapTile>> exploreAllRegions() {
        Log.info("TileExplorationManager", "Starting multi-pass BFS exploration");

        explorationPasses.clear();
        visitedTiles.clear();

        int passNumber = 1;

        // Pass 1: Explore from spawn points (primary connected regions)
        Set<MapTile> primaryRegion = exploreFromSpawnPoints();
        if (!primaryRegion.isEmpty()) {
            explorationPasses.add(primaryRegion);
            Log.info("TileExplorationManager", String.format("Pass %d (spawn-based): explored %d tiles",
                passNumber, primaryRegion.size()));
            passNumber++;
        }

        // Additional passes: Explore from unvisited tiles (isolated regions)
        while (hasUnvisitedTiles()) {
            Set<MapTile> isolatedRegion = exploreFromUnvisitedTile();
            if (!isolatedRegion.isEmpty()) {
                explorationPasses.add(isolatedRegion);
                Log.info("TileExplorationManager", String.format("Pass %d (isolated): explored %d tiles",
                    passNumber, isolatedRegion.size()));
                passNumber++;
            } else {
                // Safety break if we can't make progress
                Log.warn("TileExplorationManager", "Unable to make progress on remaining unvisited tiles");
                break;
            }
        }

        int totalExplored = visitedTiles.size();
        int totalTiles = allTiles.size();
        Log.info("TileExplorationManager", String.format(
            "Exploration complete: %d passes, %d/%d tiles explored (%d unvisited)",
            explorationPasses.size(), totalExplored, totalTiles, totalTiles - totalExplored));

        return new ArrayList<>(explorationPasses);
    }

    /**
     * Performs BFS exploration starting from all spawn points in the map.
     *
     * @return Set of tiles reachable from spawn points
     */
    private Set<MapTile> exploreFromSpawnPoints() {
        Set<MapTile> reachableTiles = new HashSet<>();
        Queue<MapTile> bfsQueue = new ArrayDeque<>();

        // Find all spawn points and start BFS from them
        ArrayList<MapHint> spawnHints = gameMap.getAllHintsOfType(SpawnPointHint.class);

        if (!spawnHints.isEmpty()) {
            for (MapHint hint : spawnHints) {
                MapTile spawnTile = gameMap.getTile(hint.tileLookupKey);
                if (spawnTile != null && !visitedTiles.contains(spawnTile)) {
                    bfsQueue.offer(spawnTile);
                    visitedTiles.add(spawnTile);
                    reachableTiles.add(spawnTile);
                }
            }
        } else {
            // Fallback: start from first empty tile found
            Log.warn("TileExplorationManager", "No spawn points found, starting from first empty tile");
            for (MapTile tile : allTiles) {
                if (tile.geometryType == MapTileGeometryType.EMPTY) {
                    bfsQueue.offer(tile);
                    visitedTiles.add(tile);
                    reachableTiles.add(tile);
                    break;
                }
            }
        }

        // Perform BFS from spawn points
        return performBFS(bfsQueue, reachableTiles);
    }

    /**
     * Performs BFS exploration starting from an unvisited tile.
     * This is used to discover isolated regions not connected to spawn points.
     *
     * @return Set of tiles in the newly discovered isolated region
     */
    private Set<MapTile> exploreFromUnvisitedTile() {
        Set<MapTile> isolatedRegion = new HashSet<>();
        Queue<MapTile> bfsQueue = new ArrayDeque<>();

        // Find the first unvisited tile
        MapTile startTile = findUnvisitedTile();
        if (startTile == null) {
            return isolatedRegion;
        }

        bfsQueue.offer(startTile);
        visitedTiles.add(startTile);
        isolatedRegion.add(startTile);

        // Perform BFS from this unvisited tile
        return performBFS(bfsQueue, isolatedRegion);
    }

    /**
     * Performs BFS using the given queue and adds discovered tiles to the result set.
     *
     * @param bfsQueue Queue of tiles to process
     * @param resultSet Set to add discovered tiles to
     * @return The result set with all discovered tiles
     */
    private Set<MapTile> performBFS(Queue<MapTile> bfsQueue, Set<MapTile> resultSet) {
        while (!bfsQueue.isEmpty()) {
            MapTile currentTile = bfsQueue.poll();

            // Explore all 6 neighbors
            Vector3 tileCoords = getTileCoordinates(currentTile);
            exploreNeighbors(tileCoords, bfsQueue, resultSet);
        }

        return resultSet;
    }

    /**
     * Explores the 6 neighboring positions around a tile coordinate.
     * Adds unvisited neighbors to the BFS queue and result set.
     */
    private void exploreNeighbors(Vector3 tileCoords, Queue<MapTile> bfsQueue, Set<MapTile> resultSet) {
        // Check all 6 directions (3D neighbors)
        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            int neighborX = (int)tileCoords.x + dx[i];
            int neighborY = (int)tileCoords.y + dy[i];
            int neighborZ = (int)tileCoords.z + dz[i];

            MapTile neighbor = gameMap.getTile(neighborX, neighborY, neighborZ);

            if (neighbor != null && !visitedTiles.contains(neighbor)) {
                bfsQueue.offer(neighbor);
                visitedTiles.add(neighbor);
                resultSet.add(neighbor);
            }
        }
    }

    /**
     * Finds the first unvisited tile in the map.
     *
     * @return An unvisited MapTile, or null if all tiles have been visited
     */
    private MapTile findUnvisitedTile() {
        for (MapTile tile : allTiles) {
            if (!visitedTiles.contains(tile)) {
                return tile;
            }
        }
        return null;
    }

    /**
     * Checks if there are any unvisited tiles remaining in the map.
     *
     * @return true if there are unvisited tiles, false otherwise
     */
    private boolean hasUnvisitedTiles() {
        return visitedTiles.size() < allTiles.size();
    }

    /**
     * Gets the number of unvisited tiles remaining.
     *
     * @return Count of unvisited tiles
     */
    public int getUnvisitedTileCount() {
        return allTiles.size() - visitedTiles.size();
    }

    /**
     * Gets all tiles that have been visited during exploration.
     *
     * @return Set of visited tiles
     */
    public Set<MapTile> getVisitedTiles() {
        return new HashSet<>(visitedTiles);
    }

    /**
     * Gets all tiles that have not yet been visited.
     *
     * @return Set of unvisited tiles
     */
    public Set<MapTile> getUnvisitedTiles() {
        Set<MapTile> unvisited = new HashSet<>(allTiles);
        unvisited.removeAll(visitedTiles);
        return unvisited;
    }

    /**
     * Gets the results of all exploration passes.
     *
     * @return List of tile sets, each representing a connected region
     */
    public List<Set<MapTile>> getExplorationPasses() {
        return new ArrayList<>(explorationPasses);
    }

    /**
     * Checks if a tile is reachable from spawn points (i.e., in the first exploration pass).
     *
     * @param tile The tile to check
     * @return true if the tile is reachable from spawn points, false otherwise
     */
    public boolean isReachableFromSpawn(MapTile tile) {
        if (explorationPasses.isEmpty()) {
            return false;
        }
        return explorationPasses.get(0).contains(tile);
    }

    /**
     * Gets the exploration pass number that contains the given tile.
     *
     * @param tile The tile to find
     * @return Pass number (1-based), or -1 if tile not found in any pass
     */
    public int getExplorationPassForTile(MapTile tile) {
        for (int i = 0; i < explorationPasses.size(); i++) {
            if (explorationPasses.get(i).contains(tile)) {
                return i + 1; // Return 1-based pass number
            }
        }
        return -1;
    }

    /**
     * Converts a MapTile's world coordinates to tile coordinates.
     *
     * @param tile The tile to get coordinates for
     * @return Vector3 with tile coordinates
     */
    private Vector3 getTileCoordinates(MapTile tile) {
        int tileX = (int)(tile.x / Constants.MAP_TILE_SIZE);
        int tileY = (int)(tile.y / Constants.MAP_TILE_SIZE);
        int tileZ = (int)(tile.z / Constants.MAP_TILE_SIZE);
        return new Vector3(tileX, tileY, tileZ);
    }

    /**
     * Resets the exploration state, allowing for fresh exploration.
     */
    public void reset() {
        visitedTiles.clear();
        explorationPasses.clear();
    }

    /**
     * Gets statistics about the exploration results.
     *
     * @return ExplorationStats object with detailed information
     */
    public ExplorationStats getStats() {
        return new ExplorationStats(
            allTiles.size(),
            visitedTiles.size(),
            explorationPasses.size(),
            explorationPasses.isEmpty() ? 0 : explorationPasses.get(0).size(),
            explorationPasses.size() > 1 ? explorationPasses.stream().skip(1).mapToInt(Set::size).sum() : 0
        );
    }

    /**
     * Statistics class for exploration results.
     */
    public static class ExplorationStats {
        public final int totalTiles;
        public final int visitedTiles;
        public final int explorationPasses;
        public final int tilesFromSpawn;
        public final int tilesFromIsolatedRegions;

        public ExplorationStats(int totalTiles, int visitedTiles, int explorationPasses,
                               int tilesFromSpawn, int tilesFromIsolatedRegions) {
            this.totalTiles = totalTiles;
            this.visitedTiles = visitedTiles;
            this.explorationPasses = explorationPasses;
            this.tilesFromSpawn = tilesFromSpawn;
            this.tilesFromIsolatedRegions = tilesFromIsolatedRegions;
        }

        @Override
        public String toString() {
            return String.format(
                "ExplorationStats{total=%d, visited=%d, passes=%d, fromSpawn=%d, isolated=%d}",
                totalTiles, visitedTiles, explorationPasses, tilesFromSpawn, tilesFromIsolatedRegions);
        }
    }
}
