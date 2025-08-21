package curly.octo.map.physics;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileGeometryType;

/**
 * Physics body builder that creates collision triangles for ALL occupied map tiles.
 * This is the original/current approach - simple but potentially wasteful for buried voxels.
 */
public class AllTilesPhysicsBodyBuilder extends PhysicsBodyBuilder {

    public AllTilesPhysicsBodyBuilder(GameMap gameMap) {
        super(gameMap);
    }

    @Override
    public btTriangleMesh buildTriangleMesh() {
        btTriangleMesh triangleMesh = new btTriangleMesh();
        totalTriangleCount = 0;

        int totalTiles = 0;
        int solidTiles = 0;

        for(MapTile tile : gameMap.getAllTiles()) {
            totalTiles++;
            if (tile.geometryType != MapTileGeometryType.EMPTY) {
                solidTiles++;
                addTileTriangles(triangleMesh, tile);
            }
        }

        Log.info("AllTilesPhysicsBodyBuilder",
            String.format("Processed %d total tiles, %d solid tiles, built %d triangles",
                totalTiles, solidTiles, totalTriangleCount));

        return triangleMesh;
    }

    @Override
    public String getStrategyDescription() {
        return "All Tiles Strategy - builds collision for every occupied tile";
    }

    private void addTileTriangles(btTriangleMesh triangleMesh, MapTile tile) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = MapTile.TILE_SIZE;

        switch (tile.geometryType) {
            case FULL:
                addFullBlockTriangles(triangleMesh, x, y, z, size);
                break;
            case HALF:
                addHalfBlockTriangles(triangleMesh, x, y, z, size);
                break;
            case SLAT:
                addSlantTriangles(triangleMesh, x, y, z, size, tile.direction, false);
                break;
            case HALF_SLANT:
                addSlantTriangles(triangleMesh, x, y, z, size, tile.direction, true);
                break;
            case TALL_HALF_SLANT:
                addTallHalfSlantTriangles(triangleMesh, x, y, z, size, tile.direction);
                break;
        }
    }

    private void addFullBlockTriangles(btTriangleMesh triangleMesh, float x, float y, float z, float size) {
        // Define the 8 vertices of a cube
        Vector3 v000 = new Vector3(x, y, z);
        Vector3 v001 = new Vector3(x, y, z + size);
        Vector3 v010 = new Vector3(x, y + size, z);
        Vector3 v011 = new Vector3(x, y + size, z + size);
        Vector3 v100 = new Vector3(x + size, y, z);
        Vector3 v101 = new Vector3(x + size, y, z + size);
        Vector3 v110 = new Vector3(x + size, y + size, z);
        Vector3 v111 = new Vector3(x + size, y + size, z + size);

        // Add triangles for each face (2 triangles per face)
        // Bottom face (y = y)
        triangleMesh.addTriangle(v000, v100, v001);
        triangleMesh.addTriangle(v100, v101, v001);
        // Top face (y = y + size)
        triangleMesh.addTriangle(v010, v011, v110);
        triangleMesh.addTriangle(v110, v011, v111);
        // Front face (z = z)
        triangleMesh.addTriangle(v000, v010, v100);
        triangleMesh.addTriangle(v100, v010, v110);
        // Back face (z = z + size)
        triangleMesh.addTriangle(v001, v101, v011);
        triangleMesh.addTriangle(v101, v111, v011);
        // Left face (x = x)
        triangleMesh.addTriangle(v000, v001, v010);
        triangleMesh.addTriangle(v010, v001, v011);
        // Right face (x = x + size)
        triangleMesh.addTriangle(v100, v110, v101);
        triangleMesh.addTriangle(v101, v110, v111);

        totalTriangleCount += 12;
    }

    private void addHalfBlockTriangles(btTriangleMesh triangleMesh, float x, float y, float z, float size) {
        float halfHeight = size / 2f;

        // Define the 8 vertices of a half-height cube
        Vector3 v000 = new Vector3(x, y, z);
        Vector3 v001 = new Vector3(x, y, z + size);
        Vector3 v010 = new Vector3(x, y + halfHeight, z);
        Vector3 v011 = new Vector3(x, y + halfHeight, z + size);
        Vector3 v100 = new Vector3(x + size, y, z);
        Vector3 v101 = new Vector3(x + size, y, z + size);
        Vector3 v110 = new Vector3(x + size, y + halfHeight, z);
        Vector3 v111 = new Vector3(x + size, y + halfHeight, z + size);

        // Add triangles for each face (2 triangles per face)
        // Bottom face
        triangleMesh.addTriangle(v000, v100, v001);
        triangleMesh.addTriangle(v100, v101, v001);
        // Top face
        triangleMesh.addTriangle(v010, v011, v110);
        triangleMesh.addTriangle(v110, v011, v111);
        // Front face
        triangleMesh.addTriangle(v000, v010, v100);
        triangleMesh.addTriangle(v100, v010, v110);
        // Back face
        triangleMesh.addTriangle(v001, v101, v011);
        triangleMesh.addTriangle(v101, v111, v011);
        // Left face
        triangleMesh.addTriangle(v000, v001, v010);
        triangleMesh.addTriangle(v010, v001, v011);
        // Right face
        triangleMesh.addTriangle(v100, v110, v101);
        triangleMesh.addTriangle(v101, v110, v111);

        totalTriangleCount += 12;
    }

    private void addSlantTriangles(btTriangleMesh triangleMesh, float x, float y, float z, float size, CardinalDirection direction, boolean isHalf) {
        float topY = isHalf ? y + size/2f : y + size;

        // Base vertices
        Vector3 v000 = new Vector3(x, y, z);
        Vector3 v001 = new Vector3(x, y, z + size);
        Vector3 v100 = new Vector3(x + size, y, z);
        Vector3 v101 = new Vector3(x + size, y, z + size);

        // Top vertices (modified based on slant direction)
        Vector3 v010 = new Vector3(x, topY, z);
        Vector3 v011 = new Vector3(x, topY, z + size);
        Vector3 v110 = new Vector3(x + size, topY, z);
        Vector3 v111 = new Vector3(x + size, topY, z + size);

        // Apply slant by lowering specific top vertices
        switch (direction) {
            case NORTH:
                v010.y = y;
                v011.y = y;
                break;
            case EAST:
                v110.y = y;
                v010.y = y;
                break;
            case SOUTH:
                v111.y = y;
                v110.y = y;
                break;
            case WEST:
                v111.y = y;
                v011.y = y;
                break;
        }

        // Add triangles (this creates a slanted shape)
        // Bottom face
        triangleMesh.addTriangle(v000, v100, v001);
        triangleMesh.addTriangle(v100, v101, v001);

        // Side faces (some will be triangular due to slant)
        addSlantedFaceTriangles(triangleMesh, v000, v001, v010, v011, v100, v101, v110, v111);

        totalTriangleCount += 8; // Approximate
    }

    private void addTallHalfSlantTriangles(btTriangleMesh triangleMesh, float x, float y, float z, float size, CardinalDirection direction) {
        // Similar to slant but with full height base
        Vector3 v000 = new Vector3(x, y, z);
        Vector3 v001 = new Vector3(x, y, z + size);
        Vector3 v100 = new Vector3(x + size, y, z);
        Vector3 v101 = new Vector3(x + size, y, z + size);

        Vector3 v010 = new Vector3(x, y + size, z);
        Vector3 v011 = new Vector3(x, y + size, z + size);
        Vector3 v110 = new Vector3(x + size, y + size, z);
        Vector3 v111 = new Vector3(x + size, y + size, z + size);

        // Apply slant to top edge only
        switch (direction) {
            case NORTH:
                v010.y = y;
                v011.y = y;
                break;
            case EAST:
                v110.y = y;
                v010.y = y;
                break;
            case SOUTH:
                v111.y = y;
                v110.y = y;
                break;
            case WEST:
                v111.y = y;
                v011.y = y;
                break;
        }

        // Add triangles
        triangleMesh.addTriangle(v000, v100, v001);
        triangleMesh.addTriangle(v100, v101, v001);

        addSlantedFaceTriangles(triangleMesh, v000, v001, v010, v011, v100, v101, v110, v111);

        totalTriangleCount += 8;
    }

    private void addSlantedFaceTriangles(btTriangleMesh triangleMesh, Vector3 v000, Vector3 v001, Vector3 v010, Vector3 v011,
                                       Vector3 v100, Vector3 v101, Vector3 v110, Vector3 v111) {
        // Front face
        if (!v000.equals(v010) || !v100.equals(v110)) {
            triangleMesh.addTriangle(v000, v010, v100);
            if (!v010.equals(v110)) {
                triangleMesh.addTriangle(v100, v010, v110);
            }
        }

        // Back face
        if (!v001.equals(v011) || !v101.equals(v111)) {
            triangleMesh.addTriangle(v001, v101, v011);
            if (!v011.equals(v111)) {
                triangleMesh.addTriangle(v101, v111, v011);
            }
        }

        // Left face
        if (!v000.equals(v010) || !v001.equals(v011)) {
            triangleMesh.addTriangle(v000, v001, v010);
            if (!v010.equals(v011)) {
                triangleMesh.addTriangle(v010, v001, v011);
            }
        }

        // Right face
        if (!v100.equals(v110) || !v101.equals(v111)) {
            triangleMesh.addTriangle(v100, v110, v101);
            if (!v110.equals(v111)) {
                triangleMesh.addTriangle(v101, v110, v111);
            }
        }

        // Top face (if any vertices are at top height)
        if (v010.y > v000.y || v011.y > v001.y || v110.y > v100.y || v111.y > v101.y) {
            if (v010.y > v000.y && v011.y > v001.y && v110.y > v100.y && v111.y > v101.y) {
                // Full top face
                triangleMesh.addTriangle(v010, v011, v110);
                triangleMesh.addTriangle(v110, v011, v111);
            } else {
                // Partial top face - add triangles for raised vertices
                if (v010.y > v000.y && v011.y > v001.y) {
                    triangleMesh.addTriangle(v010, v011, v110.y > v100.y ? v110 : v100);
                }
                if (v110.y > v100.y && v111.y > v101.y) {
                    triangleMesh.addTriangle(v110, v111, v010.y > v000.y ? v010 : v000);
                }
            }
        }
    }
}
