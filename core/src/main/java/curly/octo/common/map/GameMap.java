package curly.octo.common.map;

import curly.octo.common.Constants;
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
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.map.enums.MapTileGeometryType;
import curly.octo.common.map.generators.KissGenerator;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.physics.AllTilesPhysicsBodyBuilder;
import curly.octo.common.map.physics.BFSPhysicsBodyBuilder;
import curly.octo.common.map.physics.PhysicsBodyBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Handles the generation and management of a voxel-based dungeon map.
 */
public class GameMap {

    private String mapId;
    private HashMap<Long, MapTile> map;
    private HashMap<Class, HashMap<Long, ArrayList<MapHint>>> hints;

    // Collision groups
    public static final int GROUND_GROUP = 1 << 0;
    public static final int PLAYER_GROUP = 1 << 1;
    public static final int NPC_GROUP = 1 << 2;
    private transient Random random;

    private transient boolean physicsInitialized;
    private transient btDefaultCollisionConfiguration collisionConfig;
    private transient btCollisionDispatcher dispatcher;
    private transient btDbvtBroadphase broadphase;
    private transient btSequentialImpulseConstraintSolver solver;
    public transient btDiscreteDynamicsWorld dynamicsWorld;
    private transient DebugDrawer debugDrawer;

    // Debug rendering
    private transient boolean debugRenderingEnabled = true;
    private transient boolean characterOnlyDebugEnabled = true; // Toggle for character-only debug (players + NPCs, no terrain)

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

//        SnailMapGenerator generator = new SnailMapGenerator(random, this);
//        TemplateGenerator generator = new TemplateGenerator(random, this);
//        KissGenerator generator = new KissGenerator(random, this);
//        generator.generate();


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
//        TemplateGenerator generator = new TemplateGenerator(random, this);
        KissGenerator generator = new KissGenerator(random, this);
        this.mapId = String.valueOf(System.currentTimeMillis());
        generator.generate();
    }

    public MapTile touchTile(Vector3 coordinate, String templateName) {
        return touchTile((int)coordinate.x, (int)coordinate.y, (int)coordinate.z, templateName);
    }

    public MapTile touchTile(int x, int y, int z, String templateName) {
        if (getTile(x, y, z) == null) {
            MapTile newBasicTile = new MapTile();
            newBasicTile.geometryType = MapTileGeometryType.EMPTY;
            newBasicTile.fillType = MapTileFillType.AIR;
            // Set world coordinates based on tile index and tile size
            newBasicTile.x = x * Constants.MAP_TILE_SIZE;
            newBasicTile.y = y * Constants.MAP_TILE_SIZE;
            newBasicTile.z = z * Constants.MAP_TILE_SIZE;
            newBasicTile.templateName = templateName;
            map.put(constructKeyFromIndexCoordinates(x, y, z), newBasicTile);
            return newBasicTile;
        }
        return getTile(x, y, z);
    }

    public MapTile getTileFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)Math.floor(worldX / Constants.MAP_TILE_SIZE);
        int yIndex = (int)Math.floor(worldY / Constants.MAP_TILE_SIZE);
        int zIndex = (int)Math.floor(worldZ / Constants.MAP_TILE_SIZE);
        return getTile(xIndex, yIndex, zIndex);
    }

    public MapTile getTile(int x, int y, int z) {
        return getTile(constructKeyFromIndexCoordinates(x,y,z));
    }

    public MapTile getTile(Vector3 coordinates) {
        return getTile(constructKeyFromIndexCoordinates((int)coordinates.x,(int)coordinates.y,(int)coordinates.z));
    }

    public MapTile getTile(Long tileKey) {
        return map.getOrDefault(tileKey, null);
    }

    public ArrayList<MapTile> getAllTiles () {
        return new ArrayList<>(map.values());
    }

    /**
     * Check if a world position is walkable for NPCs.
     * A position is walkable if:
     * 1. The tile at that position is EMPTY (not a wall)
     * 2. The tile below has solid ground (not over a hole)
     *
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     * @return true if position is safe to walk to, false otherwise
     */
    public boolean isPositionWalkable(float worldX, float worldY, float worldZ) {
        // Check the tile at this position
        MapTile currentTile = getTileFromWorldCoordinates(worldX, worldY, worldZ);

        // Position must be in empty/air space (not inside a wall)
        if (currentTile != null && currentTile.geometryType != MapTileGeometryType.EMPTY) {
            return false; // Inside a solid block (wall)
        }

        // Check if there's ground below (not over a hole)
        MapTile groundTile = getTileFromWorldCoordinates(
            worldX,
            worldY - Constants.MAP_TILE_SIZE,
            worldZ
        );

        // Must have solid ground below (not EMPTY and not null)
        if (groundTile == null || groundTile.geometryType == MapTileGeometryType.EMPTY) {
            return false; // No ground = hole/void
        }

        return true; // Position is walkable!
    }

    public long constructKeyFromWorldCoordinates(float worldX, float worldY, float worldZ) {
        int xIndex = (int)Math.floor(worldX / Constants.MAP_TILE_SIZE);
        int yIndex = (int)Math.floor(worldY / Constants.MAP_TILE_SIZE);
        int zIndex = (int)Math.floor(worldZ / Constants.MAP_TILE_SIZE);
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

    // Static flag to ensure Bullet.init() is only called once per application
    private static boolean bulletInitialized = false;

    // Physics initialization
    public void initializePhysics() {
        if (physicsInitialized) return;

        // Only initialize Bullet once per application to prevent crashes
        if (!bulletInitialized) {
            Log.info("GameMap", "Initializing Bullet Physics (first time)");
            // Bullet.init(useRefCounting, logging)
            // First param: false = disable automatic ref counting (we manage manually)
            // Second param: false = disable GC error logging
            Bullet.init(false, false);
            bulletInitialized = true;
        } else {
            Log.info("GameMap", "Bullet Physics already initialized, skipping Bullet.init()");
        }

        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, Constants.PHYSICS_GRAVITY, 0));

        // Don't create DebugDrawer here - it needs OpenGL context
        // It will be created later when needed on the OpenGL thread

        physicsInitialized = true;
        Log.info("GameMap", "Physics initialization completed");
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

            Log.info("GameMap", "[PHYSICS_DEBUG] Adding terrain body to dynamics world");
            dynamicsWorld.addRigidBody(terrainBody, GROUND_GROUP, (short)(PLAYER_GROUP | NPC_GROUP));
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
     * Enable or disable character-only physics debug rendering.
     * When enabled, only player and NPC capsules are rendered (not terrain mesh).
     * @param enabled Whether to show only character physics wireframes
     */
    public void setPlayerOnlyDebugEnabled(boolean enabled) {
        this.characterOnlyDebugEnabled = enabled;
        Log.info("GameMap", "Character-only physics debug (players + NPCs) " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if character-only physics debug rendering is enabled.
     * @return True if character-only debug rendering is enabled
     */
    public boolean isPlayerOnlyDebugEnabled() {
        return characterOnlyDebugEnabled;
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

            // Only render player and NPC physics bodies (not terrain)
            // Temporarily remove terrain body to hide it from debug drawing
            boolean terrainWasInWorld = false;
            if (terrainBody != null) {
                dynamicsWorld.removeRigidBody(terrainBody);
                terrainWasInWorld = true;
            }

            // Draw all remaining bodies (players + NPCs only)
            dynamicsWorld.debugDrawWorld();

            // Re-add terrain body for physics simulation
            if (terrainWasInWorld && terrainBody != null) {
                dynamicsWorld.addRigidBody(terrainBody, GROUND_GROUP, (short)(PLAYER_GROUP | NPC_GROUP));
            }

            /* Commented out: World collider rendering (too many bodies, causes lag)
            // Draw all physics bodies (terrain + characters)
            dynamicsWorld.debugDrawWorld();
            */

            debugDrawer.end();

            // Restore depth testing state
            if (depthTestWasEnabled) {
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            }
        }
    }

    public void stepPhysics(float deltaTime) {
        if (dynamicsWorld != null) {
            // Use dynamic timestep with reasonable constraints
            // maxSubSteps = 10 to prevent spiral of death at very low FPS
            // fixedTimeStep = 1f/120f for smoother physics at high FPS
            dynamicsWorld.stepSimulation(deltaTime, Constants.PHYSICS_MAX_SUBSTEPS, Constants.PHYSICS_FIXED_TIME_STEP);
        }
    }

    public String getMapId() {
        return mapId;
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
