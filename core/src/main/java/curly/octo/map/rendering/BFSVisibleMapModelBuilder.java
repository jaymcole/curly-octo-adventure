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
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.hints.MapHint;

import java.util.*;

/**
 * Map model builder that uses BFS from spawn points to only create geometry
 * for faces that are visible to players. This potentially reduces rendered faces
 * significantly by culling hidden internal faces.
 */
public class BFSVisibleMapModelBuilder extends MapModelBuilder {
    
    private Set<MapTile> reachableTiles = new HashSet<>();
    private Set<MapTile> visibleTiles = new HashSet<>();
    private Set<MapTile> tileFilter = null; // Optional filter for chunked rendering
    private Map<MapTile, boolean[]> visibleFaces = new HashMap<>(); // Which faces of each tile are visible
    
    public BFSVisibleMapModelBuilder(GameMap gameMap) {
        super(gameMap);
    }

    public BFSVisibleMapModelBuilder() {
        super(null);
    }

    /**
     * Set tile filter for chunked rendering
     */
    public void setTileFilter(Set<MapTile> filter) {
        this.tileFilter = filter;
    }

    /**
     * Clear tile filter
     */
    public void clearTileFilter() {
        this.tileFilter = null;
    }

    /**
     * Set the game map (for chunked rendering)
     */
    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
    }
    
    @Override
    public void buildGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial, 
                            Material grassMaterial, Material spawnMaterial, Material wallMaterial,
                            Material waterMaterial) {
        
        totalFacesBuilt = 0;
        totalTilesProcessed = 0;
        
        // Step 1: Find all reachable empty tiles from spawn points
        findReachableAreas();
        
        // Step 2: Find visible tiles and their visible faces
        findVisibleTilesAndFaces();
        
        Log.info("BFSVisibleMapModelBuilder", 
            String.format("Found %d reachable tiles, %d visible tiles for rendering", 
                reachableTiles.size(), visibleTiles.size()));
        
        // Step 3: Build geometry using chunk-based approach, but only for visible faces
        buildVisibleGeometry(modelBuilder, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, wallMaterial, waterMaterial);
    }
    
    @Override
    public void buildWaterGeometry(ModelBuilder modelBuilder, Material waterMaterial) {
        // Create a single mesh part for all water surfaces
        modelBuilder.node();
        MeshPartBuilder waterBuilder = modelBuilder.part("water-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, waterMaterial);
        
        int waterSurfaceCount = 0;
        
        // Find all water surfaces and build them by iterating over actual tiles (or filtered tiles)
        Collection<MapTile> tilesToProcess = (tileFilter != null) ? tileFilter : gameMap.getAllTiles();
        for (MapTile tile : tilesToProcess) {
            if (tile.fillType == MapTileFillType.WATER) {
                int x = (int)(tile.x / MapTile.TILE_SIZE);
                int y = (int)(tile.y / MapTile.TILE_SIZE);
                int z = (int)(tile.z / MapTile.TILE_SIZE);
                
                if (isTopMostWaterTile(x, y, z)) {
                    buildWaterSurface(waterBuilder, tile);
                    waterSurfaceCount++;
                }
            }
        }
        
        Log.info("BFSVisibleMapModelBuilder", "Built " + waterSurfaceCount + " water surfaces for transparent rendering");
    }
    
    @Override
    public void buildLavaGeometry(ModelBuilder modelBuilder, Material lavaMaterial) {
        // Create a single mesh part for all lava surfaces
        modelBuilder.node();
        MeshPartBuilder lavaBuilder = modelBuilder.part("lava-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, lavaMaterial);
        
        int lavaSurfaceCount = 0;
        
        // Find all lava surfaces and build them by iterating over actual tiles
        for (MapTile tile : gameMap.getAllTiles()) {
            if (tile.fillType == MapTileFillType.LAVA) {
                int x = (int)(tile.x / MapTile.TILE_SIZE);
                int y = (int)(tile.y / MapTile.TILE_SIZE);
                int z = (int)(tile.z / MapTile.TILE_SIZE);
                
                if (isTopMostFillTile(x, y, z, MapTileFillType.LAVA)) {
                    buildLavaSurface(lavaBuilder, tile);
                    lavaSurfaceCount++;
                }
            }
        }
        
        Log.info("BFSVisibleMapModelBuilder", "Built " + lavaSurfaceCount + " lava surfaces for transparent rendering");
    }
    
    @Override
    public void buildFogGeometry(ModelBuilder modelBuilder, Material fogMaterial) {
        // Create a single mesh part for all fog surfaces
        modelBuilder.node();
        MeshPartBuilder fogBuilder = modelBuilder.part("fog-transparent", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, fogMaterial);
        
        int fogSurfaceCount = 0;
        
        // Find all fog surfaces and build them by iterating over actual tiles
        for (MapTile tile : gameMap.getAllTiles()) {
            if (tile.fillType == MapTileFillType.FOG) {
                int x = (int)(tile.x / MapTile.TILE_SIZE);
                int y = (int)(tile.y / MapTile.TILE_SIZE);
                int z = (int)(tile.z / MapTile.TILE_SIZE);
                
                if (isTopMostFillTile(x, y, z, MapTileFillType.FOG)) {
                    buildFogSurface(fogBuilder, tile);
                    fogSurfaceCount++;
                }
            }
        }
        
        Log.info("BFSVisibleMapModelBuilder", "Built " + fogSurfaceCount + " fog surfaces for transparent rendering");
    }
    
    @Override
    public String getStrategyDescription() {
        return String.format("BFS Visible Strategy - renders %d visible tiles (vs %d total occupied) with %d faces", 
            visibleTiles.size(), getTotalOccupiedTiles(), getTotalFacesBuilt());
    }
    
    private void findReachableAreas() {
        reachableTiles.clear();
        
        // Start BFS from all spawn points
        Queue<MapTile> queue = new ArrayDeque<>();
        
        // Get spawn tiles from hints
        ArrayList<MapHint> spawnHints = gameMap.getAllHintsOfType(curly.octo.map.hints.SpawnPointHint.class);
        
        if (!spawnHints.isEmpty()) {
            for (MapHint hint : spawnHints) {
                MapTile spawnTile = gameMap.getTile(hint.tileLookupKey);
                if (spawnTile != null && spawnTile.geometryType == MapTileGeometryType.EMPTY && !reachableTiles.contains(spawnTile)) {
                    queue.offer(spawnTile);
                    reachableTiles.add(spawnTile);
                }
            }
        } else {
            // Fallback: find first empty tile as starting point
            Log.warn("BFSVisibleMapModelBuilder", "No spawn tiles found, using first empty tile as start");
            for (MapTile tile : gameMap.getAllTiles()) {
                if (tile.geometryType == MapTileGeometryType.EMPTY) {
                    queue.offer(tile);
                    reachableTiles.add(tile);
                    break;
                }
            }
        }
        
        // BFS to find all connected empty tiles
        while (!queue.isEmpty()) {
            MapTile current = queue.poll();
            
            // Check all 6 neighbors (3D)
            int[] dx = {-1, 1, 0, 0, 0, 0};
            int[] dy = {0, 0, -1, 1, 0, 0};
            int[] dz = {0, 0, 0, 0, -1, 1};
            
            for (int i = 0; i < 6; i++) {
                int nx = (int)(current.x / MapTile.TILE_SIZE) + dx[i];
                int ny = (int)(current.y / MapTile.TILE_SIZE) + dy[i];
                int nz = (int)(current.z / MapTile.TILE_SIZE) + dz[i];
                
                MapTile neighbor = gameMap.getTile(nx, ny, nz);
                
                // If neighbor exists, is empty and not yet visited, add to reachable set
                if (neighbor != null && neighbor.geometryType == MapTileGeometryType.EMPTY && !reachableTiles.contains(neighbor)) {
                    reachableTiles.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
    }
    
    private void findVisibleTilesAndFaces() {
        visibleTiles.clear();
        visibleFaces.clear();
        
        // Check all reachable empty tiles for solid neighbors that have visible faces
        for (MapTile emptyTile : reachableTiles) {
            int tileX = (int)(emptyTile.x / MapTile.TILE_SIZE);
            int tileY = (int)(emptyTile.y / MapTile.TILE_SIZE);
            int tileZ = (int)(emptyTile.z / MapTile.TILE_SIZE);
            
            // Check all 6 neighbors of this empty tile
            int[] dx = {-1, 1, 0, 0, 0, 0};
            int[] dy = {0, 0, -1, 1, 0, 0};
            int[] dz = {0, 0, 0, 0, -1, 1};
            
            for (int i = 0; i < 6; i++) {
                int nx = tileX + dx[i];
                int ny = tileY + dy[i];
                int nz = tileZ + dz[i];
                
                MapTile neighbor = gameMap.getTile(nx, ny, nz);
                
                // If neighbor exists and is solid (not empty), it's visible and we need to mark which face is visible
                if (neighbor != null && neighbor.geometryType != MapTileGeometryType.EMPTY) {
                    visibleTiles.add(neighbor);
                    
                    // Mark which face of this solid tile is visible
                    boolean[] faces = visibleFaces.getOrDefault(neighbor, new boolean[6]);
                    
                    // Face indices: -X=0, +X=1, -Y=2, +Y=3, -Z=4, +Z=5
                    // The face that's visible is opposite to the direction we came from
                    int visibleFaceIndex = getOppositeFaceIndex(i);
                    faces[visibleFaceIndex] = true;
                    
                    visibleFaces.put(neighbor, faces);
                }
            }
        }
    }
    
    private int getOppositeFaceIndex(int directionIndex) {
        // Direction indices: -X=0, +X=1, -Y=2, +Y=3, -Z=4, +Z=5
        // Opposite faces: -X<->+X, -Y<->+Y, -Z<->+Z
        switch (directionIndex) {
            case 0: return 1; // -X -> +X
            case 1: return 0; // +X -> -X
            case 2: return 3; // -Y -> +Y
            case 3: return 2; // +Y -> -Y
            case 4: return 5; // -Z -> +Z
            case 5: return 4; // +Z -> -Z
            default: return 0;
        }
    }
    
    private void buildVisibleGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial, 
                                    Material grassMaterial, Material spawnMaterial, Material wallMaterial,
                                    Material waterMaterial) {
        
        // Create single mesh parts for all geometry (no chunking needed for simple maps)
        modelBuilder.node();
        MeshPartBuilder stoneBuilder = modelBuilder.part("stone", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, stoneMaterial);

        modelBuilder.node();
        MeshPartBuilder dirtBuilder = modelBuilder.part("dirt", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, dirtMaterial);

        modelBuilder.node();
        MeshPartBuilder grassBuilder = modelBuilder.part("grass", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, grassMaterial);

        modelBuilder.node();
        MeshPartBuilder spawnBuilder = modelBuilder.part("spawn", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, spawnMaterial);

        modelBuilder.node();
        MeshPartBuilder wallBuilder = modelBuilder.part("wall", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, wallMaterial);

        MeshPartBuilder waterBuilder = null;
        if (waterMaterial != null) {
            modelBuilder.node();
            waterBuilder = modelBuilder.part("water", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, waterMaterial);
        }

        int renderedTiles = 0;

        // Iterate over all tiles in the map (or filtered tiles for chunking)
        Collection<MapTile> tilesToProcess = (tileFilter != null) ? tileFilter : gameMap.getAllTiles();
        for (MapTile tile : tilesToProcess) {
            totalTilesProcessed++;

            // Add spawn markers (always visible)
            if (tile.isSpawnTile()) {
                Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                SphereShapeBuilder.build(spawnBuilder, spawnPosition, 2, 2, 2, 10, 10);
                totalFacesBuilt += 200; // Approximate faces for sphere
            }

            // Only add geometry for visible tiles
            if (visibleTiles.contains(tile)) {
                MeshPartBuilder builder;
                switch (tile.material) {
                    case DIRT:
                        builder = dirtBuilder;
                        break;
                    case GRASS:
                        builder = grassBuilder;
                        break;
                    case WALL:
                        builder = wallBuilder;
                        break;
                    case STONE:
                    default:
                        builder = stoneBuilder;
                        break;
                }

                buildTileGeometry(builder, tile);
                
                // Count only the visible faces for this tile
                boolean[] faces = visibleFaces.get(tile);
                int visibleFaceCount = 0;
                if (faces != null) {
                    for (boolean face : faces) {
                        if (face) visibleFaceCount++;
                    }
                }
                totalFacesBuilt += visibleFaceCount * 2; // 2 triangles per face
                
                renderedTiles++;
            }
            
            // Check for water surfaces (only if waterMaterial is provided)
            if (waterMaterial != null && tile.fillType == MapTileFillType.WATER) {
                int x = (int)(tile.x / MapTile.TILE_SIZE);
                int y = (int)(tile.y / MapTile.TILE_SIZE);
                int z = (int)(tile.z / MapTile.TILE_SIZE);
                
                if (isTopMostWaterTile(x, y, z)) {
                    buildWaterSurface(waterBuilder, tile);
                    totalFacesBuilt += 2; // 2 triangles for water surface quad
                }
            }
        }

        Log.info("BFSVisibleMapModelBuilder", "Built " + renderedTiles + "/" + totalTilesProcessed + " visible tiles");
    }
    
    private int getTotalOccupiedTiles() {
        int count = 0;
        for (MapTile tile : gameMap.getAllTiles()) {
            if (tile.geometryType != MapTileGeometryType.EMPTY) {
                count++;
            }
        }
        return count;
    }
    
    private void buildTileGeometry(MeshPartBuilder builder, MapTile tile) {
        // Build only the faces that are exposed to empty tiles to eliminate z-fighting
        boolean[] faces = visibleFaces.get(tile);
        if (faces == null) {
            return; // No visible faces, skip this tile
        }
        
        switch(tile.geometryType) {
            case HALF:
                buildCulledHalfTile(builder, tile, faces);
                break;
            case SLAT:
            case HALF_SLANT:
            case TALL_HALF_SLANT:
                buildSlant(builder, tile); // Keep existing slant logic for now
                break;
            default:
                buildCulledFullTile(builder, tile, faces);
                break;
        }
    }
    
    private void buildSlant(MeshPartBuilder meshPartBuilder, MapTile tile) {
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
        BoxShapeBuilder.build(meshPartBuilder, v000, v001, v010, v011, v100, v101, v110, v111);
    }
    
    /**
     * Build a full tile with face culling to eliminate z-fighting
     */
    private void buildCulledFullTile(MeshPartBuilder builder, MapTile tile, boolean[] visibleFaces) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = MapTile.TILE_SIZE;
        
        // Define vertices for the cube
        Vector3[] vertices = {
            new Vector3(x, y, z),           // 0: min x, min y, min z
            new Vector3(x + size, y, z),    // 1: max x, min y, min z
            new Vector3(x, y + size, z),    // 2: min x, max y, min z
            new Vector3(x + size, y + size, z), // 3: max x, max y, min z
            new Vector3(x, y, z + size),    // 4: min x, min y, max z
            new Vector3(x + size, y, z + size), // 5: max x, min y, max z
            new Vector3(x, y + size, z + size), // 6: min x, max y, max z
            new Vector3(x + size, y + size, z + size) // 7: max x, max y, max z
        };
        
        // Build only visible faces with correct vertex ordering for outward-facing normals
        // Face indices: -X=0, +X=1, -Y=2, +Y=3, -Z=4, +Z=5
        if (visibleFaces[0]) buildFace(builder, vertices[0], vertices[4], vertices[6], vertices[2]); // -X face  
        if (visibleFaces[1]) buildFace(builder, vertices[5], vertices[1], vertices[3], vertices[7]); // +X face
        if (visibleFaces[2]) buildFace(builder, vertices[4], vertices[0], vertices[1], vertices[5]); // -Y face
        if (visibleFaces[3]) buildFace(builder, vertices[2], vertices[6], vertices[7], vertices[3]); // +Y face
        if (visibleFaces[4]) buildFace(builder, vertices[1], vertices[0], vertices[2], vertices[3]); // -Z face
        if (visibleFaces[5]) buildFace(builder, vertices[4], vertices[5], vertices[7], vertices[6]); // +Z face
    }
    
    /**
     * Build a half-height tile with face culling
     */
    private void buildCulledHalfTile(MeshPartBuilder builder, MapTile tile, boolean[] visibleFaces) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = MapTile.TILE_SIZE;
        float halfHeight = size / 2f;
        
        // Define vertices for the half-height cube
        Vector3[] vertices = {
            new Vector3(x, y, z),           // 0: min x, min y, min z
            new Vector3(x + size, y, z),    // 1: max x, min y, min z
            new Vector3(x, y + halfHeight, z), // 2: min x, max y, min z
            new Vector3(x + size, y + halfHeight, z), // 3: max x, max y, min z
            new Vector3(x, y, z + size),    // 4: min x, min y, max z
            new Vector3(x + size, y, z + size), // 5: max x, min y, max z
            new Vector3(x, y + halfHeight, z + size), // 6: min x, max y, max z
            new Vector3(x + size, y + halfHeight, z + size) // 7: max x, max y, max z
        };
        
        // Build only visible faces with correct vertex ordering for outward-facing normals
        if (visibleFaces[0]) buildFace(builder, vertices[0], vertices[4], vertices[6], vertices[2]); // -X face
        if (visibleFaces[1]) buildFace(builder, vertices[5], vertices[1], vertices[3], vertices[7]); // +X face
        if (visibleFaces[2]) buildFace(builder, vertices[4], vertices[0], vertices[1], vertices[5]); // -Y face
        if (visibleFaces[3]) buildFace(builder, vertices[2], vertices[6], vertices[7], vertices[3]); // +Y face
        if (visibleFaces[4]) buildFace(builder, vertices[1], vertices[0], vertices[2], vertices[3]); // -Z face
        if (visibleFaces[5]) buildFace(builder, vertices[4], vertices[5], vertices[7], vertices[6]); // +Z face
    }
    
    /**
     * Build a single face as two triangles with proper normals and texture coordinates
     */
    private void buildFace(MeshPartBuilder builder, Vector3 v0, Vector3 v1, Vector3 v2, Vector3 v3) {
        // Calculate normal by cross product (ensure consistent winding order)
        Vector3 edge1 = new Vector3(v1).sub(v0);
        Vector3 edge2 = new Vector3(v3).sub(v0); // Use v3 instead of v2 for proper quad normal
        Vector3 normal = new Vector3(edge1).crs(edge2).nor();
        
        // Define texture coordinates
        Vector2 uv0 = new Vector2(0, 0);
        Vector2 uv1 = new Vector2(1, 0);
        Vector2 uv2 = new Vector2(1, 1);
        Vector2 uv3 = new Vector2(0, 1);
        
        // Build two triangles to form the quad with consistent winding
        // Triangle 1: v0, v1, v2 (counter-clockwise when viewed from outside)
        builder.triangle(
            builder.vertex(v0, normal, null, uv0),
            builder.vertex(v1, normal, null, uv1),
            builder.vertex(v2, normal, null, uv2)
        );
        
        // Triangle 2: v0, v2, v3 (counter-clockwise when viewed from outside)
        builder.triangle(
            builder.vertex(v0, normal, null, uv0),
            builder.vertex(v2, normal, null, uv2),
            builder.vertex(v3, normal, null, uv3)
        );
    }
    
    /**
     * Check if this water tile is the topmost water tile in its column.
     * This determines if we should render a water surface.
     */
    private boolean isTopMostWaterTile(int x, int y, int z) {
        return isTopMostFillTile(x, y, z, MapTileFillType.WATER);
    }
    
    /**
     * Check if this tile is the topmost tile of the given fill type in its column.
     * This determines if we should render a surface for this fill type.
     */
    private boolean isTopMostFillTile(int x, int y, int z, MapTileFillType fillType) {
        // Check if the tile above is not the same fill type (or doesn't exist)
        MapTile tileAbove = gameMap.getTile(x, y + 1, z);
        return tileAbove == null || tileAbove.fillType != fillType;
    }
    
    /**
     * Build a water surface plane at half the height of a water tile.
     */
    private void buildWaterSurface(MeshPartBuilder waterBuilder, MapTile tile) {
        float x = tile.x;
        float y = tile.y + MapTile.TILE_SIZE / 2f; // Surface at half tile height
        float z = tile.z;
        float size = MapTile.TILE_SIZE;
        
        // Create a horizontal quad for the water surface
        Vector3 v00 = new Vector3(x, y, z);           // Bottom-left
        Vector3 v01 = new Vector3(x, y, z + size);    // Top-left  
        Vector3 v10 = new Vector3(x + size, y, z);    // Bottom-right
        Vector3 v11 = new Vector3(x + size, y, z + size); // Top-right
        
        // Normal pointing up for water surface
        Vector3 normal = new Vector3(0, 1, 0);
        
        // Texture coordinates for the quad
        Vector2 uv00 = new Vector2(0, 0);  // Bottom-left
        Vector2 uv01 = new Vector2(0, 1);  // Top-left
        Vector2 uv10 = new Vector2(1, 0);  // Bottom-right
        Vector2 uv11 = new Vector2(1, 1);  // Top-right
        
        // Build two triangles to form a quad
        // Triangle 1: v00, v01, v10
        waterBuilder.triangle(
            waterBuilder.vertex(v00, normal, null, uv00),
            waterBuilder.vertex(v01, normal, null, uv01), 
            waterBuilder.vertex(v10, normal, null, uv10)
        );
        
        // Triangle 2: v10, v01, v11  
        waterBuilder.triangle(
            waterBuilder.vertex(v10, normal, null, uv10),
            waterBuilder.vertex(v01, normal, null, uv01),
            waterBuilder.vertex(v11, normal, null, uv11)
        );
    }
    
    /**
     * Build a lava surface plane at half the height of a lava tile.
     */
    private void buildLavaSurface(MeshPartBuilder lavaBuilder, MapTile tile) {
        float x = tile.x;
        float y = tile.y + MapTile.TILE_SIZE / 2f; // Surface at half tile height (same as water)
        float z = tile.z;
        float size = MapTile.TILE_SIZE;
        
        // Create a horizontal quad for the lava surface
        Vector3 v00 = new Vector3(x, y, z);           // Bottom-left
        Vector3 v01 = new Vector3(x, y, z + size);    // Top-left  
        Vector3 v10 = new Vector3(x + size, y, z);    // Bottom-right
        Vector3 v11 = new Vector3(x + size, y, z + size); // Top-right
        
        // Normal pointing up for lava surface
        Vector3 normal = new Vector3(0, 1, 0);
        
        // Texture coordinates for the quad
        Vector2 uv00 = new Vector2(0, 0);  // Bottom-left
        Vector2 uv01 = new Vector2(0, 1);  // Top-left
        Vector2 uv10 = new Vector2(1, 0);  // Bottom-right
        Vector2 uv11 = new Vector2(1, 1);  // Top-right
        
        // Build two triangles to form a quad
        // Triangle 1: v00, v01, v10
        lavaBuilder.triangle(
            lavaBuilder.vertex(v00, normal, null, uv00),
            lavaBuilder.vertex(v01, normal, null, uv01), 
            lavaBuilder.vertex(v10, normal, null, uv10)
        );
        
        // Triangle 2: v10, v01, v11  
        lavaBuilder.triangle(
            lavaBuilder.vertex(v10, normal, null, uv10),
            lavaBuilder.vertex(v01, normal, null, uv01),
            lavaBuilder.vertex(v11, normal, null, uv11)
        );
    }
    
    /**
     * Build a fog surface plane at quarter the height of a fog tile.
     */
    private void buildFogSurface(MeshPartBuilder fogBuilder, MapTile tile) {
        float x = tile.x;
        float y = tile.y + MapTile.TILE_SIZE / 4f; // Surface at quarter tile height
        float z = tile.z;
        float size = MapTile.TILE_SIZE;
        
        // Create a horizontal quad for the fog surface
        Vector3 v00 = new Vector3(x, y, z);           // Bottom-left
        Vector3 v01 = new Vector3(x, y, z + size);    // Top-left  
        Vector3 v10 = new Vector3(x + size, y, z);    // Bottom-right
        Vector3 v11 = new Vector3(x + size, y, z + size); // Top-right
        
        // Normal pointing up for fog surface
        Vector3 normal = new Vector3(0, 1, 0);
        
        // Texture coordinates for the quad
        Vector2 uv00 = new Vector2(0, 0);  // Bottom-left
        Vector2 uv01 = new Vector2(0, 1);  // Top-left
        Vector2 uv10 = new Vector2(1, 0);  // Bottom-right
        Vector2 uv11 = new Vector2(1, 1);  // Top-right
        
        // Build two triangles to form a quad
        // Triangle 1: v00, v01, v10
        fogBuilder.triangle(
            fogBuilder.vertex(v00, normal, null, uv00),
            fogBuilder.vertex(v01, normal, null, uv01), 
            fogBuilder.vertex(v10, normal, null, uv10)
        );
        
        // Triangle 2: v10, v01, v11  
        fogBuilder.triangle(
            fogBuilder.vertex(v10, normal, null, uv10),
            fogBuilder.vertex(v01, normal, null, uv01),
            fogBuilder.vertex(v11, normal, null, uv11)
        );
    }
}