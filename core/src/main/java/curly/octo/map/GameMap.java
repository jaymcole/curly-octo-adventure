package curly.octo.map;

import curly.octo.Constants;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.generators.SnailMapGenerator;
import curly.octo.map.hints.MapHint;
import curly.octo.map.physics.AllTilesPhysicsBodyBuilder;
import curly.octo.map.physics.BFSPhysicsBodyBuilder;
import curly.octo.map.physics.PhysicsBodyBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class GameMap {

    private HashMap<Long, MapTile> map;
    private HashMap<Class, HashMap<Long, ArrayList<MapHint>>> hints;

    // Collision groups
    public static final int GROUND_GROUP = 1 << 0;
    public static final int PLAYER_GROUP = 1 << 1;
    private transient Random random;

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

    // Debug rendering
    private transient boolean debugRenderingEnabled = true;

    // Triangle mesh physics optimization
    private transient btTriangleMesh triangleMesh;
    private transient btBvhTriangleMeshShape terrainShape;
    private transient btRigidBody terrainBody;

    // Performance metrics
    public transient long totalTriangleCount = 0;

    // Physics building strategy
    public enum PhysicsStrategy {
        ALL_TILES,      // Build physics for all occupied tiles (original approach)
        BFS_BOUNDARY    // Build physics only for boundary tiles reachable from spawn points
    }
    private transient PhysicsStrategy physicsStrategy = PhysicsStrategy.BFS_BOUNDARY;


    // Default constructor required for Kryo
    public GameMap() {
        // Initialize HashMaps for Kryo deserialization
        map = new HashMap<>();
        hints = new HashMap<>();
    }

    public GameMap(long seed) {
        map = new HashMap<>();
        hints = new HashMap<>();
        this.random = new Random(seed);
        generateDungeon();
        initializePhysics();
    }

    /**
     * Server-only constructor that generates the map but skips physics initialization.
     * Used for hosted servers that only need the map for network distribution.
     */
    public GameMap(long seed, boolean serverOnly) {
        map = new HashMap<>();
        hints = new HashMap<>();
        this.random = new Random(seed);
        if (serverOnly) {
            // Generate map tiles only, skip physics completely
            generateDungeonServerOnly();
            Log.info("GameMap", "Created server-only map (no physics)");
        } else {
            // Normal client initialization
            generateDungeon();
            initializePhysics();
        }
    }

    public void generateDungeon() {
        Log.info("GameMap.generateDungeon", "Generating tiles");

        SnailMapGenerator generator = new SnailMapGenerator(random, this);
        generator.generate();


        Log.info("GameMap.generateDungeon", "Done generating tiles");

        Log.info("GameMap.generateDungeon", "Generating triangle mesh for physics");
        generateTriangleMeshPhysics();
        Log.info("GameMap.generateDungeon", "Done generating triangle mesh physics");
    }

    /**
     * Server-only map generation that creates tiles and hints but skips physics.
     */
    public void generateDungeonServerOnly() {
        Log.info("GameMap.generateDungeonServerOnly", "Generating tiles (server-only)");

        SnailMapGenerator generator = new SnailMapGenerator(random, this);
        generator.generate();
    }

    public MapTile touchTile(Vector3 coordinate) {
        return touchTile((int)coordinate.x, (int)coordinate.y, (int)coordinate.z);
    }

    public MapTile touchTile(int x, int y, int z) {
        if (getTile(x, y, z) == null) {
            MapTile newBasicTile = new MapTile();
            newBasicTile.geometryType = MapTileGeometryType.EMPTY;
            newBasicTile.fillType = MapTileFillType.AIR;
            // Set world coordinates based on tile index and tile size
            newBasicTile.x = x * Constants.MAP_TILE_SIZE;
            newBasicTile.y = y * Constants.MAP_TILE_SIZE;
            newBasicTile.z = z * Constants.MAP_TILE_SIZE;
            map.put(constructKeyFromIndexCoordinates(x, y, z), newBasicTile);
            return newBasicTile;
        }
        return getTile(x, y, z);
    }

    public MapTile getTileFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)(worldX / Constants.MAP_TILE_SIZE);
        int yIndex = (int)(worldY / Constants.MAP_TILE_SIZE);
        int zIndex = (int)(worldZ / Constants.MAP_TILE_SIZE);
        return getTile(xIndex, yIndex, zIndex);
    }

    public MapTile getTile(int x, int y, int z) {
        return getTile(constructKeyFromIndexCoordinates(x,y,z));
    }

    public MapTile getTile(Long tileKey) {
        return map.getOrDefault(tileKey, null);
    }

    public ArrayList<MapTile> getAllTiles () {
        return new ArrayList<>(map.values());
    }

    public long constructKeyFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)(worldX / Constants.MAP_TILE_SIZE);
        int yIndex = (int)(worldY / Constants.MAP_TILE_SIZE);
        int zIndex = (int)(worldZ / Constants.MAP_TILE_SIZE);
        return constructKeyFromIndexCoordinates(xIndex, yIndex, zIndex);
    }

    public Long constructKeyFromIndexCoordinates(int x, int y, int z) {
        return (((long)x & 0x1FFFFF) << 42) | (((long)y & 0x1FFFFF) << 21) | ((long)z & 0x1FFFFF);
    }

    public ArrayList<MapHint> getAllHintsOfType(Class hintType) {
        ArrayList<MapHint> allHints = new ArrayList<>();
        if (hints.containsKey(hintType)) {
            for(ArrayList<MapHint> hintsOnTile : hints.get(hintType).values()) {
                allHints.addAll(hintsOnTile);
            }
        }
        return allHints;
    }

    // Physics initialization
    public void initializePhysics() {
        if (physicsInitialized) return;

        Bullet.init();
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));

        // Don't create DebugDrawer here - it needs OpenGL context
        // It will be created later when needed on the OpenGL thread

        physicsInitialized = true;
    }


    // Generate triangle mesh physics using configurable builder strategy
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

        // Create the appropriate physics builder based on strategy
        PhysicsBodyBuilder builder;
        switch (physicsStrategy) {
            case BFS_BOUNDARY:
                builder = new BFSPhysicsBodyBuilder(this);
                break;
            case ALL_TILES:
            default:
                builder = new AllTilesPhysicsBodyBuilder(this);
                break;
        }

        // Build the triangle mesh using the selected strategy
        triangleMesh = builder.buildTriangleMesh();
        totalTriangleCount = builder.getTotalTriangleCount();

        // Only create physics bodies if we have triangles
        if (totalTriangleCount > 0) {
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

            Log.info("GameMap", "Generated triangle mesh with " + totalTriangleCount + " triangles using " + builder.getStrategyDescription());
        } else {
            Log.warn("GameMap", "No triangles generated - skipping physics body creation. " + builder.getStrategyDescription());
        }
    }

    public void registerHint(MapHint hint) {
        if (!hints.containsKey(hint.getClass())) {
            hints.put(hint.getClass(), new HashMap<>());
        }
        if (!hints.get(hint.getClass()).containsKey(hint.tileLookupKey)) {
            hints.get(hint.getClass()).put(hint.tileLookupKey, new ArrayList<>());
        }
        hints.get(hint.getClass()).get(hint.tileLookupKey).add(hint);
    }

    /**
     * Set the physics building strategy.
     * @param strategy The strategy to use for building physics bodies
     */
    public void setPhysicsStrategy(PhysicsStrategy strategy) {
        this.physicsStrategy = strategy;
    }

    /**
     * Get the current physics building strategy.
     * @return The current strategy
     */
    public PhysicsStrategy getPhysicsStrategy() {
        return physicsStrategy;
    }

    /**
     * Regenerate physics with current strategy. Useful for testing different approaches.
     */
    public void regeneratePhysics() {
        generateTriangleMeshPhysics();
    }

    /**
     * Check if physics has been initialized for this map.
     * @return True if physics is initialized, false otherwise
     */
    public boolean isPhysicsInitialized() {
        return physicsInitialized;
    }

    /**
     * Initialize debug rendering for physics bodies. Must be called from OpenGL thread.
     */
    public void initializeDebugDrawer() {
        if (debugDrawer == null && physicsInitialized) {
            debugDrawer = new DebugDrawer();
            debugDrawer.setDebugMode(
                DebugDrawer.DebugDrawModes.DBG_DrawWireframe |
                DebugDrawer.DebugDrawModes.DBG_DrawContactPoints
            );
            dynamicsWorld.setDebugDrawer(debugDrawer);
            Log.info("GameMap", "Physics debug drawer initialized");
        }
    }

    /**
     * Enable or disable physics debug rendering.
     * @param enabled Whether to show physics debug wireframes
     */
    public void setDebugRenderingEnabled(boolean enabled) {
        this.debugRenderingEnabled = enabled;
        if (enabled && debugDrawer == null) {
            initializeDebugDrawer();
        }
        Log.info("GameMap", "Physics debug rendering " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if physics debug rendering is enabled.
     * @return True if debug rendering is enabled
     */
    public boolean isDebugRenderingEnabled() {
        return debugRenderingEnabled;
    }

    /**
     * Render physics debug information. Call this after your normal rendering.
     * Disables depth testing to show all wireframes, even those behind geometry.
     */
    public void renderPhysicsDebug(Camera camera) {
        if (debugRenderingEnabled && debugDrawer != null && dynamicsWorld != null) {
            // Store current depth test state
            boolean depthTestWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);

            // Disable depth testing to show all wireframes
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

            // Enable blending for better wireframe visibility
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            debugDrawer.begin(camera);
            dynamicsWorld.debugDrawWorld();
            debugDrawer.end();

            // Restore depth testing state
            if (depthTestWasEnabled) {
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
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

        btCapsuleShapeZ capsule = new btCapsuleShapeZ(radius, height);
        // Position capsule so its bottom sits on the ground, not its center
        Matrix4 transform = new Matrix4().setToTranslation(x, y + height/2f, z);

        playerGhostObject = new btPairCachingGhostObject();
        playerGhostObject.setWorldTransform(transform);
        playerGhostObject.setCollisionShape(capsule);
        playerGhostObject.setCollisionFlags(playerGhostObject.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);

        playerController = new btKinematicCharacterController(playerGhostObject, capsule, 1.0f);
        playerController.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));
        playerController.setMaxSlope((float)Math.toRadians(Constants.PHYSICS_MAX_SLOPE_DEGREES));
        playerController.setJumpSpeed(15f);
        playerController.setMaxJumpHeight(4f);
        playerController.setUseGhostSweepTest(false);

        dynamicsWorld.addCollisionObject(playerGhostObject, PLAYER_GROUP, GROUND_GROUP);
        dynamicsWorld.addAction(playerController);
        playerGhostObject.setUserPointer(0L);

    }

    public btKinematicCharacterController getPlayerController() {
        return playerController;
    }

    public void stepPhysics(float deltaTime) {
        if (dynamicsWorld != null) {
            // Use dynamic timestep with reasonable constraints
            // maxSubSteps = 10 to prevent spiral of death at very low FPS
            // fixedTimeStep = 1f/120f for smoother physics at high FPS
            dynamicsWorld.stepSimulation(deltaTime, Constants.PHYSICS_MAX_SUBSTEPS, Constants.PHYSICS_FIXED_TIME_STEP);
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
        long startTime = System.currentTimeMillis();
        Log.info("GameMap", "Starting physics disposal...");

        // Early exit if physics was never initialized
        if (!physicsInitialized) {
            Log.info("GameMap", "Physics was never initialized, skipping disposal");
            return;
        }

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

        // Mark physics as uninitialized to prevent double disposal
        physicsInitialized = false;

        long totalTime = System.currentTimeMillis() - startTime;
        Log.info("GameMap", "Physics disposal completed in " + totalTime + "ms");
    }
}
