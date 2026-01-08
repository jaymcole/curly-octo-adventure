package curly.octo.common.character;

import com.badlogic.gdx.math.Vector3;
import curly.octo.common.Constants;
import curly.octo.common.NPCAuthorityManager;
import curly.octo.common.network.messages.NPCInstructionMessage;

import java.util.ArrayList;
import java.util.List;

public class NPCBrain implements ICharacterBrain {

    private GameCharacter character;
    private NPCInstructionMessage currentInstruction;
    private long instructionStartTime;
    private List<Vector3> waypointQueue = new ArrayList<>();
    private int currentWaypointIndex;
    private Vector3 targetWaypoint = new Vector3();

    @Override
    public void setCharacter(GameCharacter character) {
        this.character = character;
    }

    @Override
    public void update(float delta) {
        if (character == null) return;

        if (currentInstruction != null) {
            long elapsed = System.currentTimeMillis() - instructionStartTime;
            if (elapsed > currentInstruction.duration * 1000) {
                currentInstruction = null;
                character.setWalkDirection(new Vector3(0, 0, 0));
            } else {
                executeInstruction(delta);
            }
        }
    }

    public void setInstruction(NPCInstructionMessage instruction) {
        this.currentInstruction = instruction;
        this.instructionStartTime = System.currentTimeMillis();

        if (instruction.type == NPCInstructionMessage.InstructionType.WANDER) {
            parseWaypoints(instruction);
        }
    }

    private void parseWaypoints(NPCInstructionMessage instruction) {
        waypointQueue.clear();
        currentWaypointIndex = 0;
        int[] indices = instruction.waypointTileIndices;

        if (indices != null) {
            for (int i = 0; i < indices.length; i += 3) {
                if (i + 2 < indices.length) {
                    float x = indices[i] * Constants.MAP_TILE_SIZE + Constants.MAP_TILE_SIZE / 2f;
                    float y = indices[i + 1] * Constants.MAP_TILE_SIZE + Constants.MAP_TILE_SIZE / 2f;
                    float z = indices[i + 2] * Constants.MAP_TILE_SIZE + Constants.MAP_TILE_SIZE / 2f;
                    waypointQueue.add(new Vector3(x, y, z));
                }
            }
            if (!waypointQueue.isEmpty()) {
                targetWaypoint.set(waypointQueue.get(0));
            }
        }
    }

    private void executeInstruction(float delta) {
        if (currentInstruction.type == NPCInstructionMessage.InstructionType.WANDER) {
            updateWander(delta);
        }
    }

    private void updateWander(float delta) {
        if (waypointQueue.isEmpty()) {
            character.setWalkDirection(new Vector3(0, 0, 0));
            return;
        }

        Vector3 pos = character.getPosition();
        // Calculate distance on the XZ plane only, to ignore vertical differences
        float dx = pos.x - targetWaypoint.x;
        float dz = pos.z - targetWaypoint.z;
        float dist2 = dx * dx + dz * dz;

        if (dist2 < 0.5f * 0.5f) { // Use squared distance for comparison
            currentWaypointIndex++;
            if (currentWaypointIndex < waypointQueue.size()) {
                targetWaypoint.set(waypointQueue.get(currentWaypointIndex));
            } else {
                character.setWalkDirection(new Vector3(0, 0, 0));
                waypointQueue.clear(); // Clear the queue once path is complete
                NPCAuthorityManager.invokePathCompleteCallback(character.entityId);
                return;
            }
        }

        Vector3 dir = new Vector3(targetWaypoint).sub(pos);
        if (dir.len2() > 0.0001f) {
            dir.nor();
            character.setYaw((float) Math.toDegrees(Math.atan2(dir.x, dir.z)));
            character.setWalkDirection(dir);
        } else {
            // Already at waypoint, stop moving
            character.setWalkDirection(new Vector3(0, 0, 0));
        }
    }

    public List<Vector3> getWaypointQueue() {
        return waypointQueue;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public NPCInstructionMessage getCurrentInstruction() {
        return currentInstruction;
    }
}
