package curly.octo.server.playerManagement;

public class ClientUniqueId {
    public String uniqueId;

    public ClientUniqueId() {
        uniqueId = "missing";
    }

    public ClientUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ClientUniqueId other = (ClientUniqueId) obj;
        return this.uniqueId.equals(other.uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }

    @Override
    public String toString() {
        return uniqueId;
    }
}
