package curly.octo.server.serverStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.server.HostGameWorld;
import curly.octo.client.clientStates.mapTransferStates.MapTransferSharedStatics;
import curly.octo.server.playerManagement.ClientProfile;
import curly.octo.server.playerManagement.ConnectionStatus;
import curly.octo.server.serverStates.BaseGameStateServer;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.common.map.GameMap;
import curly.octo.server.GameServer;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferAllClientProgressMessage;

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

    // Progress broadcast rate limiting
    private float progressBroadcastTimer = 0f;
    private static final float PROGRESS_BROADCAST_INTERVAL = 0.1f; // Broadcast every 100ms (10 times/second)

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
            ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
            if (profile != null && profile.connectionStatus == ConnectionStatus.DISCONNECTED) {
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
        ClientProfile profile = hostGameWorld.getClientProfile(clientKey);
        if (profile != null && profile.connectionStatus == ConnectionStatus.DISCONNECTED) {
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

        MapTransferWorker worker = new MapTransferWorker(connection, gameServer, hostGameWorld, cachedMapData, hostGameWorld.getMapManager().getMapId());
        activeWorkers.put(connection.getID(), worker);
        worker.start();
        hasStartedTransfers = true; // Mark that we've started at least one transfer

        Log.info("ServerMapTransferState", "Started transfer worker for client " + connection.getID() +
                " (total active: " + activeWorkers.size() + ")");
    }


    @Override
    public void update(float delta) {
        // Rate-limit progress broadcasts to avoid overwhelming client buffers
        progressBroadcastTimer += delta;
        if (progressBroadcastTimer >= PROGRESS_BROADCAST_INTERVAL) {
            MapTransferAllClientProgressMessage groupProgress = constructGroupProgressMessage();
            // Broadcast progress to ALL clients (including those already in ClientPlayingState)
            NetworkManager.sendToAllClients(groupProgress);
            progressBroadcastTimer = 0f;
        }

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

        if (hasStartedTransfers && activeWorkers.isEmpty()) {
            Log.info("ServerMapTransferState", "All transfers complete, transitioning to wait for clients");
            ServerStateManager.setServerState(ServerWaitForClientsToBeReadyState.class);
        }
    }

    private MapTransferAllClientProgressMessage constructGroupProgressMessage() {
        HashMap<String, Integer> clientIdToChunkProgressMap = new HashMap<>();
        // Iterate through ALL connected clients
        for (Connection conn : gameServer.getServer().getConnections()) {
            String clientKey = gameServer.constructClientProfileKey(conn);
            ClientProfile profile = hostGameWorld.getClientProfile(clientKey);

            // Skip disconnected clients
//            if (profile == null || profile.connectionStatus == ConnectionStatus.DISCONNECTED) {
//                continue;
//            }

            if (profile.clientUniqueId == null) {
                continue;
            }

            // Check if this client has an active worker (currently downloading)
            MapTransferWorker worker = activeWorkers.get(conn.getID());
            if (worker != null) {
                // Client is downloading - use their current progress
                clientIdToChunkProgressMap.put(profile.clientUniqueId, worker.currentChunkIndex);
            } else {
                // Client already has the map - mark as 100% complete
                clientIdToChunkProgressMap.put(profile.clientUniqueId, MapTransferSharedStatics.getTotalChunks());
            }
        }

        return new MapTransferAllClientProgressMessage(clientIdToChunkProgressMap);
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
