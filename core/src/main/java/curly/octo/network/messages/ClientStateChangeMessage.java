package curly.octo.network.messages;

import curly.octo.network.NetworkMessage;

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
