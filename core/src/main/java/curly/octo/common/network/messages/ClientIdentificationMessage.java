package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

public class ClientIdentificationMessage extends NetworkMessage {
    public String clientUniqueId;
    public String clientName;

    public ClientIdentificationMessage() {
    }

    public ClientIdentificationMessage(String clientUniqueId, String clientName) {
        this.clientUniqueId = clientUniqueId;
        this.clientName = clientName;
    }
}
