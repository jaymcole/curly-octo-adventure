package curly.octo.common;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.character.GameCharacter;
import curly.octo.common.character.NPCBrain;
import curly.octo.common.network.messages.NPCInstructionMessage;

import java.util.List;

public class NPCObject extends GameCharacter {

    public static final float NPC_HEIGHT = 1.8f;
    public static final float NPC_WIDTH = 0.6f;

    public NPCObject() {
        super();
        setBrain(new NPCBrain());
    }

    public NPCObject(String npcId) {
        super(npcId);
        setBrain(new NPCBrain());
    }

    public NPCObject(String npcId, String modelAssetPath) {
        super(npcId, modelAssetPath);
        setBrain(new NPCBrain());
    }

    public void executeInstruction(NPCInstructionMessage instruction) {
        if (brain instanceof NPCBrain) {
            ((NPCBrain) brain).setInstruction(instruction);
        }
    }

    public List<Vector3> getWaypointQueue() {
        if (brain instanceof NPCBrain) {
            return ((NPCBrain) brain).getWaypointQueue();
        }
        return null;
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
}
