package curly.octo.client.clientStates.mapTransferStates;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.minlog.Log;
import curly.octo.client.clientStates.BaseGameStateClient;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.clientStates.mapTransferStates.ui.MapTransferScreen;
import curly.octo.common.GameObject;
import curly.octo.common.map.GameMap;
import curly.octo.common.network.messages.MapTransferPayload;

import java.util.List;

import java.io.ByteArrayInputStream;

import static curly.octo.client.clientStates.mapTransferStates.MapTransferSharedStatics.chunks;

public class MapTransferReassemblyState extends BaseGameStateClient {
    private GameMap receivedMap;
    private List<GameObject> receivedGameObjects;

    public MapTransferReassemblyState(BaseScreen screen) {
        super(screen);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferReassemblyState.class.getSimpleName());

        if (chunks != null) {
            reassembleAndDeserialize();
        } else {
            Log.error("MapTransferReassemblyState", "No chunks to reassemble!");
        }
    }

    private void reassembleAndDeserialize() {
        try {
            Log.info("MapTransferReassemblyState", "Reassembling map from chunks...");

            // Verify all chunks are present
            for (int i = 0; i < chunks.length; i++) {
                if (chunks[i] == null) {
                    Log.error("MapTransferReassemblyState", "Missing chunk " + i + " of " + chunks.length +
                        " - cannot reassemble map!");
                    return;
                }
            }

            // Calculate total size
            int totalLength = 0;
            for (byte[] chunk : chunks) {
                totalLength += chunk.length;
            }

            // Reassemble chunks
            byte[] completeData = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, completeData, offset, chunk.length);
                offset += chunk.length;
            }

            Log.info("MapTransferReassemblyState", "Reassembled " + totalLength + " bytes, deserializing...");

            // Deserialize using Kryo
            try (ByteArrayInputStream bais = new ByteArrayInputStream(completeData);
                 Input input = new Input(bais)) {

                // Get the client's Kryo instance
                Kryo kryo = getKryoInstance();
                MapTransferPayload payload = kryo.readObject(input, MapTransferPayload.class);

                // Extract map and game objects from payload
                receivedMap = payload.map;
                receivedGameObjects = payload.gameObjects;

                Log.info("MapTransferReassemblyState", "Transfer payload successfully deserialized:");
                Log.info("MapTransferReassemblyState", "  Map: " + receivedMap.hashCode() +
                        " (" + receivedMap.getAllTiles().size() + " tiles)");
                Log.info("MapTransferReassemblyState", "  Game Objects: " + receivedGameObjects.size() +
                        " objects received");
                StateManager.setCurrentState(MapTransferBuildAssetsState.class);
            }

            // Clear chunks to free memory
            MapTransferSharedStatics.chunks = null;

        } catch (Exception e) {
            Log.error("MapTransferReassemblyState", "Error reassembling map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Kryo getKryoInstance() {
        // Get Kryo instance from GameClient stored in StateManager
        if (StateManager.getGameClient() == null) {
            throw new IllegalStateException("GameClient not set in StateManager - call StateManager.setGameClient() first");
        }
        return StateManager.getGameClient().getClient().getKryo();
    }

    public GameMap getReceivedMap() {
        return receivedMap;
    }

    public List<GameObject> getReceivedGameObjects() {
        return receivedGameObjects;
    }

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
