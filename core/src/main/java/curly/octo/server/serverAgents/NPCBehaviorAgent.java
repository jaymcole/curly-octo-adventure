package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.NPCObject;
import curly.octo.common.WorldObject;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.NPCInstructionMessage;
import curly.octo.server.ServerGameObjectManager;

import java.util.Random;

/**
 * Server-side agent that generates and broadcasts NPC behavior instructions.
 * Clients execute these instructions deterministically.
 */
public class NPCBehaviorAgent extends BaseAgent {

    private final Random random;
    private float instructionTimer = 0f;
    private static final float INSTRUCTION_INTERVAL = 10.0f; // Send new instructions every 10 seconds

    public NPCBehaviorAgent(ServerGameObjectManager objectManager) {
        super(objectManager);
        this.random = new Random();
    }

    @Override
    public void update(float deltaTime) {
        instructionTimer += deltaTime;

        // Periodically send new instructions to NPCs
        if (instructionTimer >= INSTRUCTION_INTERVAL) {
            instructionTimer = 0f;
            generateInstructions();
        }
    }

    /**
     * Generate and broadcast instructions for all NPCs.
     */
    private void generateInstructions() {
        for (WorldObject obj : objectManager.getNPCs()) {
            if (obj instanceof NPCObject) {
                NPCObject npc = (NPCObject) obj;
                generateInstructionForNPC(npc);
            }
        }
    }

    /**
     * Generate a random instruction for a specific NPC.
     */
    private void generateInstructionForNPC(NPCObject npc) {
        // Randomly choose between IDLE and WANDER (80% wander, 20% idle)
        NPCInstructionMessage.InstructionType type;
        if (random.nextFloat() < 0.8f) {
            type = NPCInstructionMessage.InstructionType.WANDER;
        } else {
            type = NPCInstructionMessage.InstructionType.IDLE;
        }

        // Create instruction message
        NPCInstructionMessage instruction = new NPCInstructionMessage(
                npc.entityId,
                System.currentTimeMillis(), // Use timestamp as instruction ID
                type,
                System.currentTimeMillis(), // Server timestamp
                INSTRUCTION_INTERVAL,        // Duration matches interval
                random.nextLong()            // Random seed for determinism
        );

        // Add type-specific parameters
        if (type == NPCInstructionMessage.InstructionType.WANDER) {
            Vector3 npcPosition = npc.getPosition();
            if (npcPosition != null) {
                instruction.withParam("centerX", npcPosition.x);
                instruction.withParam("centerY", npcPosition.y);
                instruction.withParam("centerZ", npcPosition.z);
                instruction.withParam("radius", 5.0f);  // 5-unit wander radius
                instruction.withParam("speed", 2.0f);   // 2 units/second movement speed
            }
        }

        // Broadcast instruction to all clients
        NetworkManager.sendToAllClients(instruction);

        Log.info("NPCBehaviorAgent", "Sent " + type + " instruction to NPC " + npc.entityId);
    }

    /**
     * Generate immediate instruction for a newly spawned NPC.
     */
    public void generateInitialInstruction(NPCObject npc) {
        generateInstructionForNPC(npc);
    }
}
