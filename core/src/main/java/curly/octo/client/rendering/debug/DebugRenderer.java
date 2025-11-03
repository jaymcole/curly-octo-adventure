package curly.octo.client.rendering.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;

/**
 * Handles debug visualization for rendering.
 * Currently supports water mesh wireframe rendering with color-coded mesh parts.
 */
public class DebugRenderer implements Disposable {

    /**
     * Represents a triangle with mesh part ID for color coding.
     */
    private static class DebugTriangle {
        final Vector3[] vertices;
        final int meshPartId;

        DebugTriangle(Vector3[] vertices, int meshPartId) {
            this.vertices = vertices;
            this.meshPartId = meshPartId;
        }
    }

    private final ShapeRenderer shapeRenderer;
    private final Array<DebugTriangle> waterTriangles;
    private int currentMeshPartId = 0;
    private boolean disposed = false;

    // Color palette for different mesh parts (distinct, bright colors)
    private static final Color[] MESH_PART_COLORS = {
        new Color(0, 1, 1, 1),      // Cyan
        new Color(1, 0, 1, 1),      // Magenta
        new Color(1, 1, 0, 1),      // Yellow
        new Color(1, 0, 0, 1),      // Red
        new Color(0, 1, 0, 1),      // Green
        new Color(0, 0, 1, 1),      // Blue
        new Color(1, 0.5f, 0, 1),   // Orange
        new Color(0.5f, 0, 1, 1),   // Purple
        new Color(1, 1, 1, 1),      // White
        new Color(1, 0.75f, 0.8f, 1) // Pink
    };

    public DebugRenderer() {
        this.shapeRenderer = new ShapeRenderer();
        this.waterTriangles = new Array<>();
    }

    /**
     * Resets debug data for a new frame.
     * Call this before collecting new debug data.
     */
    public void reset() {
        waterTriangles.clear();
        currentMeshPartId = 0;
    }

    /**
     * Collects water triangles from a mesh part for wireframe rendering.
     *
     * @param nodePart The node part containing the mesh
     * @param worldTransform The world transformation matrix
     */
    public void collectWaterTriangles(NodePart nodePart, Matrix4 worldTransform) {
        Mesh mesh = nodePart.meshPart.mesh;
        int offset = nodePart.meshPart.offset;
        int count = nodePart.meshPart.size;

        // Assign a unique ID to this mesh part
        int meshPartId = currentMeshPartId++;

        // Get vertex data
        float[] vertices = new float[mesh.getNumVertices() * mesh.getVertexSize() / 4];
        mesh.getVertices(vertices);

        int vertexSize = mesh.getVertexSize() / 4; // floats per vertex

        // Collect triangles with mesh part ID
        for (int i = offset; i < offset + count; i += 3) {
            int idx0 = i * vertexSize;
            int idx1 = (i + 1) * vertexSize;
            int idx2 = (i + 2) * vertexSize;

            // Extract and transform positions
            Vector3 v0 = new Vector3(vertices[idx0], vertices[idx0 + 1], vertices[idx0 + 2]).mul(worldTransform);
            Vector3 v1 = new Vector3(vertices[idx1], vertices[idx1 + 1], vertices[idx1 + 2]).mul(worldTransform);
            Vector3 v2 = new Vector3(vertices[idx2], vertices[idx2 + 1], vertices[idx2 + 2]).mul(worldTransform);

            waterTriangles.add(new DebugTriangle(new Vector3[]{v0, v1, v2}, meshPartId));
        }

        Log.debug("DebugRenderer", "Collected mesh part #" + meshPartId + " with " + (count / 3) + " triangles");
    }

    /**
     * Collects water triangles from all instances with transparent materials.
     * Useful for collecting all water meshes in a scene at once.
     *
     * @param instances Array of model instances
     */
    public void collectWaterTrianglesFromInstances(Array<ModelInstance> instances) {
        for (ModelInstance instance : instances) {
            for (Node node : instance.nodes) {
                for (NodePart part : node.parts) {
                    if (part.enabled && isWaterMaterial(part)) {
                        collectWaterTriangles(part, instance.transform);
                    }
                }
            }
        }
    }

    /**
     * Determines if a node part represents water material.
     * Currently checks for transparency, but could be enhanced with material names.
     */
    private boolean isWaterMaterial(NodePart part) {
        // Simple check: water is typically transparent
        // Could be enhanced by checking material ID or name
        if (part.material != null) {
            // Check for blending attribute which water usually has
            return part.material.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) != null;
        }
        return false;
    }

    /**
     * Renders water wireframes.
     * Each water mesh part gets a unique color to help identify separate parts.
     * Call this AFTER main rendering.
     *
     * @param camera The camera for projection matrix
     */
    public void renderWaterWireframes(Camera camera) {
        if (!Constants.DEBUG_WATER_WIREFRAME || waterTriangles.size == 0) {
            return;
        }

        // Disable depth test so wireframe shows on top
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Draw all collected water triangles with color based on mesh part ID
        for (DebugTriangle waterTri : waterTriangles) {
            Color color = MESH_PART_COLORS[waterTri.meshPartId % MESH_PART_COLORS.length];
            shapeRenderer.setColor(color);

            Vector3[] triangle = waterTri.vertices;
            shapeRenderer.line(triangle[0], triangle[1]);
            shapeRenderer.line(triangle[1], triangle[2]);
            shapeRenderer.line(triangle[2], triangle[0]);
        }

        shapeRenderer.end();

        // Re-enable depth test
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        Log.debug("DebugRenderer", "Drew " + waterTriangles.size + " water triangles from " + currentMeshPartId + " mesh parts");
    }

    /**
     * Renders a bounding box wireframe.
     * Useful for debugging collision bounds.
     */
    public void renderBoundingBox(Camera camera, Vector3 min, Vector3 max, Color color) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);

        // Bottom face
        shapeRenderer.line(min.x, min.y, min.z, max.x, min.y, min.z);
        shapeRenderer.line(max.x, min.y, min.z, max.x, min.y, max.z);
        shapeRenderer.line(max.x, min.y, max.z, min.x, min.y, max.z);
        shapeRenderer.line(min.x, min.y, max.z, min.x, min.y, min.z);

        // Top face
        shapeRenderer.line(min.x, max.y, min.z, max.x, max.y, min.z);
        shapeRenderer.line(max.x, max.y, min.z, max.x, max.y, max.z);
        shapeRenderer.line(max.x, max.y, max.z, min.x, max.y, max.z);
        shapeRenderer.line(min.x, max.y, max.z, min.x, max.y, min.z);

        // Vertical edges
        shapeRenderer.line(min.x, min.y, min.z, min.x, max.y, min.z);
        shapeRenderer.line(max.x, min.y, min.z, max.x, max.y, min.z);
        shapeRenderer.line(max.x, min.y, max.z, max.x, max.y, max.z);
        shapeRenderer.line(min.x, min.y, max.z, min.x, max.y, max.z);

        shapeRenderer.end();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    public int getWaterTriangleCount() {
        return waterTriangles.size;
    }

    public int getMeshPartCount() {
        return currentMeshPartId;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }

        waterTriangles.clear();
        disposed = true;
        Log.info("DebugRenderer", "Disposed debug renderer");
    }

    public boolean isDisposed() {
        return disposed;
    }
}
