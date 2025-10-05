package curly.octo.game.clientStates.mapTransfer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.clientStates.BaseGameStateClient;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.game.clientStates.StateManager;
import curly.octo.map.GameMap;
import curly.octo.network.messages.legacyMessages.MapTransferCompleteMessage;

import java.io.ByteArrayInputStream;

public class MapTransferCompleteState extends BaseGameStateClient {
    private byte[][] chunksToReassemble;
    private GameMap receivedMap;

    public MapTransferCompleteState(BaseScreen screen) {
        super(screen);
    }

    public void setChunksForReassembly(byte[][] chunks) {
        this.chunksToReassemble = chunks;
    }

    public void handleMapTransferComplete(MapTransferCompleteMessage message) {
        Log.info("MapTransferCompleteState", "Received transfer complete confirmation for: " + message.mapId);
    }

    @Override
    public void start() {
        MapTransferScreen.setPhaseMessage(MapTransferCompleteState.class.getSimpleName());

        if (chunksToReassemble != null) {
            reassembleAndDeserialize();
        } else {
            Log.error("MapTransferCompleteState", "No chunks to reassemble!");
        }
    }

    private void reassembleAndDeserialize() {
        try {
            Log.info("MapTransferCompleteState", "Reassembling map from chunks...");

            // Calculate total size
            int totalLength = 0;
            for (byte[] chunk : chunksToReassemble) {
                totalLength += chunk.length;
            }

            // Reassemble chunks
            byte[] completeData = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : chunksToReassemble) {
                System.arraycopy(chunk, 0, completeData, offset, chunk.length);
                offset += chunk.length;
            }

            Log.info("MapTransferCompleteState", "Reassembled " + totalLength + " bytes, deserializing...");

            // Deserialize using Kryo
            try (ByteArrayInputStream bais = new ByteArrayInputStream(completeData);
                 Input input = new Input(bais)) {

                // Get the client's Kryo instance
                Kryo kryo = getKryoInstance();
                receivedMap = kryo.readObject(input, GameMap.class);

                Log.info("MapTransferCompleteState", "Map successfully deserialized: " + receivedMap.hashCode() +
                        " (" + receivedMap.getAllTiles().size() + " tiles)");

                // TODO: Store map in ClientGameWorld or appropriate location

                // Transition to build assets state
                StateManager.setCurrentState(MapTransferBuildAssetsState.class);
            }

            // Clear chunks to free memory
            chunksToReassemble = null;

        } catch (Exception e) {
            Log.error("MapTransferCompleteState", "Error reassembling map: " + e.getMessage());
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
