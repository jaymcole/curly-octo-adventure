package curly.octo.map;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.MapHint;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    private final CubeShadowMapRenderer cubeShadowMapRenderer;
    private final Array<ModelInstance> instances;
    private final Array<PointLight> mapLights; // Lights generated from LightHints
    private Model model;
    private boolean disposed = false;

    // Configurable number of shadow-casting lights (performance vs quality tradeoff)
    private int maxShadowCastingLights = 8; // Reduced from 8 to 2 for better performance

    // Track light counts for debug UI
    private int lastTotalLights = 0;
    private int lastShadowLights = 0;

    public GameMapRenderer() {
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowCastingLights);
        instances = new Array<>();
        mapLights = new Array<>();
        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (" + maxShadowCastingLights + " shadow-casting lights, 512x512 per face)");
    }

    /**
     * Set the maximum number of shadow-casting lights (performance tuning)
     * @param maxLights Number of closest lights that will cast shadows (1-4 recommended)
     */
    public void setMaxShadowCastingLights(int maxLights) {
        if (maxLights < 1 || maxLights > 8) {
            Log.warn("GameMapRenderer", "Max shadow-casting lights should be between 1-8, got: " + maxLights);
            return;
        }
        this.maxShadowCastingLights = maxLights;
        Log.info("GameMapRenderer", "Set max shadow-casting lights to: " + maxLights);
    }

    public void render(PerspectiveCamera camera, Environment environment) {
        // Get all point lights in the environment
        PointLightsAttribute pointLights = environment.get(PointLightsAttribute.class, PointLightsAttribute.Type);

        // Always update light counts for debug UI (even if no lights)
        if (pointLights == null || pointLights.lights.size == 0) {
            lastTotalLights = 0;
            lastShadowLights = 0;
            Log.warn("GameMapRenderer", "No lights found, skipping shadow rendering");
            return;
        }

        // Generate shadow maps for the N most significant lights (brightest overall)
        Array<PointLight> significantLights = getMostSignificantLights(pointLights, maxShadowCastingLights);

        // Update light counts for debug UI
        lastTotalLights = pointLights.lights.size;
        lastShadowLights = significantLights.size;

        if (significantLights.size > 0) {
            // Reset light index for new frame
            cubeShadowMapRenderer.resetLightIndex();

            // Generate cube shadow maps for each significant light
            for (PointLight light : significantLights) {
                cubeShadowMapRenderer.generateCubeShadowMap(instances, light);
            }

            // Render with shadows from significant lights and illumination from all lights
            Vector3 ambientLight = getAmbientLight(environment);
            cubeShadowMapRenderer.renderWithMultipleCubeShadows(instances, camera, significantLights, pointLights.lights, ambientLight);
        } else {
            Log.warn("GameMapRenderer", "No lights found for shadow casting");
        }
    }

    private Array<PointLight> getMostSignificantLights(PointLightsAttribute pointLights, int maxLights) {
        Array<PointLight> result = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return result;
        }

        // Create array of lights with significance scores (intensity-based)
        Array<LightSignificance> lightScores = new Array<>();
        for (PointLight light : pointLights.lights) {
            // Score based on light intensity (brighter lights are more significant)
            float significance = light.intensity;
            lightScores.add(new LightSignificance(light, significance));
        }

        // Sort by significance (highest first)
        lightScores.sort((a, b) -> Float.compare(b.significance, a.significance));

        // Take the N most significant lights
        int numLights = Math.min(maxLights, lightScores.size);
        for (int i = 0; i < numLights; i++) {
            result.add(lightScores.get(i).light);
        }

        Log.debug("GameMapRenderer", "Selected " + result.size + " most significant lights for shadow casting");

        return result;
    }

    // Helper class for sorting lights by significance
    private static class LightSignificance {
        final PointLight light;
        final float significance;

        LightSignificance(PointLight light, float significance) {
            this.light = light;
            this.significance = significance;
        }
    }

    private Vector3 getAmbientLight(Environment environment) {
        // Extract ambient light color from environment
        ColorAttribute ambient = environment.get(ColorAttribute.class, ColorAttribute.AmbientLight);
        if (ambient != null) {
            return new Vector3(ambient.color.r, ambient.color.g, ambient.color.b);
        }
        return new Vector3(0.02f, 0.02f, 0.03f); // Default very low ambient
    }


    public void updateMap(GameMap map) {
        Log.info("GameMapRenderer", "Starting optimized map update for " + map.getWidth() + "x" + map.getHeight() + "x" + map.getDepth() + " map");
        long startTime = System.currentTimeMillis();

        // Clear previous model and lights
        dispose();
        clearMapLights();

        // Extract lights from map tiles with LightHints
        extractLightsFromMap(map);

        // Use batched rendering approach - create fewer, larger mesh parts
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // Create materials
        Material stoneMaterial = createMaterial(Color.GRAY, 0.2f, 8f);
        Material dirtMaterial = createMaterial(Color.BROWN, 0.1f, 4f);
        Material grassMaterial = createMaterial(Color.GREEN, 0.1f, 4f);
        Material pinkWall = createMaterial(Color.PINK, 0.1f, 4f);
        Material spawnMaterial = createMaterial(Color.LIME, 0.1f, 4f);

        // Build batched geometry for better performance
        buildBatchedGeometry(modelBuilder, map, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall);

        // Finalize the model
        model = modelBuilder.end();

        // Create a model instance for rendering
        instances.clear();
        instances.add(new ModelInstance(model));

        long endTime = System.currentTimeMillis();
        Log.info("GameMapRenderer", "Completed map update in " + (endTime - startTime) + "ms");
    }

    private Material createMaterial(Color diffuse, float specular, float shininess) {
        Material material = new Material();
        material.set(new ColorAttribute(ColorAttribute.Diffuse, diffuse));
        material.set(new ColorAttribute(ColorAttribute.Specular, specular, specular, specular, 1f));
        material.set(new FloatAttribute(FloatAttribute.Shininess, shininess));
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));
        return material;
    }

    private void buildBatchedGeometry(ModelBuilder modelBuilder, GameMap map,
                                    Material stoneMaterial, Material dirtMaterial,
                                    Material grassMaterial, Material spawnMaterial, Material wallMaterial) {

        // Use chunk-based rendering to avoid vertex limits
        final int RENDER_CHUNK_SIZE = 16; // 16x16x16 chunks to stay under vertex limits
        final int MAX_VERTICES_PER_CHUNK = 32000; // Conservative limit

        int totalTiles = 0;
        int renderedTiles = 0;
        int chunkCount = 0;

        // Calculate number of chunks needed
        int chunksX = (int) Math.ceil((double) map.getWidth() / RENDER_CHUNK_SIZE);
        int chunksY = (int) Math.ceil((double) map.getHeight() / RENDER_CHUNK_SIZE);
        int chunksZ = (int) Math.ceil((double) map.getDepth() / RENDER_CHUNK_SIZE);

        // Create chunks for rendering
        for (int chunkX = 0; chunkX < chunksX; chunkX++) {
            for (int chunkY = 0; chunkY < chunksY; chunkY++) {
                for (int chunkZ = 0; chunkZ < chunksZ; chunkZ++) {

                    // Create mesh parts for this chunk
                    String chunkId = chunkX + "_" + chunkY + "_" + chunkZ;

                    modelBuilder.node();
                    MeshPartBuilder stoneBuilder = modelBuilder.part("stone-" + chunkId, GL20.GL_TRIANGLES,
                        Usage.Position | Usage.Normal | Usage.TextureCoordinates, stoneMaterial);

                    modelBuilder.node();
                    MeshPartBuilder dirtBuilder = modelBuilder.part("dirt-" + chunkId, GL20.GL_TRIANGLES,
                        Usage.Position | Usage.Normal | Usage.TextureCoordinates, dirtMaterial);

                    modelBuilder.node();
                    MeshPartBuilder grassBuilder = modelBuilder.part("grass-" + chunkId, GL20.GL_TRIANGLES,
                        Usage.Position | Usage.Normal | Usage.TextureCoordinates, grassMaterial);

                    modelBuilder.node();
                    MeshPartBuilder spawnBuilder = modelBuilder.part("spawn-" + chunkId, GL20.GL_TRIANGLES,
                        Usage.Position | Usage.Normal | Usage.TextureCoordinates, spawnMaterial);

                    modelBuilder.node();
                    MeshPartBuilder wallBuilder = modelBuilder.part("wall-" + chunkId, GL20.GL_TRIANGLES,
                        Usage.Position | Usage.Normal | Usage.TextureCoordinates, wallMaterial);

                    int chunkTiles = 0;

                    // Calculate chunk bounds
                    int startX = chunkX * RENDER_CHUNK_SIZE;
                    int endX = Math.min(startX + RENDER_CHUNK_SIZE, map.getWidth());
                    int startY = chunkY * RENDER_CHUNK_SIZE;
                    int endY = Math.min(startY + RENDER_CHUNK_SIZE, map.getHeight());
                    int startZ = chunkZ * RENDER_CHUNK_SIZE;
                    int endZ = Math.min(startZ + RENDER_CHUNK_SIZE, map.getDepth());

                    // Build geometry for this chunk
                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {
                            for (int z = startZ; z < endZ; z++) {
                                MapTile tile = map.getTile(x, y, z);
                                totalTiles++;

                                // Add spawn markers
                                if (tile.isSpawnTile()) {
                                    Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                                    SphereShapeBuilder.build(spawnBuilder, spawnPosition, 2, 2, 2, 10, 10);
                                }

                                // Add solid geometry
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

        Log.info("GameMapRenderer", "Built " + renderedTiles + "/" + totalTiles + " tiles across " + chunkCount + " render chunks (" + RENDER_CHUNK_SIZE + "³ each)");
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
        //        1   v000    (minX, minY, minZ)
        //        2   v001    (minX, minY, maxZ)
        //        3   v010    (minX, maxY, minZ)
        //        4   v011    (minX, maxY, maxZ)
        //        5   v100    (maxX, minY, minZ)
        //        6   v101    (maxX, minY, maxZ)
        //        7   v110    (maxX, maxY, minZ)
        //        8   v111    (maxX, maxY, maxZ)

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

    private void extractLightsFromMap(GameMap map) {
        int lightCount = 0;

        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    MapTile tile = map.getTile(x, y, z);

                    // Check if this tile has any LightHints
                    for (MapHint hint : tile.getHints()) {
                        if (hint instanceof LightHint) {
                            LightHint lightHint = (LightHint) hint;

                            // Create a PointLight from the LightHint
                            PointLight mapLight = new PointLight();
                            mapLight.set(
                                lightHint.color_r, lightHint.color_g, lightHint.color_b,  // Color
                                tile.x + MapTile.TILE_SIZE / 2f,                        // X position (center of tile)
                                tile.y + MapTile.TILE_SIZE / 2f + 2f,                   // Y position (slightly above tile)
                                tile.z + MapTile.TILE_SIZE / 2f,                        // Z position (center of tile)
                                lightHint.intensity                                      // Intensity/range
                            );

                            mapLights.add(mapLight);
                            lightCount++;

                            Log.info("GameMapRenderer", "Created light from LightHint at (" + tile.x + "," + tile.y + "," + tile.z +
                                ") with intensity " + lightHint.intensity + " and color (" + lightHint.color_r + "," + lightHint.color_g + "," + lightHint.color_b + ")");
                        }
                    }
                }
            }
        }

        Log.info("GameMapRenderer", "Extracted " + lightCount + " lights from map LightHints");
    }

    private void clearMapLights() {
        mapLights.clear();
    }

    /**
     * Get the lights generated from map LightHints
     * @return Array of PointLights created from map tiles
     */
    public Array<PointLight> getMapLights() {
        return mapLights;
    }

    /**
     * Add the map lights to an environment for rendering
     * @param environment The environment to add lights to
     */
    public void addMapLightsToEnvironment(Environment environment) {
        for (PointLight light : mapLights) {
            environment.add(light);
        }
        if (mapLights.size > 0) {
            Log.info("GameMapRenderer", "Added " + mapLights.size + " map lights to environment");
        }
    }

    /**
     * Get the total number of lights rendered in the last frame
     */
    public int getLastTotalLights() {
        return lastTotalLights;
    }

    /**
     * Get the number of shadow-casting lights rendered in the last frame
     */
    public int getLastShadowLights() {
        return lastShadowLights;
    }

    @Override
    public void dispose() {
        if (disposed) {
            Log.info("GameMapRenderer", "Already disposed, skipping");
            return;
        }

        if (model != null) {
            try {
                model.dispose();
                Log.info("GameMapRenderer", "Model disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing model: " + e.getMessage());
            }
            model = null;
            instances.clear();
        }

        disposed = true;
    }

    public void disposeAll() {
        dispose();
        if (cubeShadowMapRenderer != null && !disposed) {
            try {
                cubeShadowMapRenderer.dispose();
                Log.info("GameMapRenderer", "Cube shadow map renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing cube shadow map renderer: " + e.getMessage());
            }
        }
    }
}
