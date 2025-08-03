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

import java.util.*;

/**
 * Map model builder that uses BFS from spawn points to only create geometry
 * for faces that are visible to players. This potentially reduces rendered faces
 * significantly by culling hidden internal faces.
 */
public class BFSVisibleMapModelBuilder extends MapModelBuilder {
    
    private Set<MapTile> reachableTiles = new HashSet<>();
    private Set<MapTile> visibleTiles = new HashSet<>();
    private Map<MapTile, boolean[]> visibleFaces = new HashMap<>(); // Which faces of each tile are visible
    
    public BFSVisibleMapModelBuilder(GameMap gameMap) {
        super(gameMap);
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
        
        // Find all water surfaces and build them
        for (int x = 0; x < gameMap.getWidth(); x++) {
            for (int y = 0; y < gameMap.getHeight(); y++) {
                for (int z = 0; z < gameMap.getDepth(); z++) {
                    MapTile tile = gameMap.getTile(x, y, z);
                    
                    if (tile.fillType == MapTileFillType.WATER && isTopMostWaterTile(x, y, z)) {
                        buildWaterSurface(waterBuilder, tile);
                        waterSurfaceCount++;
                    }
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
        
        // Find all lava surfaces and build them (same height as water - half tile)
        for (int x = 0; x < gameMap.getWidth(); x++) {
            for (int y = 0; y < gameMap.getHeight(); y++) {
                for (int z = 0; z < gameMap.getDepth(); z++) {
                    MapTile tile = gameMap.getTile(x, y, z);
                    
                    if (tile.fillType == MapTileFillType.LAVA && isTopMostFillTile(x, y, z, MapTileFillType.LAVA)) {
                        buildLavaSurface(lavaBuilder, tile);
                        lavaSurfaceCount++;
                    }
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
        
        // Find all fog surfaces and build them (quarter tile height)
        for (int x = 0; x < gameMap.getWidth(); x++) {
            for (int y = 0; y < gameMap.getHeight(); y++) {
                for (int z = 0; z < gameMap.getDepth(); z++) {
                    MapTile tile = gameMap.getTile(x, y, z);
                    
                    if (tile.fillType == MapTileFillType.FOG && isTopMostFillTile(x, y, z, MapTileFillType.FOG)) {
                        buildFogSurface(fogBuilder, tile);
                        fogSurfaceCount++;
                    }
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
        
        if (gameMap.spawnTiles != null && !gameMap.spawnTiles.isEmpty()) {
            for (MapTile spawnTile : gameMap.spawnTiles) {
                if (spawnTile.geometryType == MapTileGeometryType.EMPTY && !reachableTiles.contains(spawnTile)) {
                    queue.offer(spawnTile);
                    reachableTiles.add(spawnTile);
                }
            }
        } else {
            // Fallback: find first empty tile as starting point
            Log.warn("BFSVisibleMapModelBuilder", "No spawn tiles found, using first empty tile as start");
            boolean foundStart = false;
            for (int x = 0; x < gameMap.getWidth() && !foundStart; x++) {
                for (int y = 0; y < gameMap.getHeight() && !foundStart; y++) {
                    for (int z = 0; z < gameMap.getDepth() && !foundStart; z++) {
                        MapTile tile = gameMap.getTile(x, y, z);
                        if (tile.geometryType == MapTileGeometryType.EMPTY) {
                            queue.offer(tile);
                            reachableTiles.add(tile);
                            foundStart = true;
                        }
                    }
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
                
                // Check bounds
                if (nx >= 0 && nx < gameMap.getWidth() && 
                    ny >= 0 && ny < gameMap.getHeight() && 
                    nz >= 0 && nz < gameMap.getDepth()) {
                    
                    MapTile neighbor = gameMap.getTile(nx, ny, nz);
                    
                    // If neighbor is empty and not yet visited, add to reachable set
                    if (neighbor.geometryType == MapTileGeometryType.EMPTY && !reachableTiles.contains(neighbor)) {
                        reachableTiles.add(neighbor);
                        queue.offer(neighbor);
                    }
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
                
                // Check bounds
                if (nx >= 0 && nx < gameMap.getWidth() && 
                    ny >= 0 && ny < gameMap.getHeight() && 
                    nz >= 0 && nz < gameMap.getDepth()) {
                    
                    MapTile neighbor = gameMap.getTile(nx, ny, nz);
                    
                    // If neighbor is solid (not empty), it's visible and we need to mark which face is visible
                    if (neighbor.geometryType != MapTileGeometryType.EMPTY) {
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
        
        // Use chunk-based rendering to avoid vertex limits
        final int RENDER_CHUNK_SIZE = 16;
        
        int renderedTiles = 0;
        int chunkCount = 0;

        // Calculate number of chunks needed
        int chunksX = (int) Math.ceil((double) gameMap.getWidth() / RENDER_CHUNK_SIZE);
        int chunksY = (int) Math.ceil((double) gameMap.getHeight() / RENDER_CHUNK_SIZE);
        int chunksZ = (int) Math.ceil((double) gameMap.getDepth() / RENDER_CHUNK_SIZE);

        // Create chunks for rendering
        for (int chunkX = 0; chunkX < chunksX; chunkX++) {
            for (int chunkY = 0; chunkY < chunksY; chunkY++) {
                for (int chunkZ = 0; chunkZ < chunksZ; chunkZ++) {

                    // Create mesh parts for this chunk
                    String chunkId = chunkX + "_" + chunkY + "_" + chunkZ;

                    modelBuilder.node();
                    MeshPartBuilder stoneBuilder = modelBuilder.part("stone-" + chunkId, GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, stoneMaterial);

                    modelBuilder.node();
                    MeshPartBuilder dirtBuilder = modelBuilder.part("dirt-" + chunkId, GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, dirtMaterial);

                    modelBuilder.node();
                    MeshPartBuilder grassBuilder = modelBuilder.part("grass-" + chunkId, GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, grassMaterial);

                    modelBuilder.node();
                    MeshPartBuilder spawnBuilder = modelBuilder.part("spawn-" + chunkId, GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, spawnMaterial);

                    modelBuilder.node();
                    MeshPartBuilder wallBuilder = modelBuilder.part("wall-" + chunkId, GL20.GL_TRIANGLES,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, wallMaterial);

                    MeshPartBuilder waterBuilder = null;
                    if (waterMaterial != null) {
                        modelBuilder.node();
                        waterBuilder = modelBuilder.part("water-" + chunkId, GL20.GL_TRIANGLES,
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, waterMaterial);
                    }

                    int chunkTiles = 0;

                    // Calculate chunk bounds
                    int startX = chunkX * RENDER_CHUNK_SIZE;
                    int endX = Math.min(startX + RENDER_CHUNK_SIZE, gameMap.getWidth());
                    int startY = chunkY * RENDER_CHUNK_SIZE;
                    int endY = Math.min(startY + RENDER_CHUNK_SIZE, gameMap.getHeight());
                    int startZ = chunkZ * RENDER_CHUNK_SIZE;
                    int endZ = Math.min(startZ + RENDER_CHUNK_SIZE, gameMap.getDepth());

                    // Build geometry for this chunk - only for visible tiles
                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {
                            for (int z = startZ; z < endZ; z++) {
                                MapTile tile = gameMap.getTile(x, y, z);
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

                                    // For now, build full tile geometry but count visible faces
                                    // TODO: Could be optimized further to only build visible faces
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
                                    chunkTiles++;
                                }
                                
                                // Check for water surfaces (only if waterMaterial is provided)
                                if (waterMaterial != null && tile.fillType == MapTileFillType.WATER && isTopMostWaterTile(x, y, z)) {
                                    buildWaterSurface(waterBuilder, tile);
                                    totalFacesBuilt += 2; // 2 triangles for water surface quad
                                    chunkTiles++;
                                }
                            }
                        }
                    }

                    if (chunkTiles > 0) {
                        chunkCount++;
                    }
                }
            }
        }

        Log.info("BFSVisibleMapModelBuilder", "Built " + renderedTiles + "/" + totalTilesProcessed + " tiles across " + chunkCount + " render chunks");
    }
    
    private int getTotalOccupiedTiles() {
        int count = 0;
        for (int x = 0; x < gameMap.getWidth(); x++) {
            for (int y = 0; y < gameMap.getHeight(); y++) {
                for (int z = 0; z < gameMap.getDepth(); z++) {
                    if (gameMap.getTile(x, y, z).geometryType != MapTileGeometryType.EMPTY) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    private void buildTileGeometry(MeshPartBuilder builder, MapTile tile) {
        // For simplicity, still building full boxes but counting only visible faces
        // Could be optimized to build only visible faces using custom geometry building
        switch(tile.geometryType) {
            case HALF:
                BoxShapeBuilder.build(
                    builder,
                    tile.x + MapTile.TILE_SIZE / 2f,
                    tile.y + MapTile.TILE_SIZE / 4f,
                    tile.z + MapTile.TILE_SIZE / 2f,
                    MapTile.TILE_SIZE, MapTile.TILE_SIZE / 2, MapTile.TILE_SIZE
                );
                break;
            case SLAT:
            case HALF_SLANT:
            case TALL_HALF_SLANT:
                buildSlant(builder, tile);
                break;
            default:
                BoxShapeBuilder.build(
                    builder,
                    tile.x + MapTile.TILE_SIZE / 2f,
                    tile.y + MapTile.TILE_SIZE / 2f,
                    tile.z + MapTile.TILE_SIZE / 2f,
                    MapTile.TILE_SIZE, MapTile.TILE_SIZE, MapTile.TILE_SIZE
                );
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
        // Check if the tile above is not the same fill type (or is outside bounds)
        if (y + 1 >= gameMap.getHeight()) {
            return true; // Top of map, definitely topmost
        }
        
        MapTile tileAbove = gameMap.getTile(x, y + 1, z);
        return tileAbove.fillType != fillType;
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