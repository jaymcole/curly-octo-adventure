package curly.octo.game;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.esotericsoftware.minlog.Log;
import curly.octo.network.GameServer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Threaded wrapper for HostedGameMode that runs server updates in a separate thread.
 * This prevents server logic from blocking the main render thread.
 */
public class ThreadedHostedGameMode implements GameMode {
    
    private final HostedGameMode hostedGameMode;
    private Thread serverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile Exception initializationException = null;
    
    private static final int TARGET_FPS = 60;
    private static final long FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS;
    
    public ThreadedHostedGameMode(java.util.Random random) {
        this.hostedGameMode = new HostedGameMode(random);
    }
    
    @Override
    public void initialize() {
        Log.info("ThreadedHostedGameMode", "Starting initialization in separate thread");
        
        serverThread = new Thread(() -> {
            try {
                // Initialize the hosted game mode
                hostedGameMode.initialize();
                initialized.set(true);
                running.set(true);
                
                Log.info("ThreadedHostedGameMode", "Server thread initialized successfully");
                
                // Run the server update loop
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
                        // Update the hosted game mode
                        hostedGameMode.update(deltaTime);
                        
                        // Sleep to maintain target FPS
                        long frameEndTime = System.nanoTime();
                        long frameTime = frameEndTime - currentTime;
                        long sleepTime = FRAME_TIME_NS - frameTime;
                        
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                        }
                        
                    } catch (IOException e) {
                        Log.error("ThreadedHostedGameMode", "Server update error: " + e.getMessage());
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        Log.info("ThreadedHostedGameMode", "Server thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } catch (Exception e) {
                Log.error("ThreadedHostedGameMode", "Server thread initialization failed: " + e.getMessage());
                e.printStackTrace();
                initializationException = e;
                initialized.set(true); // Set to true even on failure so main thread doesn't hang
            }
            
            Log.info("ThreadedHostedGameMode", "Server thread exiting");
        }, "ServerUpdateThread");
        
        serverThread.setDaemon(false); // Keep JVM alive
        serverThread.start();
        
        // Wait for initialization to complete
        while (!initialized.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for server initialization", e);
            }
        }
        
        // Check if initialization failed
        if (initializationException != null) {
            throw new RuntimeException("Server initialization failed", initializationException);
        }
        
        Log.info("ThreadedHostedGameMode", "Threaded hosted mode initialization complete");
    }
    
    @Override
    public void update(float deltaTime) throws IOException {
        // Server updates are handled in the separate thread
        // This method is intentionally empty to prevent blocking the render thread
    }
    
    @Override
    public void render(ModelBatch modelBatch, Environment environment) {
        // Delegate to the underlying hosted game mode
        hostedGameMode.render(modelBatch, environment);
    }
    
    @Override
    public void resize(int width, int height) {
        hostedGameMode.resize(width, height);
    }
    
    @Override
    public void dispose() {
        Log.info("ThreadedHostedGameMode", "Disposing threaded hosted game mode...");
        
        // Signal the server thread to stop
        running.set(false);
        
        // Wait for the server thread to finish
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.interrupt();
                serverThread.join(5000); // Wait up to 5 seconds
                if (serverThread.isAlive()) {
                    Log.warn("ThreadedHostedGameMode", "Server thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("ThreadedHostedGameMode", "Interrupted while waiting for server thread to stop");
            }
        }
        
        // Dispose the underlying hosted game mode
        hostedGameMode.dispose();
        
        Log.info("ThreadedHostedGameMode", "Threaded hosted game mode disposed");
    }
    
    @Override
    public boolean isActive() {
        return running.get() && hostedGameMode.isActive();
    }
    
    @Override
    public GameWorld getGameWorld() {
        return hostedGameMode.getGameWorld();
    }
    
    public GameServer getGameServer() {
        return hostedGameMode.getGameServer();
    }
    
    public ClientGameMode getClientGameMode() {
        // Ensure initialization is complete before returning client mode
        if (!initialized.get()) {
            return null;
        }
        return hostedGameMode.getClientGameMode();
    }
}