package curly.octo.map.rendering;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileGeometryType;

/**
 * Map model builder that creates geometry for ALL non-empty tiles.
 * This is the original/current approach - simple but potentially renders many hidden faces.
 */
public class AllTilesMapModelBuilder extends MapModelBuilder {
    
    public AllTilesMapModelBuilder(GameMap gameMap) {
        super(gameMap);
    }
    
    @Override
    public void buildGeometry(ModelBuilder modelBuilder, Material stoneMaterial, Material dirtMaterial, 
                            Material grassMaterial, Material spawnMaterial, Material wallMaterial) {
        
        totalFacesBuilt = 0;
        totalTilesProcessed = 0;
        
        // Use chunk-based rendering to avoid vertex limits
        final int RENDER_CHUNK_SIZE = 16; // 16x16x16 chunks to stay under vertex limits
        
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

                    int chunkTiles = 0;

                    // Calculate chunk bounds
                    int startX = chunkX * RENDER_CHUNK_SIZE;
                    int endX = Math.min(startX + RENDER_CHUNK_SIZE, gameMap.getWidth());
                    int startY = chunkY * RENDER_CHUNK_SIZE;
                    int endY = Math.min(startY + RENDER_CHUNK_SIZE, gameMap.getHeight());
                    int startZ = chunkZ * RENDER_CHUNK_SIZE;
                    int endZ = Math.min(startZ + RENDER_CHUNK_SIZE, gameMap.getDepth());

                    // Build geometry for this chunk
                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {
                            for (int z = startZ; z < endZ; z++) {
                                MapTile tile = gameMap.getTile(x, y, z);
                                totalTilesProcessed++;

                                // Add spawn markers
                                if (tile.isSpawnTile()) {
                                    Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                                    SphereShapeBuilder.build(spawnBuilder, spawnPosition, 2, 2, 2, 10, 10);
                                    totalFacesBuilt += 200; // Approximate faces for sphere
                                }

                                // Add solid geometry for all non-empty tiles
                                if (tile.geometryType != MapTileGeometryType.EMPTY) {
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
                                    renderedTiles++;
                                    chunkTiles++;
                                    totalFacesBuilt += 12; // 6 faces * 2 triangles per face
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

        Log.info("AllTilesMapModelBuilder", "Built " + renderedTiles + "/" + totalTilesProcessed + " tiles across " + chunkCount + " render chunks");
    }
    
    @Override
    public String getStrategyDescription() {
        return String.format("All Tiles Strategy - renders all %d non-empty tiles (%d faces)", 
            getTotalTilesProcessed() - getEmptyTileCount(), getTotalFacesBuilt());
    }
    
    private long getEmptyTileCount() {
        long emptyCount = 0;
        for (int x = 0; x < gameMap.getWidth(); x++) {
            for (int y = 0; y < gameMap.getHeight(); y++) {
                for (int z = 0; z < gameMap.getDepth(); z++) {
                    if (gameMap.getTile(x, y, z).geometryType == MapTileGeometryType.EMPTY) {
                        emptyCount++;
                    }
                }
            }
        }
        return emptyCount;
    }
    
    private void buildTileGeometry(MeshPartBuilder builder, MapTile tile) {
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
        } else if (tile.geometryType == MapTileGeometryType.TALL_HALF_SLANT) {
            // TALL_HALF_SLANT is same size as normal block, just with slant on top
            // No height adjustment needed - it's a 1x1x1 block with slant geometry
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
}