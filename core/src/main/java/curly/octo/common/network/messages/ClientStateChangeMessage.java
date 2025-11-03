package curly.octo.common.network.messages;

import curly.octo.common.network.NetworkMessage;

public class ClientStateChangeMessage extends NetworkMessage  {
    public String newState;
    public String oldState;

    public ClientStateChangeMessage() {

    }

    public ClientStateChangeMessage(Class newState, Class oldState) {
        this.newState = newState.getSimpleName();
        this.oldState = oldState.getSimpleName();
    }
}
