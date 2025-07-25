package curly.octo.map;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;

import java.util.ArrayList;
import java.util.List;

public class PhysicsManager {
    private btCollisionConfiguration collisionConfig;
    private btDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld dynamicsWorld;

    private final List<btRigidBody> staticBodies = new ArrayList<>();
    private btRigidBody playerBody;
    private btCollisionShape playerShape;

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
    }

    public void addStaticBlock(float x, float y, float z, float size) {
        btCollisionShape blockShape = new btBoxShape(new Vector3(size/2f, size/2f, size/2f));
        Matrix4 transform = new Matrix4().setToTranslation(x + size/2f, y + size/2f, z + size/2f);
        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0, motionState, blockShape, Vector3.Zero);
        btRigidBody body = new btRigidBody(info);
        dynamicsWorld.addRigidBody(body, GROUND_GROUP, PLAYER_GROUP); // Only collide with player
        staticBodies.add(body);
        info.dispose(); // Dispose after use
    }

    public void addPlayer(float x, float y, float z, float radius, float height, float mass) {
        playerShape = new btCapsuleShape(radius, height - 2*radius);
        Matrix4 transform = new Matrix4().setToTranslation(x, y, z);
        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        Vector3 inertia = new Vector3();
        playerShape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(mass, motionState, playerShape, inertia);
        playerBody = new btRigidBody(info);
        dynamicsWorld.addRigidBody(playerBody, PLAYER_GROUP, GROUND_GROUP); // Only collide with ground
        info.dispose(); // Dispose after use
    }

    public void step(float deltaTime) {
        dynamicsWorld.stepSimulation(deltaTime, 5, 1f/60f);
    }

    public Vector3 getPlayerPosition() {
        Matrix4 transform = new Matrix4();
        playerBody.getMotionState().getWorldTransform(transform);
        return transform.getTranslation(new Vector3());
    }

    public void setPlayerPosition(float x, float y, float z) {
        Matrix4 transform = new Matrix4().setToTranslation(x, y, z);
        playerBody.setWorldTransform(transform);
        playerBody.setLinearVelocity(Vector3.Zero);
        playerBody.setAngularVelocity(Vector3.Zero);
    }

    public btRigidBody getPlayerBody() {
        return playerBody;
    }

    public btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public void dispose() {
        if (playerBody != null) playerBody.dispose();
        if (playerShape != null) playerShape.dispose();
        for (btRigidBody body : staticBodies) body.dispose();
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }


}
