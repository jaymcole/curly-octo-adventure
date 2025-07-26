package curly.octo.map;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.MapTileGeometryType;

/**
 * Handles rendering of the VoxelMap in 3D space.
 */
public class GameMapRenderer implements Disposable {
    private final ModelBatch modelBatch;
    private final Array<ModelInstance> instances;
    private Model model;
    private boolean disposed = false;

    public GameMapRenderer() {
        modelBatch = new ModelBatch();
        instances = new Array<>();
    }

    public void render(PerspectiveCamera camera, Environment environment) {
        modelBatch.begin(camera);
        modelBatch.render(instances, environment);
        modelBatch.end();
    }

    public void updateMap(GameMap map) {
        // Clear previous model
        dispose();

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // Create a material for each voxel type with shadow support
        Material stoneMaterial = new Material();
        stoneMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GRAY));
        stoneMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        Material dirtMaterial = new Material();
        dirtMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.BROWN));
        dirtMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        Material grassMaterial = new Material();
        grassMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GREEN));
        grassMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));

        // Create mesh parts for each material type

        // Generate cubes for each voxel
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    Log.info("GameMapRenderer.updateMap", "x:" + x + " y:" + y + " z:" + z);
                    MapTile tile = map.getTile(x, y, z);
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
                            Usage.Position | Usage.Normal,
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
        if (modelBatch != null && !disposed) {
            try {
                modelBatch.dispose();
                Log.info("GameMapRenderer", "Model batch disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing model batch: " + e.getMessage());
            }
        }
    }
}
