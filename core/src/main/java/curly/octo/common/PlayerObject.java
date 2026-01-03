package curly.octo.common;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import curly.octo.common.character.GameCharacter;
import curly.octo.common.character.PlayerBrain;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileFillType;

public class PlayerObject extends GameCharacter {

    public static final float PLAYER_HEIGHT = Constants.PLAYER_HEIGHT;
    public static final float PLAYER_SPEED = Constants.PLAYER_MOVEMENT_SPEED;
    public static final float JUMP_FORCE = Constants.PLAYER_JUMP_FORCE;

    private transient GameMap gameMap;
    private transient btRigidBody remotePhysicsBody;
    private transient btCapsuleShape remotePhysicsShape;
    private boolean flyModeEnabled = false;

    public PlayerObject() {
        super();
        setBrain(new PlayerBrain());
    }

    public PlayerObject(String playerId) {
        super(playerId);
        setBrain(new PlayerBrain());
    }

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
            MapTile headTile = gameMap.getTileFromWorldCoordinates(position.x, position.y + PLAYER_HEIGHT, position.z);
            if (headTile != null) {
                return headTile.fillType;
            }
        }
        return MapTileFillType.AIR;
    }

    public MapTile getHeadTile() {
        if (gameMap != null && position != null) {
            return gameMap.getTileFromWorldCoordinates(position.x, position.y + PLAYER_HEIGHT, position.z);
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

    @Override
    public Vector3 getCameraPosition() {
        if (position != null) {
            return new Vector3(position).add(0, PLAYER_HEIGHT, 0);
        }
        return new Vector3(0, PLAYER_HEIGHT, 0);
    }

    @Override
    public Vector3 getCameraDirection() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        return new Vector3(
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) -Math.sin(pitchRad),
            (float) (Math.cos(pitchRad) * Math.cos(yawRad))
        ).nor();
    }

    @Override
    public void setPitch(float pitch) {
        // Clamp pitch to prevent camera flipping
        this.pitch = Math.max(-89f, Math.min(89f, pitch));
    }
}
