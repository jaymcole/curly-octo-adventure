package curly.octo.network.messages;

import curly.octo.network.NetworkMessage;

public class ClientStateChangeMessage extends NetworkMessage  {
    public String newState;
    public String oldState;

    public ClientStateChangeMessage() {

    }

    public ClientStateChangeMessage(Class newState, Class oldState) {
        this.newState = newState.getName();
        this.oldState = oldState.getName();
    }
}
