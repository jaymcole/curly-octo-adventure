package curly.octo.common.network.messages.mapTransferMessages;

import curly.octo.common.network.NetworkMessage;
import curly.octo.server.playerManagement.ClientUniqueId;

import java.util.HashMap;

public class MapTransferAllClientProgressMessage extends NetworkMessage {

    public HashMap<ClientUniqueId, Integer> clientToChunkProgress;

    public MapTransferAllClientProgressMessage() {}

    public MapTransferAllClientProgressMessage(HashMap<ClientUniqueId, Integer> clientToChunkProgress) {
        this.clientToChunkProgress = clientToChunkProgress;
    }
}
