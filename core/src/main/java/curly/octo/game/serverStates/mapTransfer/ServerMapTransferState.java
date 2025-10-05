package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.HostGameWorld;
import curly.octo.game.serverStates.BaseGameStateServer;
import curly.octo.map.GameMap;
import curly.octo.network.GameServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Server state that manages map transfers to multiple clients.
 * Creates and manages MapTransferWorker instances for each client.
 */
public class ServerMapTransferState extends BaseGameStateServer {

    private byte[] cachedMapData; // Serialize once, reuse for all clients
    private HashMap<Integer, MapTransferWorker> activeWorkers; // connectionId -> worker

    public ServerMapTransferState(GameServer gameServer, HostGameWorld hostGameWorld) {
        super(gameServer, hostGameWorld);
        activeWorkers = new HashMap<>();
    }

    @Override
    public void start() {
        Log.info("ServerMapTransferState", "Entering map transfer state");

        // Serialize map once
        cachedMapData = getSerializedMapData();
        if (cachedMapData != null) {
            Log.info("ServerMapTransferState", "Cached map data: " + cachedMapData.length + " bytes");
        } else {
            Log.error("ServerMapTransferState", "Failed to serialize map data");
        }
    }

    /**
     * Start transfer for a specific client
     */
    public void startTransferForClient(Connection connection) {
        if (activeWorkers.containsKey(connection.getID())) {
            Log.warn("ServerMapTransferState", "Transfer already in progress for client " + connection.getID());
            return;
        }

        if (cachedMapData == null) {
            Log.error("ServerMapTransferState", "Cannot start transfer - no cached map data");
            return;
        }

        MapTransferWorker worker = new MapTransferWorker(connection, gameServer, cachedMapData);
        activeWorkers.put(connection.getID(), worker);
        worker.start();

        Log.info("ServerMapTransferState", "Started transfer worker for client " + connection.getID() +
                " (total active: " + activeWorkers.size() + ")");
    }

    @Override
    public void update(float delta) {
        // Update all active workers
        Iterator<Map.Entry<Integer, MapTransferWorker>> iterator = activeWorkers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, MapTransferWorker> entry = iterator.next();
            MapTransferWorker worker = entry.getValue();

            worker.update(delta);

            // Remove completed workers
            if (worker.isComplete()) {
                Log.info("ServerMapTransferState", "Worker completed for client " + entry.getKey());
                iterator.remove();
            }
        }

        // If all transfers complete and no active workers, could transition out of state
        if (activeWorkers.isEmpty()) {
            Log.debug("ServerMapTransferState", "All transfers complete, state idle");
        }
    }

    @Override
    public void end() {
        Log.info("ServerMapTransferState", "Exiting map transfer state, cleaning up " +
                activeWorkers.size() + " active workers");
        activeWorkers.clear();
        cachedMapData = null; // Release memory
    }

    private byte[] getSerializedMapData() {
        GameMap currentMap = hostGameWorld.getMapManager();
        if (currentMap == null) {
            Log.error("ServerMapTransferState", "Cannot serialize - no map available");
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            Kryo kryo = gameServer.getServer().getKryo();
            kryo.writeObject(output, currentMap);
            output.flush();
            byte[] mapData = baos.toByteArray();
            Log.info("ServerMapTransferState", "Serialized map: " + currentMap.hashCode() +
                    " (" + mapData.length + " bytes, " + currentMap.getAllTiles().size() + " tiles)");
            return mapData;
        } catch (IOException exception) {
            Log.error("ServerMapTransferState", "Failed to serialize map: " + exception.getMessage());
            return null;
        }
    }

    public int getActiveWorkerCount() {
        return activeWorkers.size();
    }
}
