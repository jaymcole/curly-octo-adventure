package curly.octo.common.network.messages.mapTransferMessages;

import curly.octo.common.network.NetworkMessage;

import java.util.HashMap;

public class MapTransferAllClientProgressMessage extends NetworkMessage {

    public HashMap<String, Integer> clientToChunkProgress;

    public MapTransferAllClientProgressMessage() {}

    public MapTransferAllClientProgressMessage(HashMap<String, Integer> clientToChunkProgress) {
        this.clientToChunkProgress = clientToChunkProgress;
    }
}
