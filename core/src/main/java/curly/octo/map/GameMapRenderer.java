package curly.octo.map;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import curly.octo.map.enums.MapTileGeometryType;

/**
 * Handles rendering of the VoxelMap in 3D space.
 */
public class GameMapRenderer implements Disposable {
    private final ModelBatch modelBatch;
//    private final Environment environment;
    private final Array<ModelInstance> instances;
    private Model model;

    public GameMapRenderer() {
        modelBatch = new ModelBatch();
//        environment = new Environment();
//        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
//        environment.add(new com.badlogic.gdx.graphics.g3d.environment.DirectionalLight()
//            .set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
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

        // Create a material for each voxel type
        Material stoneMaterial = new Material();
        stoneMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GRAY));

        Material dirtMaterial = new Material();
        dirtMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.BROWN));

        Material spawnMaterial = new Material();
        spawnMaterial.set(new ColorAttribute(ColorAttribute.Diffuse, Color.GREEN));

        // Create mesh parts for each material type

        // Generate cubes for each voxel
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    MapTile tile = map.getTile(x, y, z);
                    if (tile.geometryType != MapTileGeometryType.EMPTY) {
                        modelBuilder.node();
                        MeshPartBuilder meshPartBuilder = modelBuilder.part("stone",
                            GL20.GL_TRIANGLES,
                            Usage.Position | Usage.Normal,
                            stoneMaterial);

                        switch(tile.geometryType) {
                        case HALF:
                            BoxShapeBuilder.build(meshPartBuilder, tile.x, tile.y - (MapTile.TILE_SIZE / 4.0f), tile.z, MapTile.TILE_SIZE, MapTile.TILE_SIZE / 2.0f, MapTile.TILE_SIZE);
                            break;
                        case SLAT:
                            buildSlant(meshPartBuilder, tile);
                            break;
                        default:
                            BoxShapeBuilder.build(meshPartBuilder, tile.x, tile.y, tile.z, MapTile.TILE_SIZE, MapTile.TILE_SIZE, MapTile.TILE_SIZE);
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

        // Center the model in the world
        for (ModelInstance instance : instances) {
            instance.transform.translate(
                -map.getWidth() / 2f,
                -map.getHeight() / 4f,  // Lower the model a bit
                -map.getDepth() / 2f
            );
        }
    }

    private void buildSlant(MeshPartBuilder meshPartBuilder, MapTile tile) {
        float vertexOffset = MapTile.TILE_SIZE / 2.0f;

        float minX = tile.x - vertexOffset;
        float maxX = tile.x + vertexOffset;
        float minY = tile.y - vertexOffset;
        float maxY = tile.y + vertexOffset;
        float minZ = tile.z - vertexOffset;
        float maxZ = tile.z + vertexOffset;

        Vector3 v000 = new Vector3(minX, minY, minZ);
        Vector3 v001 = new Vector3(minX, minY, maxZ);
        Vector3 v010 = new Vector3(minX, maxY, minZ);
        Vector3 v011 = new Vector3(minX, maxY, maxZ);

        Vector3 v100 = new Vector3(maxX, minY, minZ);
        Vector3 v101 = new Vector3(maxX, minY, maxZ);
        Vector3 v110 = new Vector3(maxX, maxY, minZ);
        Vector3 v111 = new Vector3(maxX, maxY, maxZ);
        //        1	v000	(minX, minY, minZ)
        //        2	v001	(minX, minY, maxZ)
        //        3	v010	(minX, maxY, minZ)
        //        4	v011	(minX, maxY, maxZ)
        //        5	v100	(maxX, minY, minZ)
        //        6	v101	(maxX, minY, maxZ)
        //        7	v110	(maxX, maxY, minZ)
        //        8	v111	(maxX, maxY, maxZ)

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
        if (model != null) {
            model.dispose();
            instances.clear();
        }
    }

    public void disposeAll() {
        dispose();
        modelBatch.dispose();
    }
}
