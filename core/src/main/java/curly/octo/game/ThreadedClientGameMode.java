package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Threaded wrapper for ClientGameMode that runs network updates in a separate thread.
 * This prevents network I/O from blocking the main render thread.
 */
public class ThreadedClientGameMode implements GameMode {
    
    private final ClientGameMode clientGameMode;
    private Thread networkThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final boolean isWrappedInstance;
    private volatile Exception initializationException = null;
    
    private static final int TARGET_FPS = 60;
    private static final long FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS;
    
    public ThreadedClientGameMode(String host, java.util.Random random) {
        this.clientGameMode = new ClientGameMode(host, random);
        this.isWrappedInstance = false;
    }
    
    /**
     * Constructor that wraps an existing ClientGameMode instance.
     * This is used when the ClientGameMode is created by HostedGameMode.
     */
    public ThreadedClientGameMode(ClientGameMode clientGameMode) {
        this.clientGameMode = clientGameMode;
        this.isWrappedInstance = true;
        // Mark as initialized since we're wrapping an existing instance
        this.initialized.set(true);
        this.running.set(true);
    }
    
    @Override
    public void initialize() {
        // If this is a wrapped instance, don't create a separate thread
        if (isWrappedInstance) {
            Log.info("ThreadedClientGameMode", "Wrapped instance - no separate thread needed");
            return;
        }
        
        Log.info("ThreadedClientGameMode", "Starting initialization in separate thread");
        
        networkThread = new Thread(() -> {
            try {
                // Initialize the client game mode if it hasn't been initialized yet
                if (!clientGameMode.isActive()) {
                    clientGameMode.initialize();
                }
                initialized.set(true);
                running.set(true);
                
                Log.info("ThreadedClientGameMode", "Network thread initialized successfully");
                
                // Run the network update loop
                long lastTime = System.nanoTime();
                while (running.get()) {
                    long currentTime = System.nanoTime();
                    float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
                    lastTime = currentTime;
                    
                    // Cap delta time to prevent large jumps
                    if (deltaTime > 0.1f) {
                        deltaTime = 0.1f;
                    }
                    
                    try {
                        // Update the client game mode (handles networking and game world updates)
                        clientGameMode.update(deltaTime);
                        
                        // Sleep to maintain target FPS
                        long frameEndTime = System.nanoTime();
                        long frameTime = frameEndTime - currentTime;
                        long sleepTime = FRAME_TIME_NS - frameTime;
                        
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                        }
                        
                    } catch (IOException e) {
                        Log.error("ThreadedClientGameMode", "Network update error: " + e.getMessage());
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        Log.info("ThreadedClientGameMode", "Network thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } catch (Exception e) {
                Log.error("ThreadedClientGameMode", "Network thread initialization failed: " + e.getMessage());
                e.printStackTrace();
                initializationException = e;
                initialized.set(true); // Set to true even on failure so main thread doesn't hang
            }
            
            Log.info("ThreadedClientGameMode", "Network thread exiting");
        }, "NetworkUpdateThread");
        
        networkThread.setDaemon(false); // Keep JVM alive
        networkThread.start();
        
        // Wait for initialization to complete
        while (!initialized.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for network initialization", e);
            }
        }
        
        // Check if initialization failed
        if (initializationException != null) {
            throw new RuntimeException("Network initialization failed", initializationException);
        }
        
        Log.info("ThreadedClientGameMode", "Threaded client mode initialization complete");
    }
    
    @Override
    public void update(float deltaTime) throws IOException {
        // Network updates are handled in the separate thread
        // This method is intentionally empty to prevent blocking the render thread
    }
    
    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        // Delegate to the underlying client game mode
        clientGameMode.render(modelBatch, environment);
    }
    
    @Override
    public void resize(int width, int height) {
        clientGameMode.resize(width, height);
    }
    
    @Override
    public void dispose() {
        Log.info("ThreadedClientGameMode", "Disposing threaded client game mode...");
        
        // Signal the network thread to stop
        running.set(false);
        
        // Only manage thread lifecycle if this is not a wrapped instance
        if (!isWrappedInstance && networkThread != null && networkThread.isAlive()) {
            try {
                networkThread.interrupt();
                networkThread.join(5000); // Wait up to 5 seconds
                if (networkThread.isAlive()) {
                    Log.warn("ThreadedClientGameMode", "Network thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("ThreadedClientGameMode", "Interrupted while waiting for network thread to stop");
            }
        }
        
        // Only dispose the underlying client if this is not a wrapped instance
        // (wrapped instances are disposed by their parent HostedGameMode)
        if (!isWrappedInstance) {
            clientGameMode.dispose();
        }
        
        Log.info("ThreadedClientGameMode", "Threaded client game mode disposed");
    }
    
    @Override
    public boolean isActive() {
        return running.get() && clientGameMode.isActive();
    }
    
    @Override
    public GameWorld getGameWorld() {
        return clientGameMode.getGameWorld();
    }
    
    public GameClient getGameClient() {
        return clientGameMode.getGameClient();
    }
}