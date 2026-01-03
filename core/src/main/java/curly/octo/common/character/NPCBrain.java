package curly.octo.common.character;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.network.messages.NPCInstructionMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
                Log.info("NPCBrain", "[DEBUG_NPC] Instruction expired for " + character.entityId);
                currentInstruction = null;
                character.setWalkDirection(new Vector3(0, 0, 0));
            } else {
                executeInstruction(delta);
            }
        }
    }

    public void setInstruction(NPCInstructionMessage instruction) {
        Log.info("NPCBrain", "[DEBUG_NPC] Received new instruction for " + character.entityId + ": " + instruction.type);
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
                Log.info("NPCBrain", "[DEBUG_NPC] Parsed " + waypointQueue.size() + " waypoints. First target: " + targetWaypoint);
            } else {
                Log.warn("NPCBrain", "[DEBUG_NPC] Waypoint parsing resulted in an empty queue.");
            }
        } else {
            Log.warn("NPCBrain", "[DEBUG_NPC] Received WANDER instruction with null waypoint indices.");
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
        float dist = Vector3.dst(pos.x, pos.y, pos.z, targetWaypoint.x, targetWaypoint.y, targetWaypoint.z);

        if (dist < 0.5f) {
            currentWaypointIndex++;
            if (currentWaypointIndex < waypointQueue.size()) {
                targetWaypoint.set(waypointQueue.get(currentWaypointIndex));
                Log.info("NPCBrain", "[DEBUG_NPC] " + character.entityId + " reached waypoint. New target: " + targetWaypoint);
            } else {
                Log.info("NPCBrain", "[DEBUG_NPC] " + character.entityId + " completed path.");
                character.setWalkDirection(new Vector3(0, 0, 0));
                waypointQueue.clear(); // Clear the queue once path is complete
                return;
            }
        }

        Vector3 dir = new Vector3(targetWaypoint).sub(pos).nor();
        character.setYaw((float) Math.toDegrees(Math.atan2(dir.x, dir.z)));
        character.setWalkDirection(dir);

        if(System.currentTimeMillis() % 1000 < 50) { // Log every second
            Log.info("NPCBrain", "[DEBUG_NPC] " + character.entityId + " moving towards " + targetWaypoint + ". Distance: " + dist);
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
