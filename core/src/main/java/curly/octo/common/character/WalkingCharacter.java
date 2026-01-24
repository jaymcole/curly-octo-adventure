package curly.octo.common.character;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.network.messages.NPCInstructionMessage;

import java.util.Collections;
import java.util.List;

/**
 * Represents a character that walks on the ground using a kinematic character controller.
 * This class consolidates the common logic from the old PlayerObject and NPCObject.
 */
public class WalkingCharacter extends GameCharacter {

    // State previously in PlayerObject
    private transient GameMap gameMap;
    private transient btRigidBody remotePhysicsBody;
    private transient btCapsuleShape remotePhysicsShape;
    private boolean flyModeEnabled = false;

    /** Scale factor for this character's model */
    private float modelScale = 1.0f;

    // No-arg constructor for Kryo
    public WalkingCharacter() {
        super();
        // Defaults are now set in the GameCharacter constructor
    }

    public WalkingCharacter(String id, float height, float width) {
        super(id, height, width);
        this.modelScale = 1.0f;
    }

    public WalkingCharacter(String id, String modelAssetPath, float height, float width) {
        super(id, modelAssetPath, height, width);
        // Automatically determine scale based on model path
        this.modelScale = determineModelScale(modelAssetPath);
    }

    // Common methods from PlayerObject
    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public void setCharacterController(com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController characterController) {
        this.characterController = characterController;
    }

    public boolean isFlyModeEnabled() {
        return flyModeEnabled;
    }

    public void toggleFlyMode() {
        flyModeEnabled = !flyModeEnabled;
    }

    public MapTileFillType getHeadTileFillType() {
        if (gameMap != null && position != null) {
            MapTile headTile = gameMap.getTileFromWorldCoordinates(position.x, position.y + characterHeight, position.z);
            if (headTile != null) {
                return headTile.fillType;
            }
        }
        return MapTileFillType.AIR;
    }

    public MapTile getHeadTile() {
        if (gameMap != null && position != null) {
            return gameMap.getTileFromWorldCoordinates(position.x, position.y + characterHeight, position.z);
        }
        return null;
    }

    public void resetPhysicsState() {
        if (characterController != null) {
            characterController.setWalkDirection(new Vector3(0, 0, 0));
        }
        externalForce.setZero();
        velocity.setZero();
    }

    public boolean isRemotePhysicsInitialized() {
        return remotePhysicsBody != null;
    }

    public void initializeRemotePhysics(GameMap map, float radius, float height) {
        Log.error("WalkingCharacter", "[MYSTERY_COLLIDER] initializeRemotePhysics CALLED FOR " + entityId);
        this.gameMap = map;
        disposeRemotePhysics(map);
        remotePhysicsShape = new btCapsuleShape(radius, height);
        com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4()
            .setToTranslation(position.x, position.y + height / 2f + radius, position.z);
        btRigidBody.btRigidBodyConstructionInfo bodyInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0, null, remotePhysicsShape, new Vector3(0, 0, 0));
        remotePhysicsBody = new btRigidBody(bodyInfo);
        bodyInfo.dispose();
        remotePhysicsBody.setCollisionFlags(
            remotePhysicsBody.getCollisionFlags() |
            com.badlogic.gdx.physics.bullet.collision.btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT
        );
        remotePhysicsBody.setWorldTransform(transform);
        map.dynamicsWorld.addRigidBody(remotePhysicsBody, GameMap.PLAYER_GROUP, (short)(GameMap.GROUND_GROUP | GameMap.PLAYER_GROUP | GameMap.NPC_GROUP));
    }

    public void disposeRemotePhysics(GameMap map) {
        if (remotePhysicsBody != null) {
            if (map != null && map.dynamicsWorld != null) {
                map.dynamicsWorld.removeRigidBody(remotePhysicsBody);
            }
            remotePhysicsBody.dispose();
            remotePhysicsBody = null;
        }
        if (remotePhysicsShape != null) {
            remotePhysicsShape.dispose();
            remotePhysicsShape = null;
        }
    }

    public void updateRemotePhysicsPosition() {
        if (remotePhysicsBody != null && remotePhysicsShape != null) {
            float height = remotePhysicsShape.getHalfHeight() * 2.0f;
            float radius = remotePhysicsShape.getRadius();
            com.badlogic.gdx.math.Matrix4 transform = new com.badlogic.gdx.math.Matrix4()
                .setToTranslation(position.x, position.y + height / 2f + radius, position.z);
            remotePhysicsBody.setWorldTransform(transform);
        }
    }

    public com.badlogic.gdx.physics.bullet.dynamics.btKinematicCharacterController getCharacterController() {
        return characterController;
    }

    // Methods from NPCObject, guarded by brain type check
    public void executeInstruction(NPCInstructionMessage instruction) {
        if (brain instanceof NPCBrain) {
            ((NPCBrain) brain).setInstruction(instruction);
        }
    }

    public List<Vector3> getWaypointQueue() {
        if (brain instanceof NPCBrain) {
            return ((NPCBrain) brain).getWaypointQueue();
        }
        return Collections.emptyList();
    }

    public int getCurrentWaypointIndex() {
        if (brain instanceof NPCBrain) {
            return ((NPCBrain) brain).getCurrentWaypointIndex();
        }
        return 0;
    }

    public NPCInstructionMessage getCurrentInstruction() {
        if (brain instanceof NPCBrain) {
            return ((NPCBrain) brain).getCurrentInstruction();
        }
        return null;
    }

    public void applySyncCorrection(Vector3 syncPosition, float syncYaw, long activeInstructionId) {
        if (position != null) {
            position.set(syncPosition);
            setYaw(syncYaw);
        }
    }

    /**
     * Determines the appropriate scale for a given model path.
     * Uses constants to allow easy future customization.
     */
    private float determineModelScale(String modelPath) {
        if (modelPath == null) return 1.0f;

        // Check against known model paths
        if (modelPath.equals(Constants.PLAYER_MODEL_PATH)) {
            return Constants.PLAYER_MODEL_SCALE;
        } else if (modelPath.equals(Constants.NPC_MODEL_PATH)) {
            return Constants.NPC_MODEL_SCALE;
        }

        // Default scale for unknown models
        return 1.0f;
    }

    @Override
    protected float getModelScale() {
        return modelScale;
    }

    /**
     * Allows manual override of model scale for special cases.
     */
    public void setModelScale(float scale) {
        this.modelScale = scale;
    }
}
