package curly.octo.network.messages.mapTransferMessages;

import curly.octo.network.NetworkMessage;

import java.util.HashMap;

public class MapTransferAllClientProgressMessage extends NetworkMessage {

    public HashMap<String, Integer> clientToChunkProgress;

    public MapTransferAllClientProgressMessage() {}

    public MapTransferAllClientProgressMessage(HashMap<String, Integer> clientToChunkProgress) {
        this.clientToChunkProgress = clientToChunkProgress;
    }
}
