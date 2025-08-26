package curly.octo.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.messages.*;
import curly.octo.network.messages.MapDataUpdate;
import curly.octo.network.messages.MapReceivedListener;
import curly.octo.network.messages.MapTransferStartListener;
import curly.octo.network.messages.MapChunkListener;
import curly.octo.network.messages.MapTransferCompleteListener;
import curly.octo.network.messages.PlayerDisconnectListener;

/**
 * Handles incoming network messages and connection events.
 * Extend this class and override the methods you need.
 */
public class NetworkListener implements Listener {
    private ConnectionListener connectionListener;
    private MapReceivedListener mapReceivedListener;
    private MapTransferStartListener mapTransferStartListener;
    private MapChunkListener mapChunkListener;
    private MapTransferCompleteListener mapTransferCompleteListener;
    private PlayerAssignmentListener playerAssignmentListener;
    private PlayerRosterListener playerRosterListener;
    private PlayerUpdateListener playerUpdateListener;
    private PlayerDisconnectListener playerDisconnectListener;

    private Server server;
    private Client client;

    public NetworkListener() {
    }

    public NetworkListener(Server server) {
        this.server = server;
    }

    public NetworkListener(Client client) {
        this.client = client;
    }

    public void setMapReceivedListener(MapReceivedListener listener) {
        this.mapReceivedListener = listener;
    }
    public void setPlayerAssignmentListener(PlayerAssignmentListener listener) {
        this.playerAssignmentListener = listener;
    }
    public void setPlayerRosterListener(PlayerRosterListener listener) {
        this.playerRosterListener = listener;
    }
    public void setPlayerUpdateListener(PlayerUpdateListener listener) {
        this.playerUpdateListener = listener;
    }
    public void setPlayerDisconnectListener(PlayerDisconnectListener listener) {
        this.playerDisconnectListener = listener;
    }
    
    public void setMapTransferStartListener(MapTransferStartListener listener) {
        this.mapTransferStartListener = listener;
    }
    
    public void setMapChunkListener(MapChunkListener listener) {
        this.mapChunkListener = listener;
    }
    
    public void setMapTransferCompleteListener(MapTransferCompleteListener listener) {
        this.mapTransferCompleteListener = listener;
    }
    /**
     * Called when a connection is received from a client (server-side)
     * or when connected to a server (client-side).
     */
    @Override
    public void connected(Connection connection) {
        Log.info("Network", "Connection established: " + connection.getRemoteAddressTCP().getAddress());
        if (connectionListener != null) {
            connectionListener.connected(connection);
        }
    }

    /**
     * Called when a connection is lost or disconnected.
     */
    @Override
    public void disconnected(Connection connection) {
        Log.info("Network", "Connection closed: " + connection.getID());
    }

    /**
     * Called when an object is received from a remote connection.
     * Override this method to handle specific message types.
     */
    @Override
    public void received(Connection connection, Object object) {
        // Log.info("Network", "Received object of type: " + (object != null ? object.getClass().getSimpleName() : "null"));
        if (object instanceof MapDataUpdate) {
            Log.info("Network", "Handling MapDataUpdate from " + connection.getRemoteAddressTCP().getAddress());
            MapDataUpdate update = (MapDataUpdate) object;
            if (update.map != null) {
                if (mapReceivedListener != null) {
                    mapReceivedListener.onMapReceived(update.toVoxelMap());
                } else {
                    Log.warn("Network", "No map received listener set");
                }
            } else {
                Log.error("Network", "Received null map in MapDataUpdate");
            }
        } else if (object instanceof PlayerObjectRosterUpdate) {
            PlayerObjectRosterUpdate update = (PlayerObjectRosterUpdate) object;
            Log.info("Network", "Received new player roster with " + update.players.length + " players");
            if (playerRosterListener != null) {
                playerRosterListener.onPlayerRosterReceived(update);
            } else {
                Log.warn("Network", "No player roster listener set");
            }
        } else if (object instanceof PlayerAssignmentUpdate) {
            Log.info("Network", "Received new player assignment");
            PlayerAssignmentUpdate update = (PlayerAssignmentUpdate) object;
            playerAssignmentListener.onPlayerAssignmentReceived(update);
        } else if (object instanceof PlayerUpdate) {
            PlayerUpdate update = (PlayerUpdate) object;
            if (playerUpdateListener != null) {
                playerUpdateListener.onPlayerUpdateReceived(update);
            }
        } else if (object instanceof MapTransferStartMessage) {
            MapTransferStartMessage message = (MapTransferStartMessage) object;
            Log.info("Network", "Received map transfer start: " + message.mapId);
            if (mapTransferStartListener != null) {
                mapTransferStartListener.onMapTransferStart(message);
            }
        } else if (object instanceof MapChunkMessage) {
            MapChunkMessage message = (MapChunkMessage) object;
            if (mapChunkListener != null) {
                mapChunkListener.onMapChunk(message);
            }
        } else if (object instanceof MapTransferCompleteMessage) {
            MapTransferCompleteMessage message = (MapTransferCompleteMessage) object;
            Log.info("Network", "Received map transfer complete: " + message.mapId);
            if (mapTransferCompleteListener != null) {
                mapTransferCompleteListener.onMapTransferComplete(message);
            }
        } else if (object instanceof PlayerDisconnectUpdate) {
            PlayerDisconnectUpdate update = (PlayerDisconnectUpdate) object;
            Log.info("Network", "Received player disconnect for player " + update.playerId);
            if (playerDisconnectListener != null) {
                playerDisconnectListener.onPlayerDisconnected(update);
            } else {
                Log.warn("Network", "No player disconnect listener set");
            }
        }
    }
}
