package curly.octo.map;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.generators.FlatRandomPathGenerator;
import curly.octo.map.generators.MapGenerator;
import curly.octo.map.hints.MapHint;
import curly.octo.map.hints.SpawnPointHint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class GameMap {
    // Collision groups
    public static final int GROUND_GROUP = 1 << 0;
    public static final int PLAYER_GROUP = 1 << 1;
    private MapTile[][][] map;
    private transient Random random;

    public int getWidth() { return map.length; }
    public int getHeight() { return map[0].length; }
    public int getDepth() { return map[0][0].length; }

    public transient ArrayList<MapTile> spawnTiles;

    private transient boolean physicsInitialized;
    private transient btDefaultCollisionConfiguration collisionConfig;
    private transient btCollisionDispatcher dispatcher;
    private transient btDbvtBroadphase broadphase;
    private transient btSequentialImpulseConstraintSolver solver;
    private transient btDiscreteDynamicsWorld dynamicsWorld;
    private transient DebugDrawer debugDrawer;
    private transient final List<btRigidBody> staticBodies = new ArrayList<>();
    private transient final List<btCollisionShape> staticShapes = new ArrayList<>();
    private transient btPairCachingGhostObject playerGhostObject;
    private transient btKinematicCharacterController playerController;
    private transient btRigidBody playerRigidBody;

    // Triangle mesh physics optimization
    private transient btTriangleMesh triangleMesh;
    private transient btBvhTriangleMeshShape terrainShape;
    private transient btRigidBody terrainBody;

    // Performance metrics
    private transient long totalTriangleCount = 0;


    // Default constructor required for Kryo
    public GameMap() {
        // Initialize with minimum size, will be replaced by deserialization
        this(1, 1, 1, 0);
    }

    public GameMap(int width, int height, int depth, long seed) {
        this.random = new Random(seed);
        generateDungeon(width, height, depth);
        initializePhysics();
    }

    public void generateDungeon(int width, int height, int depth) {
        Log.info("GameMap.generateDungeon", "Generating tiles");
        MapGenerator generator = new FlatRandomPathGenerator(random, 100, 10, 100);

        map = generator.generate();
        Log.info("GameMap.generateDungeon", "Done generating tiles");

        Log.info("GameMap.generateDungeon", "Generating triangle mesh for physics");
        generateTriangleMeshPhysics();
        Log.info("GameMap.generateDungeon", "Done generating triangle mesh physics");

        Log.info("GameMap.generateDungeon", "Loading hints");
        cacheMapHints();
        Log.info("GameMap.generateDungeon", "Done loading hints");
    }

    public void cacheMapHints() {
        spawnTiles = new ArrayList<>();

        for(int x  = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for(int z = 0; z < getDepth(); z++) {
                    MapTile tile = map[x][y][z];
                    for (MapHint hint : tile.getHints()) {
                        if (hint instanceof SpawnPointHint) {
                            spawnTiles.add(tile);
                        }
                    }
                }
            }
        }
    }

    public MapTile getTileFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)(worldX / MapTile.TILE_SIZE);
        int yIndex = (int)(worldY / MapTile.TILE_SIZE);
        int zIndex = (int)(worldZ / MapTile.TILE_SIZE);

        if (xIndex < 0 || xIndex >= getWidth()) {
          return null;
        } else if (yIndex < 0 || yIndex >= getHeight()) {
            return null;
        } else if (zIndex < 0 || zIndex >= getDepth()) {
            return null;
        }
        return map[xIndex][yIndex][zIndex];
    }

    public MapTile getTile(int x, int y, int z) {
        return map[x][y][z];
    }

    // Physics initialization
    private void initializePhysics() {
        if (physicsInitialized) return;

        Bullet.init();
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -50f, 0));

        // Don't create DebugDrawer here - it needs OpenGL context
        // It will be created later when needed on the OpenGL thread

        physicsInitialized = true;
    }


    // Generate triangle mesh physics for the entire map
    private void generateTriangleMeshPhysics() {
        if (!physicsInitialized) initializePhysics();

        // Clean up existing triangle mesh
        if (terrainBody != null) {
            dynamicsWorld.removeRigidBody(terrainBody);
            terrainBody.dispose();
            terrainBody = null;
        }
        if (terrainShape != null) {
            terrainShape.dispose();
            terrainShape = null;
        }
        if (triangleMesh != null) {
            triangleMesh.dispose();
            triangleMesh = null;
        }

        triangleMesh = new btTriangleMesh();
        totalTriangleCount = 0;

        // Generate triangles for all non-empty tiles
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    MapTile tile = map[x][y][z];
                    if (tile.geometryType != MapTileGeometryType.EMPTY) {
                        addTileTriangles(tile);
                    }
                }
            }
        }

        // Create the collision shape from the triangle mesh
        terrainShape = new btBvhTriangleMeshShape(triangleMesh, true);

        // Create the rigid body
        Matrix4 transform = new Matrix4().idt();
        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0, motionState, terrainShape, Vector3.Zero);
        terrainBody = new btRigidBody(info);
        terrainBody.setCollisionFlags(terrainBody.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);

        dynamicsWorld.addRigidBody(terrainBody, GROUND_GROUP, PLAYER_GROUP);
        info.dispose();

        Log.info("GameMap", "Generated triangle mesh with " + totalTriangleCount + " triangles");
    }

    private void addTileTriangles(MapTile tile) {
        float x = tile.x;
        float y = tile.y;
        float z = tile.z;
        float size = MapTile.TILE_SIZE;

        switch (tile.geometryType) {
            case FULL:
                addFullBlockTriangles(x, y, z, size);
                break;
            case HALF:
                addHalfBlockTriangles(x, y, z, size);
                break;
            case SLAT:
                addSlantTriangles(x, y, z, size, tile.direction, false);
                break;
            case HALF_SLANT:
                addSlantTriangles(x, y, z, size, tile.direction, true);
                break;
            case TALL_HALF_SLANT:
                addTallHalfSlantTriangles(x, y, z, size, tile.direction);
                break;
        }
    }

    private void addFullBlockTriangles(float x, float y, float z, float size) {
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

    private void addHalfBlockTriangles(float x, float y, float z, float size) {
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

    private void addSlantTriangles(float x, float y, float z, float size, CardinalDirection direction, boolean isHalf) {
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
        addSlantedFaceTriangles(v000, v001, v010, v011, v100, v101, v110, v111);

        totalTriangleCount += 8; // Approximate
    }

    private void addTallHalfSlantTriangles(float x, float y, float z, float size, CardinalDirection direction) {
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

        addSlantedFaceTriangles(v000, v001, v010, v011, v100, v101, v110, v111);

        totalTriangleCount += 8;
    }

    private void addSlantedFaceTriangles(Vector3 v000, Vector3 v001, Vector3 v010, Vector3 v011,
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


    public void addPlayer(float x, float y, float z, float radius, float height, float mass) {
        if (!physicsInitialized) initializePhysics();

        // Remove old player if exists
        if (playerController != null) {
            dynamicsWorld.removeAction(playerController);
            playerController.dispose();
            playerController = null;
        }
        if (playerGhostObject != null) {
            dynamicsWorld.removeCollisionObject(playerGhostObject);
            playerGhostObject.dispose();
            playerGhostObject = null;
        }
        if (playerRigidBody != null) {
            dynamicsWorld.removeRigidBody(playerRigidBody);
            playerRigidBody.dispose();
            playerRigidBody = null;
        }

        btCapsuleShape capsule = new btCapsuleShape(radius, height - 2*radius);
        Matrix4 transform = new Matrix4().setToTranslation(x, y, z);

        playerGhostObject = new btPairCachingGhostObject();
        playerGhostObject.setWorldTransform(transform);
        playerGhostObject.setCollisionShape(capsule);
        playerGhostObject.setCollisionFlags(playerGhostObject.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);

        playerController = new btKinematicCharacterController(playerGhostObject, capsule, 1.0f);
        playerController.setGravity(new Vector3(0, -50f, 0));
        playerController.setMaxSlope((float)Math.toRadians(60));
        playerController.setJumpSpeed(15f);
        playerController.setMaxJumpHeight(4f);
        playerController.setUseGhostSweepTest(false);

        dynamicsWorld.addCollisionObject(playerGhostObject, PLAYER_GROUP, GROUND_GROUP);
        dynamicsWorld.addAction(playerController);
        playerGhostObject.setUserPointer(0L);

        Log.info("GameMap", "Added player CHARACTER CONTROLLER at position: " + x + ", " + y + ", " + z);
    }

    public btKinematicCharacterController getPlayerController() {
        return playerController;
    }

    public void stepPhysics(float deltaTime) {
        if (dynamicsWorld != null) {
            dynamicsWorld.stepSimulation(deltaTime, 5, 1f/60f);
        }
    }

    public Vector3 getPlayerPosition() {
        if (playerGhostObject != null) {
            Matrix4 transform = new Matrix4();
            playerGhostObject.getWorldTransform(transform);
            return transform.getTranslation(new Vector3());
        }
        return new Vector3();
    }

    public void dispose() {
        if (debugDrawer != null) {
            debugDrawer.dispose();
            debugDrawer = null;
        }
        if (playerController != null) {
            dynamicsWorld.removeAction(playerController);
            playerController.dispose();
            playerController = null;
        }
        if (playerGhostObject != null) {
            dynamicsWorld.removeCollisionObject(playerGhostObject);
            playerGhostObject.dispose();
            playerGhostObject = null;
        }
        if (playerRigidBody != null) {
            dynamicsWorld.removeRigidBody(playerRigidBody);
            playerRigidBody.dispose();
            playerRigidBody = null;
        }
        // Dispose triangle mesh physics
        if (terrainBody != null) {
            dynamicsWorld.removeRigidBody(terrainBody);
            terrainBody.dispose();
            terrainBody = null;
        }
        if (terrainShape != null) {
            terrainShape.dispose();
            terrainShape = null;
        }
        if (triangleMesh != null) {
            triangleMesh.dispose();
            triangleMesh = null;
        }

        for (btRigidBody body : staticBodies) {
            dynamicsWorld.removeRigidBody(body);
            body.dispose();
        }
        staticBodies.clear();
        for (btCollisionShape shape : staticShapes) {
            shape.dispose();
        }
        staticShapes.clear();
        if (dynamicsWorld != null) {
            dynamicsWorld.dispose();
        }
        if (solver != null) {
            solver.dispose();
        }
        if (broadphase != null) {
            broadphase.dispose();
        }
        if (dispatcher != null) {
            dispatcher.dispose();
        }
        if (collisionConfig != null) {
            collisionConfig.dispose();
        }
    }

    public void logPerformanceMetrics() {
        Log.info("GameMap Performance",
            String.format("Triangle Mesh: %d triangles | Single collision body for entire terrain",
                totalTriangleCount));
    }
}
