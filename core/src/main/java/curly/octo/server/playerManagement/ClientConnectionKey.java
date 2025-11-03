package curly.octo.server.playerManagement;
import com.esotericsoftware.kryonet.Connection;

public class ClientConnectionKey {

    private final String connectionId;

    public ClientConnectionKey(Connection connection) {
        connectionId = connection.getID() + "";
    }


    @Override
    public String toString() {
        return this.connectionId;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ClientConnectionKey other = (ClientConnectionKey) obj;
        return this.toString().equals(other.toString());
    }

}
