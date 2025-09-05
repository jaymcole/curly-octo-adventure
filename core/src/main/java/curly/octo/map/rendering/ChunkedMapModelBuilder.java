package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.Constants;
import curly.octo.map.ChunkManager;
import curly.octo.map.GameMap;
import curly.octo.map.LevelChunk;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.exploration.TileExplorationManager;
import curly.octo.map.hints.MapHint;

import java.util.*;

/**
 * Map model builder that creates separate ModelInstances for each chunk, enabling
 * dynamic chunk-based rendering. Each chunk gets its own Model and ModelInstance,
 * allowing the renderer to selectively display only chunks near the player position.
 *
 * This approach provides:
 * - True dynamic chunk loading/unloading based on player position
 * - Efficient frustum culling at the chunk level
 * - Better memory management for large maps
 * - Potential for chunk streaming in the future
 */
public class ChunkedMapModelBuilder extends MapModelBuilder implements Disposable {

    private ChunkManager chunkManager;
    private Set<LevelChunk> populatedChunks;
    private Map<LevelChunk, ChunkFaceInfo> chunkFaceVisibility;

    // Chunk-based rendering system
    private Map<LevelChunk, ChunkModelData> chunkModels;
    private Array<ModelInstance> allChunkInstances;

    public ChunkedMapModelBuilder(GameMap gameMap) {
        super(gameMap);
        this.chunkManager = new ChunkManager(gameMap);
        this.chunkFaceVisibility = new HashMap<>();
        this.chunkModels = new HashMap<>();
        this.allChunkInstances = new Array<>();
    }

    /**
     * Get all chunk ModelInstances for rendering.
     * @return Array of all chunk ModelInstances
     */
    public Array<ModelInstance> getAllChunkInstances() {
        return allChunkInstances;
    }

    /**
     * Get chunk ModelInstances within a certain distance of a position.
     * @param position Center position (typically player/camera position)
     * @param maxDistance Maximum distance to include chunks
     * @return Array of nearby chunk ModelInstances
     */
    public Array<ModelInstance> getChunksNearPosition(Vector3 position, float maxDistance) {
        Array<ModelInstance> nearbyInstances = new Array<>();

        for (Map.Entry<LevelChunk, ChunkModelData> entry : chunkModels.entrySet()) {
            LevelChunk chunk = entry.getKey();
            ChunkModelData modelData = entry.getValue();

            // Calculate distance from position to chunk center
            Vector3 chunkCenter = chunk.getWorldOffset().cpy();
            chunkCenter.add(LevelChunk.CHUNK_SIZE * Constants.MAP_TILE_SIZE / 2f); // Center of chunk

            float distance = chunkCenter.dst(position);
            if (distance <= maxDistance) {
                nearbyInstances.add(modelData.instance);
            }
        }

        return nearbyInstances;
    }


    @Override
    public void buildGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial,
                            Material grassMaterial, Material spawnMaterial, Material wallMaterial,
                            Material waterMaterial) {
        // Clear previous models
        dispose();

        totalFacesBuilt = 0;
        totalTilesProcessed = 0;

        // Organize map into chunks using BFS from spawn points
        populatedChunks = chunkManager.organizeIntoChunks();
        Log.info("ChunkedMapModelBuilder", "Creating separate models for " + populatedChunks.size() + " populated chunks");

        // Calculate face visibility for each chunk
        calculateChunkFaceVisibility();

        // Build individual models for each chunk
        buildIndividualChunkModels(stoneMaterial, dirtMaterial, grassMaterial,
                                 spawnMaterial, wallMaterial, waterMaterial);

        Log.info("ChunkedMapModelBuilder", String.format(
            "Created %d chunk models with %d total faces and %d tiles",
            chunkModels.size(), totalFacesBuilt, totalTilesProcessed));
    }

    /**
     * Build separate Model and ModelInstance for each chunk.
     */
    private void buildIndividualChunkModels(Material stoneMaterial, Material dirtMaterial,
                                          Material grassMaterial, Material spawnMaterial,
                                          Material wallMaterial, Material waterMaterial) {
        for (LevelChunk chunk : populatedChunks) {
            // Skip chunks with no solid tiles
            if (chunk.getSolidTileCount() == 0) {
                continue;
            }

            // Create a ModelBuilder for this chunk
            ModelBuilder chunkBuilder = new ModelBuilder();
            chunkBuilder.begin();

            boolean hasGeometry = buildSingleChunkGeometry(chunkBuilder, chunk, stoneMaterial,
                                                         dirtMaterial, grassMaterial, spawnMaterial,
                                                         wallMaterial, waterMaterial);

            if (hasGeometry) {
                // Finish the model
                Model chunkModel = chunkBuilder.end();
                ModelInstance chunkInstance = new ModelInstance(chunkModel);

                // Store the chunk model data
                ChunkModelData modelData = new ChunkModelData(chunk, chunkModel, chunkInstance);
                chunkModels.put(chunk, modelData);
                allChunkInstances.add(chunkInstance);

                Log.debug("ChunkedMapModelBuilder", String.format(
                    "Created model for chunk (%d,%d,%d) with %d tiles",
                    (int)chunk.getChunkCoordinates().x, (int)chunk.getChunkCoordinates().y,
                    (int)chunk.getChunkCoordinates().z, chunk.getSolidTileCount()));
            }
        }
    }

    /**
     * Build geometry for a single chunk.
     * @return true if any geometry was built, false if chunk is empty
     */
    private boolean buildSingleChunkGeometry(ModelBuilder chunkBuilder, LevelChunk chunk,
                                           Material stoneMaterial, Material dirtMaterial,
                                           Material grassMaterial, Material spawnMaterial,
                                           Material wallMaterial, Material waterMaterial) {

        Map<String, MapTile> chunkTiles = chunk.getAllTiles();
        ChunkFaceInfo faceInfo = chunkFaceVisibility.get(chunk);
        boolean hasGeometry = false;

        // Create mesh parts for each material
        MeshPartBuilder stoneBuilder = null, dirtBuilder = null, grassBuilder = null;
        MeshPartBuilder spawnBuilder = null, wallBuilder = null, waterBuilder = null;

        // Build tiles in this chunk
        for (MapTile tile : chunkTiles.values()) {
            // Add spawn markers
            if (tile.isSpawnTile()) {
                if (spawnBuilder == null) {
                    chunkBuilder.node();
                    spawnBuilder = chunkBuilder.part("spawn", GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                        spawnMaterial);
                }
                Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                SphereShapeBuilder.build(spawnBuilder, spawnPosition, 2, 2, 2, 10, 10);
                totalFacesBuilt += 200; // Approximate faces for sphere
                hasGeometry = true;
            }

            // Add solid tile geometry
            if (tile.geometryType != MapTileGeometryType.EMPTY) {
                // Create appropriate builder on demand
                MeshPartBuilder builder = null;
                switch (tile.material) {
                    case DIRT:
                        if (dirtBuilder == null) {
                            chunkBuilder.node();
                            dirtBuilder = chunkBuilder.part("dirt", GL20.GL_TRIANGLES,
                                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                                dirtMaterial);
                        }
                        builder = dirtBuilder;
                        break;
                    case GRASS:
                        if (grassBuilder == null) {
                            chunkBuilder.node();
                            grassBuilder = chunkBuilder.part("grass", GL20.GL_TRIANGLES,
                                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                                grassMaterial);
                        }
                        builder = grassBuilder;
                        break;
                    case WALL:
                        if (wallBuilder == null) {
                            chunkBuilder.node();
                            wallBuilder = chunkBuilder.part("wall", GL20.GL_TRIANGLES,
                                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                                wallMaterial);
                        }
                        builder = wallBuilder;
                        break;
                    case STONE:
                    default:
                        if (stoneBuilder == null) {
                            chunkBuilder.node();
                            stoneBuilder = chunkBuilder.part("stone", GL20.GL_TRIANGLES,
                                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                                stoneMaterial);
                        }
                        builder = stoneBuilder;
                        break;
                }

                boolean[] visibleFaces = faceInfo != null ? faceInfo.getVisibleFaces(tile) : null;
                if (visibleFaces != null) {
                    buildTileGeometry(builder, tile, visibleFaces);
                    // Count visible faces
                    for (boolean face : visibleFaces) {
                        if (face) totalFacesBuilt += 2; // 2 triangles per face
                    }
                } else {
                    // Fallback: build all faces
                    buildTileGeometry(builder, tile);
                    totalFacesBuilt += 12; // 6 faces * 2 triangles each
                }

                totalTilesProcessed++;
                hasGeometry = true;
            }

            // Add water surfaces if requested
            if (waterMaterial != null && tile.fillType == MapTileFillType.WATER) {
                Vector3 tileCoords = getTileCoordinates(tile);
                if (isTopMostFillTile((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, MapTileFillType.WATER)) {
                    if (waterBuilder == null) {
                        chunkBuilder.node();
                        waterBuilder = chunkBuilder.part("water", GL20.GL_TRIANGLES,
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                            waterMaterial);
                    }
                    buildWaterSurface(waterBuilder, tile);
                    totalFacesBuilt += 2; // 2 triangles for water quad
                    hasGeometry = true;
                }
            }
        }

        return hasGeometry;
    }

    @Override
    public void dispose() {
        // Dispose all chunk models
        for (ChunkModelData modelData : chunkModels.values()) {
            if (modelData.model != null) {
                modelData.model.dispose();
            }
        }
        chunkModels.clear();
        allChunkInstances.clear();
    }

    @Override
    public void buildWaterGeometry(ModelBuilder modelBuilder, Material waterMaterial) {
        // Water geometry is now built into individual chunk models during buildGeometry()
        // This method is kept for compatibility but does nothing as water is handled per-chunk
        Log.info("ChunkedMapModelBuilder", "Water geometry is built into individual chunk models - no separate water model needed");
    }

    @Override
    public void buildLavaGeometry(ModelBuilder modelBuilder, Material lavaMaterial) {
        // Lava geometry would be built into individual chunk models during buildGeometry()
        // This method is kept for compatibility but does nothing as lava would be handled per-chunk
        Log.info("ChunkedMapModelBuilder", "Lava geometry would be built into individual chunk models - no separate lava model needed");
    }

    @Override
    public void buildFogGeometry(ModelBuilder modelBuilder, Material fogMaterial) {
        // Fog geometry would be built into individual chunk models during buildGeometry()
        // This method is kept for compatibility but does nothing as fog would be handled per-chunk
        Log.info("ChunkedMapModelBuilder", "Fog geometry would be built into individual chunk models - no separate fog model needed");
    }

    @Override
    public String getStrategyDescription() {
        int totalChunks = chunkManager != null ? chunkManager.getTotalChunkCount() : 0;
        int chunkModelsCreated = chunkModels != null ? chunkModels.size() : 0;
        int allInstancesCount = allChunkInstances != null ? allChunkInstances.size : 0;
        return String.format("Dynamic Chunked Strategy - %d chunk models created from %d total chunks (%d faces, %d tiles, %d instances)",
            chunkModelsCreated, totalChunks, getTotalFacesBuilt(), getTotalTilesProcessed(), allInstancesCount);
    }

    /**
     * Calculate which faces of tiles in each chunk should be visible.
     * This is done by checking neighboring tiles, including tiles in adjacent chunks.
     * Only faces exposed to reachable empty space are marked as visible.
     */
    private void calculateChunkFaceVisibility() {
        chunkFaceVisibility.clear();

        // First, identify all reachable empty tiles using BFS from spawn points
        Set<MapTile> reachableEmptyTiles = findReachableEmptyTiles();

        Log.info("ChunkedMapModelBuilder", String.format(
            "Found %d reachable empty tiles for improved face culling", reachableEmptyTiles.size()));

        for (LevelChunk chunk : populatedChunks) {
            ChunkFaceInfo faceInfo = new ChunkFaceInfo();
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();

            for (MapTile tile : chunkTiles.values()) {
                if (tile.geometryType != MapTileGeometryType.EMPTY) {
                    boolean[] visibleFaces = calculateTileVisibleFaces(tile, reachableEmptyTiles);

                    // Check if this tile has any visible faces
                    boolean hasVisibleFace = false;
                    for (boolean face : visibleFaces) {
                        if (face) {
                            hasVisibleFace = true;
                            break;
                        }
                    }

                    // Fallback: if no faces are visible but this tile is adjacent to reachable space,
                    // make at least one face visible to prevent gaps
                    if (!hasVisibleFace && isTileAdjacentToReachableSpace(tile, reachableEmptyTiles)) {
                        visibleFaces = calculateBasicVisibleFaces(tile); // Use basic culling as fallback
                    }
                    faceInfo.setVisibleFaces(tile, visibleFaces);
                }
            }

            chunkFaceVisibility.put(chunk, faceInfo);
        }
    }

    /**
     * Find all empty tiles reachable using multi-pass BFS.
     * This includes tiles reachable from spawn points AND tiles in isolated regions.
     */
    private Set<MapTile> findReachableEmptyTiles() {
        TileExplorationManager explorationManager = new TileExplorationManager(gameMap);
        List<Set<MapTile>> allRegions = explorationManager.exploreAllRegions();
        
        Set<MapTile> reachableEmptyTiles = new HashSet<>();
        
        // Include empty tiles from all discovered regions (both connected and isolated)
        for (Set<MapTile> region : allRegions) {
            for (MapTile tile : region) {
                if (tile.geometryType == MapTileGeometryType.EMPTY) {
                    reachableEmptyTiles.add(tile);
                }
            }
        }
        
        TileExplorationManager.ExplorationStats stats = explorationManager.getStats();
        Log.info("ChunkedMapModelBuilder", String.format(
            "Multi-pass BFS found %d reachable empty tiles across %d regions: %s", 
            reachableEmptyTiles.size(), allRegions.size(), stats.toString()));
        
        return reachableEmptyTiles;
    }

    /**
     * Calculate which faces of a tile should be visible by checking neighbors.
     * Only faces exposed to reachable empty space are considered visible.
     */
    private boolean[] calculateTileVisibleFaces(MapTile tile, Set<MapTile> reachableEmptyTiles) {
        boolean[] visibleFaces = new boolean[6]; // -X, +X, -Y, +Y, -Z, +Z
        Vector3 tileCoords = getTileCoordinates(tile);

        // Check all 6 directions
        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            int neighborX = (int)tileCoords.x + dx[i];
            int neighborY = (int)tileCoords.y + dy[i];
            int neighborZ = (int)tileCoords.z + dz[i];

            MapTile neighbor = gameMap.getTile(neighborX, neighborY, neighborZ);

            // Face is visible if neighbor is reachable empty space
            if (neighbor != null && neighbor.geometryType == MapTileGeometryType.EMPTY) {
                visibleFaces[i] = reachableEmptyTiles.contains(neighbor);
            } else if (neighbor == null) {
                // Face exposed to outside world - only visible if we're on the boundary of reachable space
                // Check if any adjacent empty tile is reachable
                visibleFaces[i] = isAdjacentToReachableSpace(neighborX, neighborY, neighborZ, reachableEmptyTiles);
            } else {
                // Neighbor is solid, face is not visible
                visibleFaces[i] = false;
            }
        }

        return visibleFaces;
    }

    /**
     * Check if a position (which might be outside the map) is adjacent to reachable empty space.
     */
    private boolean isAdjacentToReachableSpace(int x, int y, int z, Set<MapTile> reachableEmptyTiles) {
        // Check the 6 neighbors of this position
        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            MapTile adjacentTile = gameMap.getTile(x + dx[i], y + dy[i], z + dz[i]);
            if (adjacentTile != null && reachableEmptyTiles.contains(adjacentTile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a tile is adjacent to any reachable empty space (used for gap prevention).
     */
    private boolean isTileAdjacentToReachableSpace(MapTile tile, Set<MapTile> reachableEmptyTiles) {
        Vector3 tileCoords = getTileCoordinates(tile);

        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            int neighborX = (int)tileCoords.x + dx[i];
            int neighborY = (int)tileCoords.y + dy[i];
            int neighborZ = (int)tileCoords.z + dz[i];

            MapTile neighbor = gameMap.getTile(neighborX, neighborY, neighborZ);
            if (neighbor != null && reachableEmptyTiles.contains(neighbor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate basic visible faces (exposed to any empty space) as a fallback.
     */
    private boolean[] calculateBasicVisibleFaces(MapTile tile) {
        boolean[] visibleFaces = new boolean[6];
        Vector3 tileCoords = getTileCoordinates(tile);

        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            int neighborX = (int)tileCoords.x + dx[i];
            int neighborY = (int)tileCoords.y + dy[i];
            int neighborZ = (int)tileCoords.z + dz[i];

            MapTile neighbor = gameMap.getTile(neighborX, neighborY, neighborZ);
            // Face is visible if neighbor doesn't exist or is empty (basic culling)
            visibleFaces[i] = (neighbor == null || neighbor.geometryType == MapTileGeometryType.EMPTY);
        }

        return visibleFaces;
    }


    /**
     * Build geometry for the specified chunks.
     */
    private void buildChunkedGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial,
                                    Material grassMaterial, Material spawnMaterial, Material wallMaterial,
                                    Material waterMaterial, Set<LevelChunk> chunksToRender) {

        // Create mesh parts for each material
        modelBuilder.node();
        MeshPartBuilder stoneBuilder = modelBuilder.part("stone", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            stoneMaterial);

        modelBuilder.node();
        MeshPartBuilder dirtBuilder = modelBuilder.part("dirt", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            dirtMaterial);

        modelBuilder.node();
        MeshPartBuilder grassBuilder = modelBuilder.part("grass", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            grassMaterial);

        modelBuilder.node();
        MeshPartBuilder spawnBuilder = modelBuilder.part("spawn", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            spawnMaterial);

        modelBuilder.node();
        MeshPartBuilder wallBuilder = modelBuilder.part("wall", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            wallMaterial);

        MeshPartBuilder waterBuilder = null;
        if (waterMaterial != null) {
            modelBuilder.node();
            waterBuilder = modelBuilder.part("water", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                waterMaterial);
        }

        int totalRenderedTiles = 0;
        int totalChunksProcessed = 0;

        // Process each chunk to render
        for (LevelChunk chunk : chunksToRender) {
            int chunkRenderedTiles = processChunk(chunk, stoneBuilder, dirtBuilder, grassBuilder,
                                               spawnBuilder, wallBuilder, waterBuilder);
            totalRenderedTiles += chunkRenderedTiles;
            totalChunksProcessed++;
            totalTilesProcessed += chunk.getSolidTileCount();
        }

        Log.info("ChunkedMapModelBuilder",
            String.format("Processed %d/%d chunks, rendered %d tiles with %d faces",
                totalChunksProcessed, populatedChunks.size(), totalRenderedTiles, totalFacesBuilt));
    }

    /**
     * Process a single chunk and add its geometry to the appropriate builders.
     */
    private int processChunk(LevelChunk chunk, MeshPartBuilder stoneBuilder, MeshPartBuilder dirtBuilder,
                           MeshPartBuilder grassBuilder, MeshPartBuilder spawnBuilder, MeshPartBuilder wallBuilder,
                           MeshPartBuilder waterBuilder) {

        Map<String, MapTile> chunkTiles = chunk.getAllTiles();
        ChunkFaceInfo faceInfo = chunkFaceVisibility.get(chunk);
        int renderedTilesInChunk = 0;

        for (MapTile tile : chunkTiles.values()) {
            // Add spawn markers
            if (tile.isSpawnTile()) {
                Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                SphereShapeBuilder.build(spawnBuilder, spawnPosition, 2, 2, 2, 10, 10);
                totalFacesBuilt += 200; // Approximate faces for sphere
            }

            // Add solid tile geometry
            if (tile.geometryType != MapTileGeometryType.EMPTY) {
                MeshPartBuilder builder = selectBuilderByMaterial(tile, stoneBuilder, dirtBuilder,
                                                                grassBuilder, wallBuilder);

                boolean[] visibleFaces = faceInfo != null ? faceInfo.getVisibleFaces(tile) : null;
                if (visibleFaces != null) {
                    buildTileGeometry(builder, tile, visibleFaces);

                    // Count visible faces
                    for (boolean face : visibleFaces) {
                        if (face) totalFacesBuilt += 2; // 2 triangles per face
                    }
                } else {
                    // Fallback: build all faces
                    buildTileGeometry(builder, tile);
                    totalFacesBuilt += 12; // 6 faces * 2 triangles each
                }

                renderedTilesInChunk++;
            }

            // Add water surfaces if requested
            if (waterBuilder != null && tile.fillType == MapTileFillType.WATER) {
                Vector3 tileCoords = getTileCoordinates(tile);
                if (isTopMostFillTile((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, MapTileFillType.WATER)) {
                    buildWaterSurface(waterBuilder, tile);
                    totalFacesBuilt += 2; // 2 triangles for water quad
                }
            }
        }

        return renderedTilesInChunk;
    }

    /**
     * Select the appropriate mesh builder based on tile material.
     */
    private MeshPartBuilder selectBuilderByMaterial(MapTile tile, MeshPartBuilder stoneBuilder,
                                                   MeshPartBuilder dirtBuilder, MeshPartBuilder grassBuilder,
                                                   MeshPartBuilder wallBuilder) {
        switch (tile.material) {
            case DIRT:
                return dirtBuilder;
            case GRASS:
                return grassBuilder;
            case WALL:
                return wallBuilder;
            case STONE:
            default:
                return stoneBuilder;
        }
    }

    /**
     * Build tile geometry with face culling.
     */
    private void buildTileGeometry(MeshPartBuilder builder, MapTile tile, boolean[] visibleFaces) {
        switch(tile.geometryType) {
            case HALF:
                buildCulledHalfTile(builder, tile, visibleFaces);
                break;
            case SLAT:
            case HALF_SLANT:
            case TALL_HALF_SLANT:
                buildSlant(builder, tile); // Keep existing slant logic
                break;
            default:
                buildCulledFullTile(builder, tile, visibleFaces);
                break;
        }
    }

    /**
     * Build tile geometry without face culling (fallback).
     */
    private void buildTileGeometry(MeshPartBuilder builder, MapTile tile) {
        switch(tile.geometryType) {
            case HALF:
                buildHalfTile(builder, tile);
                break;
            case SLAT:
            case HALF_SLANT:
            case TALL_HALF_SLANT:
                buildSlant(builder, tile);
                break;
            default:
                buildFullTile(builder, tile);
                break;
        }
    }

    // Geometry building methods (similar to BFSVisibleMapModelBuilder but chunk-aware)

    private void buildCulledFullTile(MeshPartBuilder builder, MapTile tile, boolean[] visibleFaces) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = Constants.MAP_TILE_SIZE;

        Vector3[] vertices = {
            new Vector3(x, y, z),           // 0
            new Vector3(x + size, y, z),    // 1
            new Vector3(x, y + size, z),    // 2
            new Vector3(x + size, y + size, z), // 3
            new Vector3(x, y, z + size),    // 4
            new Vector3(x + size, y, z + size), // 5
            new Vector3(x, y + size, z + size), // 6
            new Vector3(x + size, y + size, z + size) // 7
        };

        // Build only visible faces: -X=0, +X=1, -Y=2, +Y=3, -Z=4, +Z=5
        if (visibleFaces[0]) buildFace(builder, vertices[0], vertices[4], vertices[6], vertices[2]); // -X face
        if (visibleFaces[1]) buildFace(builder, vertices[5], vertices[1], vertices[3], vertices[7]); // +X face
        if (visibleFaces[2]) buildFace(builder, vertices[4], vertices[0], vertices[1], vertices[5]); // -Y face
        if (visibleFaces[3]) buildFace(builder, vertices[2], vertices[6], vertices[7], vertices[3]); // +Y face
        if (visibleFaces[4]) buildFace(builder, vertices[1], vertices[0], vertices[2], vertices[3]); // -Z face
        if (visibleFaces[5]) buildFace(builder, vertices[4], vertices[5], vertices[7], vertices[6]); // +Z face
    }

    private void buildCulledHalfTile(MeshPartBuilder builder, MapTile tile, boolean[] visibleFaces) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = Constants.MAP_TILE_SIZE;
        float halfHeight = size / 2f;

        Vector3[] vertices = {
            new Vector3(x, y, z),           // 0
            new Vector3(x + size, y, z),    // 1
            new Vector3(x, y + halfHeight, z), // 2
            new Vector3(x + size, y + halfHeight, z), // 3
            new Vector3(x, y, z + size),    // 4
            new Vector3(x + size, y, z + size), // 5
            new Vector3(x, y + halfHeight, z + size), // 6
            new Vector3(x + size, y + halfHeight, z + size) // 7
        };

        if (visibleFaces[0]) buildFace(builder, vertices[0], vertices[4], vertices[6], vertices[2]); // -X face
        if (visibleFaces[1]) buildFace(builder, vertices[5], vertices[1], vertices[3], vertices[7]); // +X face
        if (visibleFaces[2]) buildFace(builder, vertices[4], vertices[0], vertices[1], vertices[5]); // -Y face
        if (visibleFaces[3]) buildFace(builder, vertices[2], vertices[6], vertices[7], vertices[3]); // +Y face
        if (visibleFaces[4]) buildFace(builder, vertices[1], vertices[0], vertices[2], vertices[3]); // -Z face
        if (visibleFaces[5]) buildFace(builder, vertices[4], vertices[5], vertices[7], vertices[6]); // +Z face
    }

    private void buildFullTile(MeshPartBuilder builder, MapTile tile) {
        BoxShapeBuilder.build(builder, tile.x, tile.y, tile.z, Constants.MAP_TILE_SIZE, Constants.MAP_TILE_SIZE, Constants.MAP_TILE_SIZE);
    }

    private void buildHalfTile(MeshPartBuilder builder, MapTile tile) {
        BoxShapeBuilder.build(builder, tile.x, tile.y, tile.z, Constants.MAP_TILE_SIZE, Constants.MAP_TILE_SIZE / 2f, Constants.MAP_TILE_SIZE);
    }

    private void buildSlant(MeshPartBuilder builder, MapTile tile) {
        float vertexOffset = Constants.MAP_TILE_SIZE / 2.0f;

        float minX = tile.x + Constants.MAP_TILE_SIZE / 2.0f - vertexOffset;
        float maxX = tile.x + Constants.MAP_TILE_SIZE / 2.0f + vertexOffset;
        float minY = tile.y + Constants.MAP_TILE_SIZE / 2.0f - vertexOffset;
        float maxY = tile.y + Constants.MAP_TILE_SIZE / 2.0f + vertexOffset;
        float minZ = tile.z + Constants.MAP_TILE_SIZE / 2.0f - vertexOffset;
        float maxZ = tile.z + Constants.MAP_TILE_SIZE / 2.0f + vertexOffset;

        if (tile.geometryType == MapTileGeometryType.HALF_SLANT) {
            maxY -= (vertexOffset);
        }

        Vector3 v000 = new Vector3(minX, minY, minZ);
        Vector3 v001 = new Vector3(minX, minY, maxZ);
        Vector3 v010 = new Vector3(minX, maxY, minZ);
        Vector3 v011 = new Vector3(minX, maxY, maxZ);
        Vector3 v100 = new Vector3(maxX, minY, minZ);
        Vector3 v101 = new Vector3(maxX, minY, maxZ);
        Vector3 v110 = new Vector3(maxX, maxY, minZ);
        Vector3 v111 = new Vector3(maxX, maxY, maxZ);

        switch(tile.direction) {
            case NORTH:
                v010 = v000;
                v011 = v001;
                break;
            case EAST:
                v110 = v100;
                v010 = v000;
                break;
            case SOUTH:
                v111 = v101;
                v110 = v100;
                break;
            case WEST:
                v111 = v101;
                v011 = v001;
                break;
        }
        BoxShapeBuilder.build(builder, v000, v001, v010, v011, v100, v101, v110, v111);
    }

    private void buildFace(MeshPartBuilder builder, Vector3 v0, Vector3 v1, Vector3 v2, Vector3 v3) {
        Vector3 edge1 = new Vector3(v1).sub(v0);
        Vector3 edge2 = new Vector3(v3).sub(v0);
        Vector3 normal = new Vector3(edge1).crs(edge2).nor();

        Vector2 uv0 = new Vector2(0, 0);
        Vector2 uv1 = new Vector2(1, 0);
        Vector2 uv2 = new Vector2(1, 1);
        Vector2 uv3 = new Vector2(0, 1);

        builder.triangle(
            builder.vertex(v0, normal, null, uv0),
            builder.vertex(v1, normal, null, uv1),
            builder.vertex(v2, normal, null, uv2)
        );

        builder.triangle(
            builder.vertex(v0, normal, null, uv0),
            builder.vertex(v2, normal, null, uv2),
            builder.vertex(v3, normal, null, uv3)
        );
    }

    // Surface building methods for transparent materials

    private void buildWaterSurface(MeshPartBuilder builder, MapTile tile) {
        buildSurface(builder, tile, Constants.MAP_TILE_SIZE / 2f);
    }

    private void buildLavaSurface(MeshPartBuilder builder, MapTile tile) {
        buildSurface(builder, tile, Constants.MAP_TILE_SIZE / 2f);
    }

    private void buildFogSurface(MeshPartBuilder builder, MapTile tile) {
        buildSurface(builder, tile, Constants.MAP_TILE_SIZE / 4f);
    }

    private void buildSurface(MeshPartBuilder builder, MapTile tile, float heightOffset) {
        float x = tile.x;
        float y = tile.y + heightOffset;
        float z = tile.z;
        float size = Constants.MAP_TILE_SIZE;

        Vector3 v00 = new Vector3(x, y, z);
        Vector3 v01 = new Vector3(x, y, z + size);
        Vector3 v10 = new Vector3(x + size, y, z);
        Vector3 v11 = new Vector3(x + size, y, z + size);

        Vector3 normal = new Vector3(0, 1, 0);

        Vector2 uv00 = new Vector2(0, 0);
        Vector2 uv01 = new Vector2(0, 1);
        Vector2 uv10 = new Vector2(1, 0);
        Vector2 uv11 = new Vector2(1, 1);

        builder.triangle(
            builder.vertex(v00, normal, null, uv00),
            builder.vertex(v01, normal, null, uv01),
            builder.vertex(v10, normal, null, uv10)
        );

        builder.triangle(
            builder.vertex(v10, normal, null, uv10),
            builder.vertex(v01, normal, null, uv01),
            builder.vertex(v11, normal, null, uv11)
        );
    }

    // Utility methods

    private Vector3 getTileCoordinates(MapTile tile) {
        int tileX = (int)(tile.x / Constants.MAP_TILE_SIZE);
        int tileY = (int)(tile.y / Constants.MAP_TILE_SIZE);
        int tileZ = (int)(tile.z / Constants.MAP_TILE_SIZE);
        return new Vector3(tileX, tileY, tileZ);
    }

    private boolean isTopMostFillTile(int x, int y, int z, MapTileFillType fillType) {
        MapTile tileAbove = gameMap.getTile(x, y + 1, z);
        return tileAbove == null || tileAbove.fillType != fillType;
    }

    // Helper class to store face visibility information for chunks
    private static class ChunkFaceInfo {
        private Map<MapTile, boolean[]> tileFaceVisibility = new HashMap<>();

        void setVisibleFaces(MapTile tile, boolean[] visibleFaces) {
            tileFaceVisibility.put(tile, visibleFaces);
        }

        boolean[] getVisibleFaces(MapTile tile) {
            return tileFaceVisibility.get(tile);
        }
    }

    /**
     * Get the ChunkManager used by this builder.
     * @return The ChunkManager instance
     */
    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    /**
     * Get the populated chunks found during the last build operation.
     * @return Set of populated LevelChunks
     */
    public Set<LevelChunk> getPopulatedChunks() {
        if (populatedChunks == null) {
            populatedChunks = chunkManager.organizeIntoChunks();
        }
        return populatedChunks;
    }

    /**
     * Helper class to store chunk model data.
     */
    private static class ChunkModelData {
        final LevelChunk chunk;
        final Model model;
        final ModelInstance instance;

        ChunkModelData(LevelChunk chunk, Model model, ModelInstance instance) {
            this.chunk = chunk;
            this.model = model;
            this.instance = instance;
        }
    }
}
