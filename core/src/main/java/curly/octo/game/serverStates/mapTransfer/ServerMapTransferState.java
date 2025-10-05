package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.HostGameWorld;
import curly.octo.game.serverStates.BaseGameStateServer;
import curly.octo.game.serverStates.ServerStateManager;
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
    private boolean hasStartedTransfers = false; // Track if any transfers have been initiated

    public ServerMapTransferState(GameServer gameServer, HostGameWorld hostGameWorld) {
        super(gameServer, hostGameWorld);
        activeWorkers = new HashMap<>();
    }

    @Override
    public void start() {
        Log.info("ServerMapTransferState", "Entering map transfer state");

        // Reset transfer tracking
        hasStartedTransfers = false;

        // Serialize map once
        cachedMapData = getSerializedMapData();
        if (cachedMapData != null) {
            Log.info("ServerMapTransferState", "Cached map data: " + cachedMapData.length + " bytes");
        } else {
            Log.error("ServerMapTransferState", "Failed to serialize map data");
            return;
        }

        // Create workers for ALL currently connected clients
        // This handles initial entry and mid-game joins where existing clients need to be notified
        for (Connection conn : gameServer.getServer().getConnections()) {
            // Skip disconnected clients
            String clientKey = gameServer.constructClientProfileKey(conn);
            curly.octo.game.serverObjects.ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
            if (profile != null && profile.connectionStatus == curly.octo.game.serverObjects.ConnectionStatus.DISCONNECTED) {
                Log.info("ServerMapTransferState", "Skipping transfer for disconnected client " + conn.getID());
                continue;
            }
            startTransferForClient(conn);
        }

        Log.info("ServerMapTransferState", "Initialized " + activeWorkers.size() + " transfer worker(s)");
    }

    /**
     * Start transfer for a specific client
     */
    public void startTransferForClient(Connection connection) {
        // Check if client is disconnected
        String clientKey = gameServer.constructClientProfileKey(connection);
        curly.octo.game.serverObjects.ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
        if (profile != null && profile.connectionStatus == curly.octo.game.serverObjects.ConnectionStatus.DISCONNECTED) {
            Log.info("ServerMapTransferState", "Skipping transfer for disconnected client " + connection.getID());
            return;
        }

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
        hasStartedTransfers = true; // Mark that we've started at least one transfer

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

        // When all transfers complete, transition to wait for clients to confirm readiness
        // Only transition if we actually started transfers (prevents premature transition on state entry)
        if (hasStartedTransfers && activeWorkers.isEmpty()) {
            Log.info("ServerMapTransferState", "All transfers complete, transitioning to wait for clients");
            ServerStateManager.setServerState(ServerWaitForClientsToBeReadyState.class);
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
