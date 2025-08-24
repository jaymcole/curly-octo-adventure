package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.ChunkManager;
import curly.octo.map.GameMap;
import curly.octo.map.LevelChunk;
import curly.octo.map.MapTile;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Map model builder that uses chunk-based organization for efficient mesh generation.
 * This builder processes tiles organized into chunks, allowing for better memory usage,
 * culling optimizations, and potentially per-chunk mesh updates in the future.
 */
public class ChunkedMapModelBuilder extends MapModelBuilder {

    private ChunkManager chunkManager;
    private Set<LevelChunk> populatedChunks;
    private Map<LevelChunk, ChunkFaceInfo> chunkFaceVisibility;

    public ChunkedMapModelBuilder(GameMap gameMap) {
        super(gameMap);
        this.chunkManager = new ChunkManager(gameMap);
        this.chunkFaceVisibility = new HashMap<>();
    }

    @Override
    public void buildGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial,
                            Material grassMaterial, Material spawnMaterial, Material wallMaterial,
                            Material waterMaterial) {

        totalFacesBuilt = 0;
        totalTilesProcessed = 0;

        // Organize map into chunks using BFS from spawn points
        populatedChunks = chunkManager.organizeIntoChunks();
        Log.info("ChunkedMapModelBuilder", "Processing " + populatedChunks.size() + " populated chunks");

        // Calculate face visibility for each chunk (similar to BFS approach but chunk-aware)
        calculateChunkFaceVisibility();

        // Log chunk face visibility stats
        int totalVisibleFaces = 0;
        int totalSolidTiles = 0;
        for (LevelChunk chunk : populatedChunks) {
            ChunkFaceInfo faceInfo = chunkFaceVisibility.get(chunk);
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();
            for (MapTile tile : chunkTiles.values()) {
                if (tile.geometryType != MapTileGeometryType.EMPTY) {
                    totalSolidTiles++;
                    boolean[] faces = faceInfo != null ? faceInfo.getVisibleFaces(tile) : null;
                    if (faces != null) {
                        for (boolean face : faces) {
                            if (face) totalVisibleFaces++;
                        }
                    } else {
                        totalVisibleFaces += 6; // Fallback: all faces visible
                    }
                }
            }
        }

        Log.info("ChunkedMapModelBuilder", String.format(
            "Face visibility: %d solid tiles, %d visible faces total (avg %.1f faces/tile)",
            totalSolidTiles, totalVisibleFaces, (float)totalVisibleFaces / Math.max(1, totalSolidTiles)));

        // Check if we might exceed vertex limits (rough estimate)
        int estimatedTriangles = totalVisibleFaces * 2; // 2 triangles per face
        int estimatedVertices = estimatedTriangles * 3; // 3 vertices per triangle
        Log.info("ChunkedMapModelBuilder", String.format(
            "Estimated geometry: %d triangles, %d vertices",
            estimatedTriangles, estimatedVertices));

        // Limit chunks to avoid vertex overflow
        Set<LevelChunk> chunksToRender = populatedChunks;
        if (estimatedVertices > 50000) {
            Log.warn("ChunkedMapModelBuilder",
                "WARNING: High vertex count detected! Limiting to first chunks to avoid LibGDX vertex limits.");

            // Calculate safe chunk limit (aim for ~40000 vertices max)
            int maxVertices = 40000;
            int avgVerticesPerChunk = estimatedVertices / Math.max(1, populatedChunks.size());
            int maxChunks = Math.max(1, maxVertices / Math.max(1, avgVerticesPerChunk));

            Log.info("ChunkedMapModelBuilder", String.format(
                "Limiting to %d chunks (avg %d vertices/chunk)", maxChunks, avgVerticesPerChunk));

            chunksToRender = new HashSet<>();
            int chunkCount = 0;
            for (LevelChunk chunk : populatedChunks) {
                if (chunkCount >= maxChunks) break;
                chunksToRender.add(chunk);
                chunkCount++;
            }
        }

        // Build geometry chunk by chunk
        buildChunkedGeometry(modelBuilder, stoneMaterial, dirtMaterial, grassMaterial,
                           spawnMaterial, wallMaterial, waterMaterial, chunksToRender);
    }

    @Override
    public void buildWaterGeometry(ModelBuilder modelBuilder, Material waterMaterial) {
        if (populatedChunks == null) {
            populatedChunks = chunkManager.organizeIntoChunks();
        }

        modelBuilder.node();
        MeshPartBuilder waterBuilder = modelBuilder.part("water-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            waterMaterial);

        int waterSurfaceCount = 0;

        // Process water surfaces chunk by chunk
        for (LevelChunk chunk : populatedChunks) {
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();

            for (MapTile tile : chunkTiles.values()) {
                if (tile.fillType == MapTileFillType.WATER) {
                    Vector3 tileCoords = getTileCoordinates(tile);
                    if (isTopMostFillTile((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, MapTileFillType.WATER)) {
                        buildWaterSurface(waterBuilder, tile);
                        waterSurfaceCount++;
                    }
                }
            }
        }

        Log.info("ChunkedMapModelBuilder", "Built " + waterSurfaceCount + " water surfaces across " + populatedChunks.size() + " chunks");
    }

    @Override
    public void buildLavaGeometry(ModelBuilder modelBuilder, Material lavaMaterial) {
        if (populatedChunks == null) {
            populatedChunks = chunkManager.organizeIntoChunks();
        }

        modelBuilder.node();
        MeshPartBuilder lavaBuilder = modelBuilder.part("lava-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            lavaMaterial);

        int lavaSurfaceCount = 0;

        // Process lava surfaces chunk by chunk
        for (LevelChunk chunk : populatedChunks) {
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();

            for (MapTile tile : chunkTiles.values()) {
                if (tile.fillType == MapTileFillType.LAVA) {
                    Vector3 tileCoords = getTileCoordinates(tile);
                    if (isTopMostFillTile((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, MapTileFillType.LAVA)) {
                        buildLavaSurface(lavaBuilder, tile);
                        lavaSurfaceCount++;
                    }
                }
            }
        }

        Log.info("ChunkedMapModelBuilder", "Built " + lavaSurfaceCount + " lava surfaces across " + populatedChunks.size() + " chunks");
    }

    @Override
    public void buildFogGeometry(ModelBuilder modelBuilder, Material fogMaterial) {
        if (populatedChunks == null) {
            populatedChunks = chunkManager.organizeIntoChunks();
        }

        modelBuilder.node();
        MeshPartBuilder fogBuilder = modelBuilder.part("fog-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
            fogMaterial);

        int fogSurfaceCount = 0;

        // Process fog surfaces chunk by chunk
        for (LevelChunk chunk : populatedChunks) {
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();

            for (MapTile tile : chunkTiles.values()) {
                if (tile.fillType == MapTileFillType.FOG) {
                    Vector3 tileCoords = getTileCoordinates(tile);
                    if (isTopMostFillTile((int)tileCoords.x, (int)tileCoords.y, (int)tileCoords.z, MapTileFillType.FOG)) {
                        buildFogSurface(fogBuilder, tile);
                        fogSurfaceCount++;
                    }
                }
            }
        }

        Log.info("ChunkedMapModelBuilder", "Built " + fogSurfaceCount + " fog surfaces across " + populatedChunks.size() + " chunks");
    }

    @Override
    public String getStrategyDescription() {
        int totalChunks = chunkManager != null ? chunkManager.getTotalChunkCount() : 0;
        int populatedCount = populatedChunks != null ? populatedChunks.size() : 0;
        return String.format("Chunked Strategy - %d populated chunks out of %d total chunks (%d faces, %d tiles)",
            populatedCount, totalChunks, getTotalFacesBuilt(), getTotalTilesProcessed());
    }

    /**
     * Calculate which faces of tiles in each chunk should be visible.
     * This is done by checking neighboring tiles, including tiles in adjacent chunks.
     */
    private void calculateChunkFaceVisibility() {
        chunkFaceVisibility.clear();

        for (LevelChunk chunk : populatedChunks) {
            ChunkFaceInfo faceInfo = new ChunkFaceInfo();
            Map<String, MapTile> chunkTiles = chunk.getAllTiles();

            for (MapTile tile : chunkTiles.values()) {
                if (tile.geometryType != MapTileGeometryType.EMPTY) {
                    boolean[] visibleFaces = calculateTileVisibleFaces(tile);
                    faceInfo.setVisibleFaces(tile, visibleFaces);
                }
            }

            chunkFaceVisibility.put(chunk, faceInfo);
        }
    }

    /**
     * Calculate which faces of a tile should be visible by checking neighbors.
     * This method checks neighbors even across chunk boundaries.
     */
    private boolean[] calculateTileVisibleFaces(MapTile tile) {
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

            // Face is visible if neighbor doesn't exist or is empty
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
        float size = MapTile.TILE_SIZE;

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
        float size = MapTile.TILE_SIZE;
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
        BoxShapeBuilder.build(builder, tile.x, tile.y, tile.z, MapTile.TILE_SIZE, MapTile.TILE_SIZE, MapTile.TILE_SIZE);
    }

    private void buildHalfTile(MeshPartBuilder builder, MapTile tile) {
        BoxShapeBuilder.build(builder, tile.x, tile.y, tile.z, MapTile.TILE_SIZE, MapTile.TILE_SIZE / 2f, MapTile.TILE_SIZE);
    }

    private void buildSlant(MeshPartBuilder builder, MapTile tile) {
        float vertexOffset = MapTile.TILE_SIZE / 2.0f;

        float minX = tile.x + MapTile.TILE_SIZE / 2.0f - vertexOffset;
        float maxX = tile.x + MapTile.TILE_SIZE / 2.0f + vertexOffset;
        float minY = tile.y + MapTile.TILE_SIZE / 2.0f - vertexOffset;
        float maxY = tile.y + MapTile.TILE_SIZE / 2.0f + vertexOffset;
        float minZ = tile.z + MapTile.TILE_SIZE / 2.0f - vertexOffset;
        float maxZ = tile.z + MapTile.TILE_SIZE / 2.0f + vertexOffset;

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
        buildSurface(builder, tile, MapTile.TILE_SIZE / 2f);
    }

    private void buildLavaSurface(MeshPartBuilder builder, MapTile tile) {
        buildSurface(builder, tile, MapTile.TILE_SIZE / 2f);
    }

    private void buildFogSurface(MeshPartBuilder builder, MapTile tile) {
        buildSurface(builder, tile, MapTile.TILE_SIZE / 4f);
    }

    private void buildSurface(MeshPartBuilder builder, MapTile tile, float heightOffset) {
        float x = tile.x;
        float y = tile.y + heightOffset;
        float z = tile.z;
        float size = MapTile.TILE_SIZE;

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
        int tileX = (int)(tile.x / MapTile.TILE_SIZE);
        int tileY = (int)(tile.y / MapTile.TILE_SIZE);
        int tileZ = (int)(tile.z / MapTile.TILE_SIZE);
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
}
