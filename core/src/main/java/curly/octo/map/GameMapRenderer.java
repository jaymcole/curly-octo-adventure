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
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    private final CubeShadowMapRenderer cubeShadowMapRenderer;
    private final Array<ModelInstance> instances;
    private Model model;
    private boolean disposed = false;

    public GameMapRenderer() {
        // Use HIGH quality (1024x1024 per face) for good balance of quality vs performance
        // Performance impact: ~6x framebuffer memory usage compared to single shadow map
        // - LOW (256): Fastest, some jagged shadows at close angles
        // - MEDIUM (512): Good performance, adequate quality  
        // - HIGH (1024): Balanced quality/performance - recommended
        // - ULTRA (2048): Best quality, higher memory usage
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH);
        instances = new Array<>();
        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (1024x1024 per face)");
    }

    public void render(PerspectiveCamera camera, Environment environment) {
        // Get all point lights in the environment
        PointLightsAttribute pointLights = environment.get(PointLightsAttribute.class, PointLightsAttribute.Type);
        if (pointLights == null || pointLights.lights.size == 0) {
            Log.warn("GameMapRenderer", "No lights found, skipping shadow rendering");
            return;
        }

        Log.info("GameMapRenderer", "Rendering with " + pointLights.lights.size + " lights");

        // Render with multiple lights - currently optimized for one shadow-casting light
        // All lights contribute to illumination, but only closest light casts shadows (performance)
        // Future: Could extend to multiple shadow-casting lights with performance budget
        PointLight primaryLight = getClosestLight(pointLights, camera.position);
        
        if (primaryLight != null) {
            // Generate cube shadow map for the primary light
            cubeShadowMapRenderer.generateCubeShadowMap(instances, primaryLight);

            // Render scene with cube shadows from primary light
            // All lights in environment will contribute to illumination through LibGDX lighting
            Vector3 ambientLight = getAmbientLight(environment);
            cubeShadowMapRenderer.renderWithCubeShadows(instances, camera, primaryLight, ambientLight);
        } else {
            Log.warn("GameMapRenderer", "No primary light found for shadow casting");
        }
    }

    private PointLight getClosestLight(PointLightsAttribute pointLights, Vector3 cameraPosition) {
        if (pointLights == null || pointLights.lights.size == 0) {
            return null;
        }
        
        PointLight closestLight = null;
        float closestDistance = Float.MAX_VALUE;
        
        for (PointLight light : pointLights.lights) {
            float distance = light.position.dst(cameraPosition);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestLight = light;
            }
        }
        
        if (closestLight != null) {
            Log.debug("GameMapRenderer", "Using closest light at distance " + closestDistance + " from camera");
        }
        
        return closestLight;
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
        // Clear previous model
        dispose();

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
