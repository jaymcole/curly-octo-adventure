package curly.octo.game.serverStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.HostGameWorld;
import curly.octo.game.serverStates.BaseGameStateServer;
import curly.octo.map.GameMap;
import curly.octo.network.GameServer;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.mapTransferMessages.MapTransferBeginMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static curly.octo.map.LevelChunk.CHUNK_SIZE;

public class ServerMapTransferState extends BaseGameStateServer {

    public ServerMapTransferState(GameServer gameServer, HostGameWorld hostGameWorld) {
        super(gameServer, hostGameWorld);
    }

    @Override
    public void start() {
        byte[] mapData = getSerializedMapData();
        int totalChunks = (int) Math.ceil((double) mapData.length / CHUNK_SIZE);

        MapTransferBeginMessage startMessage = new MapTransferBeginMessage(hostGameWorld.getMapManager().getMapId(), totalChunks, mapData.length);
        NetworkManager.sendToAllClients(startMessage);
    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void end() {

    }

    private byte[] getSerializedMapData()  {
        GameMap currentMap = hostGameWorld.getMapManager();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            Kryo kryo = gameServer.getServer().getKryo();
            kryo.writeObject(output, currentMap);
            output.flush();
            byte[] mapData = baos.toByteArray();
            Log.info("Server", "Serialized and cached NEW map data with hash " + currentMap.hashCode() +
                " (" + mapData.length + " bytes, " + currentMap.getAllTiles().size() + " tiles)");
            return mapData;
        } catch (IOException exception) {
            Log.error("getSerializedMapData", "Failed to serialize game map. " + exception.getMessage());
        }
        return null;
    }
}
