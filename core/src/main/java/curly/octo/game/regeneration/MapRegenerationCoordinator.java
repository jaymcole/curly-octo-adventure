package curly.octo.game.regeneration;

import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.network.messages.MapRegenerationStartMessage;
import curly.octo.network.messages.ClientReadyForMapMessage;
import curly.octo.network.GameClient;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Single coordinator for the entire map regeneration process.
 * Replaces the complex state machine with simple linear progression.
 * 
 * Thread-safe for network events coming from different threads.
 */
public class MapRegenerationCoordinator {
    
    private final MapResourceManager resourceManager;
    private final GameClient gameClient;
    private final List<RegenerationProgressListener> listeners;
    
    // Current regeneration state
    private volatile RegenerationPhase currentPhase;
    private volatile boolean isActive = false;
    private volatile boolean cancelled = false;
    
    // Download tracking
    private volatile int totalChunks = 0;
    private volatile int chunksReceived = 0;
    private volatile long totalBytes = 0;
    private volatile long bytesReceived = 0;
    private volatile String mapId;
    
    // Regeneration context
    private volatile long newMapSeed;
    private volatile String regenerationReason;
    private volatile long regenerationTimestamp;
    private volatile String clientId;
    
    public MapRegenerationCoordinator(MapResourceManager resourceManager, GameClient gameClient) {
        this.resourceManager = resourceManager;
        this.gameClient = gameClient;
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Add a listener for regeneration progress updates
     */
    public void addProgressListener(RegenerationProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.debug("MapRegenerationCoordinator", "Added progress listener");
        }
    }
    
    /**
     * Remove a progress listener
     */
    public void removeProgressListener(RegenerationProgressListener listener) {
        if (listeners.remove(listener)) {
            Log.debug("MapRegenerationCoordinator", "Removed progress listener");
        }
    }
    
    /**
     * Start map regeneration process
     */
    public synchronized void startRegeneration(MapRegenerationStartMessage message, String clientId) {
        if (isActive) {
            Log.warn("MapRegenerationCoordinator", "Regeneration already in progress, ignoring start request");
            return;
        }
        
        Log.info("MapRegenerationCoordinator", String.format(
            "*** STARTING NEW REGENERATION *** - New seed: %d, Reason: %s", 
            message.newMapSeed, message.reason));
        
        // Store regeneration context
        this.newMapSeed = message.newMapSeed;
        this.regenerationReason = message.reason;
        this.regenerationTimestamp = message.timestamp;
        this.clientId = clientId;
        this.isActive = true;
        this.cancelled = false;
        
        // Reset download tracking
        this.totalChunks = 0;
        this.chunksReceived = 0;
        this.totalBytes = 0;
        this.bytesReceived = 0;
        this.mapId = null;
        
        // Start with cleanup phase
        transitionToPhase(RegenerationPhase.CLEANUP);
        performCleanup();
    }
    
    /**
     * Called when map transfer starts
     */
    public synchronized void onTransferStart(String mapId, int totalChunks, long totalBytes) {
        if (!isActive || currentPhase != RegenerationPhase.DOWNLOADING) {
            Log.warn("MapRegenerationCoordinator", "Received transfer start but not in downloading phase");
            return;
        }
        
        this.mapId = mapId;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
        this.chunksReceived = 0;
        this.bytesReceived = 0;
        
        Log.info("MapRegenerationCoordinator", String.format(
            "Map transfer started - ID: %s, Chunks: %d, Size: %d bytes", 
            mapId, totalChunks, totalBytes));
        
        updateProgress(0.1f, String.format("Receiving map data (%d chunks)...", totalChunks));
    }
    
    /**
     * Called when a map chunk is received
     */
    public synchronized void onChunkReceived(int chunkIndex, byte[] chunkData) {
        if (!isActive || currentPhase != RegenerationPhase.DOWNLOADING) {
            return;
        }
        
        chunksReceived++;
        bytesReceived += chunkData.length;
        
        // Calculate progress (10% to 90% for downloading)
        if (totalChunks > 0) {
            float downloadProgress = (float) chunksReceived / totalChunks;
            float overallProgress = 0.1f + (downloadProgress * 0.8f);
            
            updateProgress(overallProgress, String.format("Received %d/%d chunks...", chunksReceived, totalChunks));
            
            Log.debug("MapRegenerationCoordinator", String.format(
                "Chunk %d received (%d/%d, %.1f%%)", 
                chunkIndex, chunksReceived, totalChunks, overallProgress * 100));
        }
    }
    
    /**
     * Called when map transfer is complete
     */
    public synchronized void onTransferComplete() {
        if (!isActive || currentPhase != RegenerationPhase.DOWNLOADING) {
            Log.warn("MapRegenerationCoordinator", "Received transfer complete but not in downloading phase");
            return;
        }
        
        Log.info("MapRegenerationCoordinator", String.format(
            "Map transfer complete - Received %d/%d chunks (%d bytes)", 
            chunksReceived, totalChunks, bytesReceived));
        
        updateProgress(0.95f, "Map transfer complete, processing...");
        
        // Immediately transition to rebuilding phase - no delay needed
        transitionToPhase(RegenerationPhase.REBUILDING);
    }
    
    /**
     * Called when the new map data is ready to be applied
     */
    public synchronized void onMapReady(GameMap newMap) {
        if (!isActive) {
            Log.warn("MapRegenerationCoordinator", "Received map ready but regeneration is not active");
            return;
        }
        
        // If we're still downloading, automatically transition to rebuilding
        if (currentPhase == RegenerationPhase.DOWNLOADING) {
            Log.info("MapRegenerationCoordinator", "Map ready during downloading phase, transitioning to rebuilding");
            transitionToPhase(RegenerationPhase.REBUILDING);
        }
        
        if (currentPhase != RegenerationPhase.REBUILDING) {
            Log.warn("MapRegenerationCoordinator", String.format(
                "Received map ready but not in rebuilding phase - isActive: %b, currentPhase: %s", 
                isActive, currentPhase));
            return;
        }
        
        Log.info("MapRegenerationCoordinator", "New map ready, applying to game world");
        
        try {
            updateProgress(0.1f, "Applying new map...");
            resourceManager.applyNewMap(newMap);
            
            updateProgress(0.6f, "Reinitializing players...");
            resourceManager.reinitializePlayers();
            
            updateProgress(0.9f, "Finalizing world setup...");
            
            // Complete the regeneration
            completeRegeneration();
            
        } catch (Exception e) {
            Log.error("MapRegenerationCoordinator", "Error during map rebuilding", e);
            handleError("Failed to apply new map: " + e.getMessage());
        }
    }
    
    /**
     * Cancel the current regeneration process
     */
    public synchronized void cancel(String reason) {
        if (!isActive) {
            return;
        }
        
        Log.warn("MapRegenerationCoordinator", "Cancelling regeneration: " + reason);
        cancelled = true;
        isActive = false;
        
        notifyListeners(listener -> listener.onError("Regeneration cancelled: " + reason));
    }
    
    /**
     * Get current regeneration phase
     */
    public RegenerationPhase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Check if regeneration is currently active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Get regeneration context information
     */
    public long getNewMapSeed() { return newMapSeed; }
    public String getRegenerationReason() { return regenerationReason; }
    public long getRegenerationTimestamp() { return regenerationTimestamp; }
    
    // Private methods
    
    private void performCleanup() {
        new Thread(() -> {
            try {
                updateProgress(0.1f, "Cleaning up resources...");
                resourceManager.cleanupForRegeneration();
                
                updateProgress(0.5f, "Sending ready confirmation...");
                sendReadyConfirmation();
                
                updateProgress(0.9f, "Cleanup complete");
                
                // Transition to downloading after cleanup
                Thread.sleep(200); // Brief pause for UI
                transitionToPhase(RegenerationPhase.DOWNLOADING);
                
            } catch (Exception e) {
                Log.error("MapRegenerationCoordinator", "Error during cleanup", e);
                handleError("Cleanup failed: " + e.getMessage());
            }
        }, "RegenerationCleanupThread").start();
    }
    
    private void sendReadyConfirmation() {
        if (gameClient != null && clientId != null) {
            try {
                ClientReadyForMapMessage readyMessage = new ClientReadyForMapMessage(clientId, regenerationTimestamp);
                gameClient.sendTCP(readyMessage);
                Log.info("MapRegenerationCoordinator", "Sent ready confirmation to server");
                
            } catch (Exception e) {
                Log.error("MapRegenerationCoordinator", "Failed to send ready confirmation", e);
                // Don't fail the entire process for this
            }
        }
    }
    
    private synchronized void transitionToPhase(RegenerationPhase newPhase) {
        if (cancelled) {
            return;
        }
        
        RegenerationPhase oldPhase = currentPhase;
        currentPhase = newPhase;
        
        Log.info("MapRegenerationCoordinator", String.format(
            "Phase transition: %s -> %s", 
            oldPhase != null ? oldPhase.getDisplayName() : "None",
            newPhase.getDisplayName()));
        
        notifyListeners(listener -> listener.onPhaseChanged(newPhase, newPhase.getDefaultMessage()));
        
        // Reset progress for new phase
        updateProgress(0.0f, newPhase.getDefaultMessage());
    }
    
    private void updateProgress(float progress, String message) {
        // Clamp progress to valid range
        final float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        
        notifyListeners(listener -> listener.onProgressChanged(clampedProgress));
        
        if (message != null && !message.trim().isEmpty()) {
            notifyListeners(listener -> listener.onPhaseChanged(currentPhase, message));
        }
    }
    
    private void completeRegeneration() {
        updateProgress(1.0f, "Regeneration complete!");
        
        Log.info("MapRegenerationCoordinator", "Map regeneration completed successfully - setting isActive=false");
        
        isActive = false;
        transitionToPhase(RegenerationPhase.COMPLETE);
        
        Log.info("MapRegenerationCoordinator", "Notifying listeners of completion");
        notifyListeners(listener -> listener.onCompleted());
        
        Log.info("MapRegenerationCoordinator", "Regeneration fully complete and inactive");
    }
    
    private void handleError(String errorMessage) {
        Log.error("MapRegenerationCoordinator", "Regeneration error: " + errorMessage);
        
        isActive = false;
        cancelled = true;
        
        notifyListeners(listener -> listener.onError(errorMessage));
    }
    
    private void notifyListeners(java.util.function.Consumer<RegenerationProgressListener> action) {
        for (RegenerationProgressListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                Log.error("MapRegenerationCoordinator", "Error notifying listener", e);
            }
        }
    }
}