package curly.octo.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Handles rendering of the VoxelMap in 3D space.
 */
public class VoxelMapRenderer implements Disposable {
    private final ModelBatch modelBatch;
    private final Environment environment;
    private final Array<ModelInstance> instances;
    private Model model;

    public VoxelMapRenderer() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new com.badlogic.gdx.graphics.g3d.environment.DirectionalLight()
            .set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        instances = new Array<>();
    }

    public void render(PerspectiveCamera camera) {
        modelBatch.begin(camera);
        modelBatch.render(instances, environment);
        modelBatch.end();
    }

    public void updateMap(VoxelMap map) {
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
        MeshPartBuilder stoneBuilder = modelBuilder.part("stone",
            GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal,
            stoneMaterial);

        MeshPartBuilder dirtBuilder = modelBuilder.part("dirt",
            GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal,
            dirtMaterial);

        MeshPartBuilder spawnBuilder = modelBuilder.part("spawn",
            GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal,
            spawnMaterial);

        // Generate cubes for each voxel
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    VoxelType type = map.getVoxel(x, y, z);
                    if (type != VoxelType.AIR) {

                        MeshPartBuilder builder;
                        switch(type) {
                            case STONE:
                                builder = stoneBuilder;
                                break;
                            case DIRT:
                                builder = stoneBuilder;
                                break;
                            case SPAWN_POINT:
                                builder = stoneBuilder;
                                break;
                            default:
                                builder = stoneBuilder;
                        }

                        if (builder != null) {
                            // Create a cube at the voxel position
                            builder.box(
                                1f, 1f, 1f,  // Dimensions
                                x + 0.5f, y + 0.5f, z + 0.5f  // Position (centered)
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

        // Center the model in the world
        for (ModelInstance instance : instances) {
            instance.transform.translate(
                -map.getWidth() / 2f,
                -map.getHeight() / 4f,  // Lower the model a bit
                -map.getDepth() / 2f
            );
        }
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
