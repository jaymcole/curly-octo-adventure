package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.map.GameMap;

import java.io.ByteArrayInputStream;

import static curly.octo.game.clientStates.mapTransfer.MapTransferSharedStatics.chunks;

public class MapTransferReassemblyState extends BaseGameStateClient {
    private GameMap receivedMap;

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
                receivedMap = kryo.readObject(input, GameMap.class);

                Log.info("MapTransferReassemblyState", "Map successfully deserialized: " + receivedMap.hashCode() +
                        " (" + receivedMap.getAllTiles().size() + " tiles)");
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

    @Override
    public void updateState(float delta) {

    }

    @Override
    public void end() {

    }
}
