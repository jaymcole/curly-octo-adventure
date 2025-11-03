package curly.octo.common;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class ModelAssetManager implements Disposable {

    private final HashMap<String, ModelAsset> modelAssets = new HashMap<>();

    public static class ModelBounds {
        public final Vector3 min = new Vector3();
        public final Vector3 max = new Vector3();
        public final Vector3 center = new Vector3();
        public final Vector3 dimensions = new Vector3();
        public float groundOffset;

        public ModelBounds(Model model) {
            BoundingBox boundingBox = model.calculateBoundingBox(new BoundingBox());
            boundingBox.getMin(min);
            boundingBox.getMax(max);
            boundingBox.getCenter(center);
            boundingBox.getDimensions(dimensions);

            // Calculate how much to offset to put the model's bottom at ground level
            groundOffset = -min.y;
        }

        public Vector3 getGroundCenteredPosition(Vector3 targetPosition) {
            return new Vector3(targetPosition.x - center.x, targetPosition.y + groundOffset, targetPosition.z - center.z);
        }
    }

    private static class ModelAsset {
        final Model model;
        final AtomicInteger referenceCount;
        final PhysicsProperties physicsProperties;
        final ModelBounds bounds;

        ModelAsset(Model model, PhysicsProperties physicsProperties) {
            this.model = model;
            this.referenceCount = new AtomicInteger(0);
            this.physicsProperties = physicsProperties;
            this.bounds = new ModelBounds(model);
        }
    }

    public ModelInstance createModelInstance(String assetPath, Model model) {
        ModelAsset asset = modelAssets.get(assetPath);

        if (asset == null) {
            PhysicsProperties physicsProps = loadPhysicsProperties(assetPath);
            asset = new ModelAsset(model, physicsProps);
            modelAssets.put(assetPath, asset);
        }

        asset.referenceCount.incrementAndGet();
        return new ModelInstance(asset.model);
    }

    private PhysicsProperties loadPhysicsProperties(String assetPath) {
        try {
            String propsPath = assetPath.replaceFirst("\\.[^.]+$", ".properties");
            FileHandle propsFile = Gdx.files.internal(propsPath);

            if (!propsFile.exists()) {
                Log.debug("ModelAssetManager", "No properties file found for " + assetPath + ", using defaults");
                return PhysicsProperties.DEFAULT;
            }

            Properties props = new Properties();
            props.load(propsFile.read());

            float volumeDisplacement = Float.parseFloat(props.getProperty("volumeDisplacement", "1.0"));
            float weight = Float.parseFloat(props.getProperty("weight", "1.0"));
            boolean floats = Boolean.parseBoolean(props.getProperty("floats", "false"));

            Log.debug("ModelAssetManager", "Loaded physics properties for " + assetPath + ": " +
                     "volume=" + volumeDisplacement + ", weight=" + weight + ", floats=" + floats);

            return new PhysicsProperties(volumeDisplacement, weight, floats);

        } catch (Exception e) {
            Log.error("ModelAssetManager", "Failed to load physics properties for " + assetPath, e);
            return PhysicsProperties.DEFAULT;
        }
    }

    public void releaseModelInstance(String assetPath) {
        ModelAsset asset = modelAssets.get(assetPath);
        if (asset != null) {
            int count = asset.referenceCount.decrementAndGet();
            if (count <= 0) {
                asset.model.dispose();
                modelAssets.remove(assetPath);
            }
        }
    }

    public boolean hasModel(String assetPath) {
        return modelAssets.containsKey(assetPath);
    }

    public int getReferenceCount(String assetPath) {
        ModelAsset asset = modelAssets.get(assetPath);
        return asset != null ? asset.referenceCount.get() : 0;
    }

    public PhysicsProperties getPhysicsProperties(String assetPath) {
        ModelAsset asset = modelAssets.get(assetPath);
        return asset != null ? asset.physicsProperties : PhysicsProperties.DEFAULT;
    }

    public ModelBounds getModelBounds(String assetPath) {
        ModelAsset asset = modelAssets.get(assetPath);
        return asset != null ? asset.bounds : null;
    }

    @Override
    public void dispose() {
        for (ModelAsset asset : modelAssets.values()) {
            asset.model.dispose();
        }
        modelAssets.clear();
    }
}
