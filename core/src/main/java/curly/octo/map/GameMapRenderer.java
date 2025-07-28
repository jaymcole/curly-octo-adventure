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
    private int maxShadowCastingLights = 4; // Start with 2, can be adjusted

    public GameMapRenderer() {
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowCastingLights);
        instances = new Array<>();
        mapLights = new Array<>();
        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (" + maxShadowCastingLights + " shadow-casting lights, 1024x1024 per face)");
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
        if (pointLights == null || pointLights.lights.size == 0) {
            Log.warn("GameMapRenderer", "No lights found, skipping shadow rendering");
            return;
        }

        Log.info("GameMapRenderer", "Rendering with " + pointLights.lights.size + " total lights in environment");
        
        // Debug: Print positions of all lights
        for (int i = 0; i < pointLights.lights.size; i++) {
            PointLight light = pointLights.lights.get(i);
            Log.info("GameMapRenderer", "Light " + i + ": position (" + light.position.x + "," + light.position.y + "," + light.position.z + 
                ") intensity " + light.intensity + " color (" + light.color.r + "," + light.color.g + "," + light.color.b + ")");
        }

        // Get the N closest lights for shadow casting
        Array<PointLight> shadowCastingLights = getClosestLights(pointLights, camera.position, maxShadowCastingLights);
        
        Log.info("GameMapRenderer", "Primary light for shadows: position (" + 
            shadowCastingLights.first().position.x + "," + shadowCastingLights.first().position.y + "," + shadowCastingLights.first().position.z + 
            ") intensity " + shadowCastingLights.first().intensity);

        if (shadowCastingLights.size > 0) {
            // Reset light index for new frame
            cubeShadowMapRenderer.resetLightIndex();

            // Generate cube shadow maps for each shadow-casting light
            for (PointLight light : shadowCastingLights) {
                cubeShadowMapRenderer.generateCubeShadowMap(instances, light);
            }

            // Render with shadows from primary light but illumination from all lights
            Vector3 ambientLight = getAmbientLight(environment);
            cubeShadowMapRenderer.renderWithMultipleCubeShadows(instances, camera, shadowCastingLights, pointLights.lights, ambientLight);
        } else {
            Log.warn("GameMapRenderer", "No lights found for shadow casting");
        }
    }

    private Array<PointLight> getClosestLights(PointLightsAttribute pointLights, Vector3 cameraPosition, int maxLights) {
        Array<PointLight> result = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return result;
        }

        // Create array of lights with distances
        Array<LightDistance> lightDistances = new Array<>();
        for (PointLight light : pointLights.lights) {
            float distance = light.position.dst(cameraPosition);
            lightDistances.add(new LightDistance(light, distance));
        }

        // Sort by distance (closest first)
        lightDistances.sort((a, b) -> Float.compare(a.distance, b.distance));

        // Take the N closest lights
        int numLights = Math.min(maxLights, lightDistances.size);
        for (int i = 0; i < numLights; i++) {
            result.add(lightDistances.get(i).light);
        }

        Log.debug("GameMapRenderer", "Selected " + result.size + " closest lights for shadow casting");

        return result;
    }

    // Helper class for sorting lights by distance
    private static class LightDistance {
        final PointLight light;
        final float distance;

        LightDistance(PointLight light, float distance) {
            this.light = light;
            this.distance = distance;
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
        // Clear previous model and lights
        dispose();
        clearMapLights();

        // Extract lights from map tiles with LightHints
        extractLightsFromMap(map);

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // Create a material for each voxel type with improved lighting
        Material stoneMaterial = new Material();
        stoneMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GRAY));
        stoneMaterial.set(new ColorAttribute(ColorAttribute.Specular, 0.2f, 0.2f, 0.2f, 1f));
        stoneMaterial.set(new FloatAttribute(FloatAttribute.Shininess, 8f));
        stoneMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        Material dirtMaterial = new Material();
        dirtMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.BROWN));
        dirtMaterial.set(new ColorAttribute(ColorAttribute.Specular, 0.1f, 0.1f, 0.1f, 1f));
        dirtMaterial.set(new FloatAttribute(FloatAttribute.Shininess, 4f));
        dirtMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        Material grassMaterial = new Material();
        grassMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GREEN));
        grassMaterial.set(new ColorAttribute(ColorAttribute.Specular, 0.1f, 0.1f, 0.1f, 1f));
        grassMaterial.set(new FloatAttribute(FloatAttribute.Shininess, 4f));
        grassMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        Material spawnMaterial = new Material();
        grassMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.LIME));
        grassMaterial.set(new ColorAttribute(ColorAttribute.Specular, 0.1f, 0.1f, 0.1f, 1f));
        grassMaterial.set(new FloatAttribute(FloatAttribute.Shininess, 4f));
        grassMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        // Create mesh parts for each material type

        // Generate cubes for each voxel
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    MapTile tile = map.getTile(x, y, z);

                    if (tile.isSpawnTile()) {
                        modelBuilder.node();
                        MeshPartBuilder meshPartBuilder = modelBuilder.part("spawn",
                            GL20.GL_TRIANGLES,
                            Usage.Position | Usage.Normal | Usage.TextureCoordinates,
                            spawnMaterial);
                        Matrix4 spawnPosition = new Matrix4().translate(new Vector3(tile.x, tile.y, tile.z));
                        SphereShapeBuilder.build(meshPartBuilder, spawnPosition, 2,2,2, 10, 10);
                    }

                    if (tile.geometryType != MapTileGeometryType.EMPTY) {
                        Material material = stoneMaterial;
                        switch (tile.material) {
                            case DIRT:
                                material = dirtMaterial;
                                break;
                            case GRASS:
                                material = grassMaterial;
                                break;
                            case STONE:
                                material = stoneMaterial;
                        }

                        modelBuilder.node();
                        MeshPartBuilder meshPartBuilder = modelBuilder.part("ground",
                            GL20.GL_TRIANGLES,
                            Usage.Position | Usage.Normal | Usage.TextureCoordinates,
                            material);

                        switch(tile.geometryType) {
                        case HALF:
                            BoxShapeBuilder.build(
                                meshPartBuilder,
                                tile.x + MapTile.TILE_SIZE / 2f,
                                tile.y + MapTile.TILE_SIZE / 4f,
                                tile.z + MapTile.TILE_SIZE / 2f,
                                MapTile.TILE_SIZE, MapTile.TILE_SIZE / 2, MapTile.TILE_SIZE
                            );
                            break;
                        case SLAT:
                        case HALF_SLANT:
                            buildSlant(meshPartBuilder, tile);
                            break;
                        default:
                            BoxShapeBuilder.build(
                                meshPartBuilder,
                                tile.x + MapTile.TILE_SIZE / 2f,
                                tile.y + MapTile.TILE_SIZE / 2f,
                                tile.z + MapTile.TILE_SIZE / 2f,
                                MapTile.TILE_SIZE, MapTile.TILE_SIZE, MapTile.TILE_SIZE
                            );
                        }

                    }

                }
            }
        }

        // Finalize the model
        model = modelBuilder.end();

        // Create a model instance for rendering
        instances.clear();
        instances.add(new ModelInstance(model));
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
