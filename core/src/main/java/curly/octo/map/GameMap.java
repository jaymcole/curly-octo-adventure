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
        MapGenerator generator = new FlatRandomPathGenerator(random, 60, 10, 60);

        map = generator.generate();
        Log.info("GameMap.generateDungeon", "Done generating tiles");

        Log.info("GameMap.generateDungeon", "Loading physics bodies");
        generatePhysicsBodies();
        Log.info("GameMap.generateDungeon", "Done loading physics bodies");

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

    // Generate physics bodies for all map tiles
    private void generatePhysicsBodies() {
        if (!physicsInitialized) initializePhysics();

        int blockCount = 0;
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    MapTile tile = map[x][y][z];
                    if (tile.geometryType != MapTileGeometryType.EMPTY) {
                        addStaticBlock(tile.x, tile.y, tile.z, MapTile.TILE_SIZE, tile.geometryType, tile.direction);
                        blockCount++;
                    }
                }
            }
        }
        Log.info("GameMap", "Generated " + blockCount + " physics bodies for map tiles");
    }

    public void addStaticBlock(float x, float y, float z, float size, MapTileGeometryType geometryType, CardinalDirection direction) {
        if (!physicsInitialized) initializePhysics();

        btCollisionShape blockShape = createCollisionShape(x, y, z, size, geometryType, direction);

        Vector3 centerPos = calculatePhysicsCenter(x, y, z, size, geometryType, direction);
        Matrix4 transform = new Matrix4().setToTranslation(centerPos);

        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0, motionState, blockShape, Vector3.Zero);
        btRigidBody body = new btRigidBody(info);
        dynamicsWorld.addRigidBody(body, GROUND_GROUP, PLAYER_GROUP);

        body.setCollisionFlags(body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
        staticBodies.add(body);
        staticShapes.add(blockShape);
        info.dispose();

        if (staticBodies.size() <= 5) {
            Log.info("GameMap", "Added static block " + staticBodies.size() + " at: " + centerPos);
        }
    }

    private Vector3 calculatePhysicsCenter(float x, float y, float z, float size, MapTileGeometryType geometryType, CardinalDirection direction) {
        float centerX = x + size/2f;
        float centerZ = z + size/2f;
        float centerY;
        switch (geometryType) {
            case HALF:
            case HALF_SLANT:
                centerY = y + size/4f;
                break;
            default:
                centerY = y + size/2f;
                break;
        }
        return new Vector3(centerX, centerY, centerZ);
    }

    private btCollisionShape createCollisionShape(float x, float y, float z, float size, MapTileGeometryType geometryType, CardinalDirection direction) {
        switch (geometryType) {
            case FULL:
                return new btBoxShape(new Vector3(size/2f, size/2f, size/2f));
            case HALF:
                return new btBoxShape(new Vector3(size/2f, size/4f, size/2f));
            case SLAT:
                return createSlantShape(size, direction, false);
            case HALF_SLANT:
                return createSlantShape(size, direction, true);
            default:
                return new btBoxShape(new Vector3(size/2f, size/2f, size/2f));
        }
    }

    private btCollisionShape createSlantShape(float size, CardinalDirection direction, boolean isHalf) {
        float halfSize = size / 2f;
        Vector3 v000 = new Vector3(-halfSize, -halfSize, -halfSize);
        Vector3 v001 = new Vector3(-halfSize, -halfSize, halfSize);
        Vector3 v100 = new Vector3(halfSize, -halfSize, -halfSize);
        Vector3 v101 = new Vector3(halfSize, -halfSize, halfSize);
        Vector3 v010 = new Vector3(-halfSize, halfSize, -halfSize);
        Vector3 v011 = new Vector3(-halfSize, halfSize, halfSize);
        Vector3 v110 = new Vector3(halfSize, halfSize, -halfSize);
        Vector3 v111 = new Vector3(halfSize, halfSize, halfSize);

        // For half slant blocks, reduce the height by half the tile size (same as visual mesh)
        float topY = isHalf ? 0f : halfSize;
        v010.y = v011.y = v110.y = v111.y = topY;

        switch (direction) {
            case NORTH:
                v010.y = v000.y;
                v011.y = v001.y;
                break;
            case EAST:
                v110.y = v100.y;
                v010.y = v000.y;
                break;
            case SOUTH:
                v111.y = v101.y;
                v110.y = v100.y;
                break;
            case WEST:
                v111.y = v101.y;
                v011.y = v001.y;
                break;
        }

        btConvexHullShape hullShape = new btConvexHullShape();
        hullShape.addPoint(v000, true);
        hullShape.addPoint(v001, true);
        hullShape.addPoint(v010, true);
        hullShape.addPoint(v011, true);
        hullShape.addPoint(v100, true);
        hullShape.addPoint(v101, true);
        hullShape.addPoint(v110, true);
        hullShape.addPoint(v111, true);
        return hullShape;
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
}
