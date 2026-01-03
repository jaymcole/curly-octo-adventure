package curly.octo.server.serverAgents;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.common.character.NPCBrain;
import curly.octo.common.character.WalkingCharacter;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.hints.SpawnPointHint;
import curly.octo.common.network.messages.NPCElectionMessage;
import curly.octo.server.GameServer;
import curly.octo.server.ServerGameObjectManager;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side agent responsible for spawning NPCs at valid locations on the map.
 */
public class NPCSpawnerAgent extends BaseAgent {

    private final GameMap gameMap;
    private final GameServer gameServer;
    private final Random random;
    private int npcCount = 0;
    private static final int MAX_NPCS = 10; // Maximum number of NPCs to spawn
    private boolean spawnComplete = false;

    public NPCSpawnerAgent(ServerGameObjectManager objectManager, GameMap gameMap, GameServer gameServer) {
        super(objectManager);
        this.gameMap = gameMap;
        this.gameServer = gameServer;
        this.random = new Random();

        // Spawn NPCs immediately upon agent creation
        spawnNPCs();
    }

    @Override
    public void update(float deltaTime) {
        // NPCs are spawned in constructor, nothing to do here
        // This method left for potential future use
    }

    /**
     * Spawn all NPCs.
     */
    private void spawnNPCs() {
        if (spawnComplete) {
            return;
        }

        // Get spawn point hints from the map
        ArrayList<MapHint> spawnHints = gameMap.getAllHintsOfType(SpawnPointHint.class);

        if (spawnHints != null && !spawnHints.isEmpty()) {
            // Spawn NPCs at spawn points
            int npcsToSpawn = Math.min(MAX_NPCS, spawnHints.size());
            for (int i = 0; i < npcsToSpawn; i++) {
                SpawnPointHint hint = (SpawnPointHint) spawnHints.get(i % spawnHints.size());
                MapTile spawnTile = gameMap.getTile(hint.tileLookupKey);

                if (spawnTile != null) {
                    // DEBUG: Log raw spawn tile info
                    Log.info("NPCSpawnerAgent", "DEBUG NPC SPAWN: Raw spawn tile position: (" +
                        spawnTile.x + ", " + spawnTile.y + ", " + spawnTile.z + ")");
                    Log.info("NPCSpawnerAgent", "DEBUG NPC SPAWN: Tile fill type: " + spawnTile.fillType +
                        ", geometry: " + spawnTile.geometryType);

                    // Use raw tile coordinates (same as player spawns)
                    Vector3 spawnPosition = new Vector3(spawnTile.x, spawnTile.y, spawnTile.z);

                    // Add small random offset
                    spawnPosition.x += (random.nextFloat() - 0.5f) * 0.5f;
                    spawnPosition.z += (random.nextFloat() - 0.5f) * 0.5f;

                    Log.info("NPCSpawnerAgent", "DEBUG NPC SPAWN: Final NPC spawn position (with random offset): " + spawnPosition);
                    Log.info("NPCSpawnerAgent", "DEBUG NPC SPAWN: NPC dimensions - height: 1.8, width: 0.6");

                    spawnNPC(spawnPosition);
                    npcCount++;
                }
            }
        } else {
            Log.warn("NPCSpawnerAgent", "No spawn point hints found in map, spawning NPCs at origin");
            // Fallback: spawn at origin if no spawn points exist
            for (int i = 0; i < MAX_NPCS; i++) {
                Vector3 spawnPosition = new Vector3(
                        i * 2.0f,  // Spread them out
                        2.0f,
                        0
                );
                spawnNPC(spawnPosition);
                npcCount++;
            }
        }

        spawnComplete = true;
        Log.info("NPCSpawnerAgent", "Spawned " + npcCount + " NPCs");
    }

    /**
     * Spawn an NPC at the given position.
     */
    private void spawnNPC(Vector3 position) {
        // Generate unique NPC ID
        String npcId = "npc_" + UUID.randomUUID().toString().substring(0, 8);

        // Create NPC object
        WalkingCharacter npc = new WalkingCharacter(npcId, 1.8f, 0.6f);
        npc.setBrain(new NPCBrain());
        npc.setPosition(position);

        // Add to object manager
        objectManager.add(npc);

        Log.info("NPCSpawnerAgent", "Spawned NPC " + npcId + " at " + position);

        // Trigger election for this NPC if server is available and clients are connected
        if (gameServer != null) {
            gameServer.electNPCAuthority(npcId, NPCElectionMessage.ElectionReason.INITIAL);
        }
    }

    /**
     * Check if spawning is complete.
     */
    public boolean isSpawnComplete() {
        return spawnComplete;
    }

    /**
     * Get the number of NPCs spawned.
     */
    public int getNpcCount() {
        return npcCount;
    }
}
