package curly.octo;

import com.esotericsoftware.minlog.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

/**
 * Manages client configuration loaded from client.properties file.
 * Provides unique ID and preferred name for this client instance.
 */
public class ClientConfig {
    private static final String CONFIG_FILE = "client.properties";
    private static final String PROP_CLIENT_ID = "client.id";
    private static final String PROP_CLIENT_NAME = "client.name";

    private final String clientId;
    private final String clientName;

    public ClientConfig() {
        String tempId = null;
        String tempName = null;

        try (InputStream input = ClientConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                Log.warn("ClientConfig", "No client.properties found, using defaults");
            } else {
                Properties props = new Properties();
                props.load(input);

                // Load client ID
                String configId = props.getProperty(PROP_CLIENT_ID);
                if (configId != null && !configId.trim().isEmpty()) {
                    tempId = configId.trim();
                    Log.info("ClientConfig", "Loaded client.id: " + tempId);
                }

                // Load client name
                String configName = props.getProperty(PROP_CLIENT_NAME);
                if (configName != null && !configName.trim().isEmpty()) {
                    tempName = configName.trim();
                    Log.info("ClientConfig", "Loaded client.name: " + tempName);
                }
            }
        } catch (IOException e) {
            Log.error("ClientConfig", "Error loading client.properties: " + e.getMessage());
        }

        // Assign final values, using defaults if not loaded
        if (tempId == null || tempId.isEmpty()) {
            this.clientId = UUID.randomUUID().toString();
            Log.warn("ClientConfig", "No client.id found, generated: " + this.clientId);
        } else {
            this.clientId = tempId;
        }

        if (tempName == null || tempName.isEmpty()) {
            this.clientName = "Player";
            Log.warn("ClientConfig", "No client.name found, using default: Player");
        } else {
            this.clientName = tempName;
        }
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }
}
