package curly.octo.game.regeneration;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameClient;

/**
 * Routes network events to the appropriate components.
 * Separates networking concerns from business logic.
 */
public class NetworkEventRouter {
    
    private final MapRegenerationCoordinator coordinator;
    private final String clientId;
    
    public NetworkEventRouter(MapRegenerationCoordinator coordinator, String clientId) {
        this.coordinator = coordinator;
        this.clientId = clientId;
    }
    
    /**
     * Setup all map regeneration related network listeners on the game client
     */
    public void setupMapRegenerationListeners(GameClient gameClient) {
        Log.info("NetworkEventRouter", "Setting up map regeneration network listeners");
        
        // Map regeneration start listener
        gameClient.setMapRegenerationStartListener(message -> {
            Gdx.app.postRunnable(() -> {
                Log.info("NetworkEventRouter", String.format(
                    "Map regeneration start received - Seed: %d, Reason: %s", 
                    message.newMapSeed, message.reason));
                
                coordinator.startRegeneration(message, clientId);
            });
        });
        
        // Map transfer start listener  
        gameClient.setMapTransferStartListener(message -> {
            Gdx.app.postRunnable(() -> {
                Log.info("NetworkEventRouter", String.format(
                    "Map transfer start - ID: %s, Chunks: %d, Size: %d bytes",
                    message.mapId, message.totalChunks, message.totalSize));
                
                coordinator.onTransferStart(message.mapId, message.totalChunks, message.totalSize);
            });
        });
        
        // Map chunk listener
        gameClient.setMapChunkListener(message -> {
            // Don't post to main thread for chunk updates - they're frequent and coordinator is thread-safe
            Log.debug("NetworkEventRouter", String.format(
                "Map chunk received - %s chunk %d/%d (%d bytes)",
                message.mapId, message.chunkIndex + 1, message.totalChunks, message.chunkData.length));
            
            coordinator.onChunkReceived(message.chunkIndex, message.chunkData);
        });
        
        // Map transfer complete listener
        gameClient.setMapTransferCompleteListener(message -> {
            Gdx.app.postRunnable(() -> {
                Log.info("NetworkEventRouter", "Map transfer complete for ID: " + message.mapId);
                coordinator.onTransferComplete();
            });
        });
        
        // Map received listener removed - handled by ClientGameMode to avoid conflicts
        
        Log.info("NetworkEventRouter", "Map regeneration network listeners configured");
    }
    
    /**
     * Setup other network listeners that don't relate to map regeneration
     * This keeps non-regeneration networking separate and clean
     */
    public void setupGameplayListeners(GameClient gameClient, GameplayEventHandler handler) {
        // Player assignment, updates, roster, disconnects, etc.
        // These are separated from regeneration concerns
        
        gameClient.setPlayerAssignmentListener(message -> {
            Gdx.app.postRunnable(() -> handler.onPlayerAssignment(message));
        });
        
        gameClient.setPlayerUpdateListener(message -> {
            Gdx.app.postRunnable(() -> handler.onPlayerUpdate(message));
        });
        
        gameClient.setPlayerRosterListener(message -> {
            Gdx.app.postRunnable(() -> handler.onPlayerRoster(message));
        });
        
        gameClient.setPlayerDisconnectListener(message -> {
            Gdx.app.postRunnable(() -> handler.onPlayerDisconnect(message));
        });
        
        gameClient.setPlayerResetListener(message -> {
            Gdx.app.postRunnable(() -> handler.onPlayerReset(message));
        });
        
        Log.info("NetworkEventRouter", "Gameplay network listeners configured");
    }
    
    /**
     * Interface for handling non-regeneration gameplay events
     * This keeps the router clean and allows for easy testing/mocking
     */
    public interface GameplayEventHandler {
        void onPlayerAssignment(curly.octo.network.messages.PlayerAssignmentUpdate message);
        void onPlayerUpdate(curly.octo.network.messages.PlayerUpdate message);
        void onPlayerRoster(curly.octo.network.messages.PlayerObjectRosterUpdate message);
        void onPlayerDisconnect(curly.octo.network.messages.PlayerDisconnectUpdate message);
        void onPlayerReset(curly.octo.network.messages.PlayerResetMessage message);
    }
}