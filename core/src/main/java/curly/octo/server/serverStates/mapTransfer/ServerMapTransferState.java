package curly.octo.server.serverStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.GameObject;
import curly.octo.server.ServerCoordinator;
import curly.octo.client.clientStates.mapTransferStates.MapTransferSharedStatics;
import curly.octo.server.playerManagement.*;
import curly.octo.server.serverStates.BaseGameStateServer;
import curly.octo.server.serverStates.ServerStateManager;
import curly.octo.common.map.GameMap;
import curly.octo.common.network.messages.MapTransferPayload;
import curly.octo.server.GameServer;
import curly.octo.common.network.NetworkManager;
import curly.octo.common.network.messages.mapTransferMessages.MapTransferAllClientProgressMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Server state that manages map transfers to multiple clients.
 * Creates and manages MapTransferWorker instances for each client.
 */
public class ServerMapTransferState extends BaseGameStateServer {

    private byte[] cachedMapData; // Serialize once, reuse for all clients
    private HashMap<Integer, MapTransferWorker> activeWorkers; // connectionId -> worker
    private boolean hasStartedTransfers = false; // Track if any transfers have been initiated
    private Queue<Connection> pendingClients = new LinkedList<>(); // Clients waiting for cachedMapData

    // Progress broadcast rate limiting
    private float progressBroadcastTimer = 0f;
    private static final float PROGRESS_BROADCAST_INTERVAL = 0.1f; // Broadcast every 100ms (10 times/second)

    public ServerMapTransferState(GameServer gameServer, ServerCoordinator serverCoordinator) {
        super(gameServer, serverCoordinator);
        activeWorkers = new HashMap<>();
    }

    @Override
    public void start() {
        Log.info("ServerMapTransferState", "Entering map transfer state");

        // Reset transfer tracking
        hasStartedTransfers = false;

        // Serialize map once, if not already cached
        if (cachedMapData == null) {
            cachedMapData = getSerializedMapData();
            if (cachedMapData != null) {
                Log.info("ServerMapTransferState", "Cached map data: " + cachedMapData.length + " bytes");
            } else {
                Log.error("ServerMapTransferState", "Failed to serialize map data");
                // Transition out of this state to prevent getting stuck
                ServerStateManager.setServerState(ServerWaitForClientsToBeReadyState.class);
                return;
            }
        }

        // Create workers for ALL currently connected clients
        for (Connection conn : gameServer.getServer().getConnections()) {
            ClientProfile profile = serverCoordinator.getClientProfile(new ClientConnectionKey(conn));
            if (profile != null && profile.connectionStatus == ConnectionStatus.DISCONNECTED) {
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
        ClientProfile profile = serverCoordinator.getClientProfile(new ClientConnectionKey(connection));
        if (profile != null && profile.connectionStatus == ConnectionStatus.DISCONNECTED) {
            return;
        }

        if (activeWorkers.containsKey(connection.getID())) {
            return;
        }

        if (cachedMapData == null) {
            pendingClients.add(connection);
            return;
        }

        GameMap currentMap = serverCoordinator.getMapManager();
        if (currentMap == null) {
            return;
        }

        MapTransferWorker worker = new MapTransferWorker(connection, gameServer, serverCoordinator, cachedMapData, currentMap.getMapId());
        activeWorkers.put(connection.getID(), worker);
        worker.start();
        hasStartedTransfers = true;

        Log.info("ServerMapTransferState", "Started transfer worker for client " + connection.getID() +
                " (total active: " + activeWorkers.size() + ")");
    }


    @Override
    public void update(float delta) {
        progressBroadcastTimer += delta;
        if (progressBroadcastTimer >= PROGRESS_BROADCAST_INTERVAL) {
            MapTransferAllClientProgressMessage groupProgress = constructGroupProgressMessage();
            NetworkManager.sendToAllClients(groupProgress);
            progressBroadcastTimer = 0f;
        }

        if (!pendingClients.isEmpty() && cachedMapData != null) {
            while (!pendingClients.isEmpty()) {
                startTransferForClient(pendingClients.poll());
            }
        }

        Iterator<Map.Entry<Integer, MapTransferWorker>> iterator = activeWorkers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, MapTransferWorker> entry = iterator.next();
            MapTransferWorker worker = entry.getValue();
            worker.update(delta);
            if (worker.isComplete()) {
                iterator.remove();
            }
        }

        if (hasStartedTransfers && activeWorkers.isEmpty()) {
            Log.info("ServerMapTransferState", "All transfers complete, transitioning to wait for clients");
            ServerStateManager.setServerState(ServerWaitForClientsToBeReadyState.class);
        }
    }

    private MapTransferAllClientProgressMessage constructGroupProgressMessage() {
        HashMap<ClientUniqueId, Integer> clientIdToChunkProgressMap = new HashMap<>();
        for (Connection conn : gameServer.getServer().getConnections()) {
            ClientProfile profile = serverCoordinator.getClientProfile(new ClientConnectionKey(conn));
            if (profile == null || profile.connectionStatus == ConnectionStatus.DISCONNECTED || profile.clientUniqueId == null) {
                continue;
            }

            MapTransferWorker worker = activeWorkers.get(conn.getID());
            if (worker != null) {
                clientIdToChunkProgressMap.put(profile.clientUniqueId, worker.currentChunkIndex);
            } else {
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
        pendingClients.clear();
    }

    private byte[] getSerializedMapData() {
        GameMap currentMap = serverCoordinator.getMapManager();
        if (currentMap == null) {
            Log.error("ServerMapTransferState", "Cannot serialize - no map available");
            return null;
        }

        MapTransferPayload payload = new MapTransferPayload();
        payload.map = currentMap;

        if (serverCoordinator.getGameObjectManager() != null) {
            payload.gameObjects = serverCoordinator.getGameObjectManager().getAllObjects();
            Log.info("ServerMapTransferState", "Including " + payload.gameObjects.size() +
                    " game objects in transfer (" +
                    serverCoordinator.getGameObjectManager().getPlayerCount() + " players)");
        } else {
            Log.warn("ServerMapTransferState", "No game object manager - transferring map only");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            Kryo kryo = gameServer.getServer().getKryo();
            kryo.writeObject(output, payload);
            output.flush();
            byte[] mapData = baos.toByteArray();
            Log.info("ServerMapTransferState", "Serialized transfer payload: map " + currentMap.hashCode() +
                    " + " + payload.gameObjects.size() + " objects " +
                    "(" + mapData.length + " bytes, " + currentMap.getAllTiles().size() + " tiles)");
            return mapData;
        } catch (IOException exception) {
            Log.error("ServerMapTransferState", "Failed to serialize transfer payload: " + exception.getMessage());
            exception.printStackTrace();
            return null;
        }
    }

    public int getActiveWorkerCount() {
        return activeWorkers.size();
    }
}
