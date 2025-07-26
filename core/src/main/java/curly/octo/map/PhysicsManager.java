package curly.octo.map;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.physics.bullet.collision.btPairCachingGhostObject;
import com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController;

import java.util.ArrayList;
import java.util.List;
import curly.octo.map.enums.CardinalDirection;
import curly.octo.map.enums.MapTileGeometryType;
import com.esotericsoftware.minlog.Log;

public class PhysicsManager {
    private btCollisionConfiguration collisionConfig;
    private btDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld dynamicsWorld;

    private final List<btRigidBody> staticBodies = new ArrayList<>();
    private final List<btCollisionShape> staticShapes = new ArrayList<>();
    private btPairCachingGhostObject playerGhostObject;
    private btKinematicCharacterController playerController;
    private btRigidBody playerRigidBody; // Alternative player as rigid body

    private DebugDrawer debugDrawer;

    // Collision groups
    public static final int GROUND_GROUP = 1 << 0;
    public static final int PLAYER_GROUP = 1 << 1;

    public PhysicsManager() {
        Bullet.init();
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -30f, 0));
        debugDrawer = new DebugDrawer();
        debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_DrawWireframe);
        dynamicsWorld.setDebugDrawer(debugDrawer);
    }

    public void addStaticBlock(float x, float y, float z, float size) {
        addStaticBlock(x, y, z, size, MapTileGeometryType.FULL, CardinalDirection.NORTH);
    }

    public void addStaticBlock(float x, float y, float z, float size, MapTileGeometryType geometryType, CardinalDirection direction) {
        btCollisionShape blockShape = createCollisionShape(x, y, z, size, geometryType, direction);

        // Calculate the center position for the physics body
        Vector3 centerPos = calculatePhysicsCenter(x, y, z, size, geometryType, direction);
        Matrix4 transform = new Matrix4().setToTranslation(centerPos);

        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0, motionState, blockShape, Vector3.Zero);
        btRigidBody body = new btRigidBody(info);
        // Add static body to physics world
        // Static objects should be in ground group and collide with player group
        dynamicsWorld.addRigidBody(body, GROUND_GROUP, PLAYER_GROUP);

        // Ensure static bodies have proper collision flags for character controller interaction
        body.setCollisionFlags(body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
        staticBodies.add(body);
        staticShapes.add(blockShape);
        info.dispose(); // Dispose after use

        // Debug: Log block addition (only log first few to avoid spam)
        if (staticBodies.size() <= 5) {
            Log.info("PhysicsManager", "Added static block " + staticBodies.size() + " at: " + centerPos);
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
        // Base vertices (bottom face)
        Vector3 v000 = new Vector3(-halfSize, -halfSize, -halfSize);
        Vector3 v001 = new Vector3(-halfSize, -halfSize, halfSize);
        Vector3 v100 = new Vector3(halfSize, -halfSize, -halfSize);
        Vector3 v101 = new Vector3(halfSize, -halfSize, halfSize);
        // Top vertices (top face)
        Vector3 v010 = new Vector3(-halfSize, halfSize, -halfSize);
        Vector3 v011 = new Vector3(-halfSize, halfSize, halfSize);
        Vector3 v110 = new Vector3(halfSize, halfSize, -halfSize);
        Vector3 v111 = new Vector3(halfSize, halfSize, halfSize);
        // For half-slant, top is at y=0 (tile base), not y=halfSize
        float topY = isHalf ? -halfSize : halfSize;
        v010.y = v011.y = v110.y = v111.y = topY;
        // Apply slant by moving only the correct top vertices down to the base
        switch (direction) {
            case NORTH:
                v010.y = v000.y; // v010 = v000
                v011.y = v001.y; // v011 = v001
                break;
            case EAST:
                v110.y = v100.y; // v110 = v100
                v010.y = v000.y; // v010 = v000
                break;
            case SOUTH:
                v111.y = v101.y; // v111 = v101
                v110.y = v100.y; // v110 = v100
                break;
            case WEST:
                v111.y = v101.y; // v111 = v101
                v011.y = v001.y; // v011 = v001
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

        // Create capsule shape for the player
        btCapsuleShape capsule = new btCapsuleShape(radius, height - 2*radius);
        Matrix4 transform = new Matrix4().setToTranslation(x, y, z);

        // Create ghost object for character controller
        playerGhostObject = new btPairCachingGhostObject();
        playerGhostObject.setWorldTransform(transform);
        playerGhostObject.setCollisionShape(capsule);
        playerGhostObject.setCollisionFlags(playerGhostObject.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_CHARACTER_OBJECT);

        // Create kinematic character controller with proper step height
        // Step height should be reasonable for climbing small obstacles
        playerController = new btKinematicCharacterController(playerGhostObject, capsule, 1.0f);

        // Configure character controller for better movement
        playerController.setGravity(new Vector3(0, -30f, 0));
        playerController.setMaxSlope((float)Math.toRadians(60)); // 60 degree max slope for better climbing
        playerController.setJumpSpeed(15f);
        playerController.setMaxJumpHeight(4f);

        // Set up proper collision detection
        playerController.setUseGhostSweepTest(false); // Use convex sweep test for better collision
        //playerController.setUpAxis(1); // Y axis is up


        // Add to physics world with proper collision filtering
        // Ghost object should collide with ground objects
        dynamicsWorld.addCollisionObject(playerGhostObject, PLAYER_GROUP, GROUND_GROUP);
        dynamicsWorld.addAction(playerController);

        // IMPORTANT: Set up ghost object for proper collision detection
        playerGhostObject.setUserPointer(0L); // Clear any user data

        // Enable collision detection between ghost object and static world
        // The character controller relies on the ghost object detecting collisions

        // Debug: Log that player was added
        Log.info("PhysicsManager", "Added player CHARACTER CONTROLLER at position: " + x + ", " + y + ", " + z);
    }

    public btKinematicCharacterController getPlayerController() {
        return playerController;
    }
    public btPairCachingGhostObject getPlayerGhostObject() {
        return playerGhostObject;
    }

    public void step(float deltaTime) {
        dynamicsWorld.stepSimulation(deltaTime, 5, 1f/60f);

        // Debug: Check player ground state occasionally
        if (playerController != null && Math.random() < 0.1) {  // 1% chance per frame
            boolean onGround = playerController.onGround();
            Vector3 pos = getPlayerPosition();
            Log.debug("PhysicsManager", "Player at " + pos + ", onGround: " + onGround);
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

    public void setPlayerPosition(float x, float y, float z) {
        if (playerGhostObject != null) {
            Matrix4 transform = new Matrix4().setToTranslation(x, y, z);
            playerGhostObject.setWorldTransform(transform);
        }
    }

    public btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public btRigidBody getPlayerBody() {
        return playerRigidBody;
    }

    public DebugDrawer getDebugDrawer() {
        return debugDrawer;
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
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }


}
