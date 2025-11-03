package curly.octo.server;

import curly.octo.client.ClientGameMode;
import curly.octo.common.GameMode;
import curly.octo.common.GameWorld;
import curly.octo.common.Constants;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.esotericsoftware.minlog.Log;

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

    private static final int TARGET_FPS = Constants.GAME_TARGET_FPS;
    private static final long FRAME_TIME_NS = Constants.GAME_FRAME_TIME_NS;

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
    public void update(float deltaTime) throws IOException {}

    @Override
    public void render(ModelBatch modelBatch, Environment environment) {}

    @Override
    public void resize(int width, int height) {
        hostedGameMode.resize(width, height);
    }

    @Override
    public void dispose() {
        long startTime = System.currentTimeMillis();
        Log.info("ThreadedHostedGameMode", "Disposing threaded hosted game mode...");

        // Signal the server thread to stop
        long threadStopStart = System.currentTimeMillis();
        running.set(false);

        // Wait for the server thread to finish
        if (serverThread != null && serverThread.isAlive()) {
            try {
                serverThread.interrupt();
                serverThread.join(1000); // Wait up to 1 second (reduced from 5)
                if (serverThread.isAlive()) {
                    Log.warn("ThreadedHostedGameMode", "Server thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("ThreadedHostedGameMode", "Interrupted while waiting for server thread to stop");
            }
        }
        long threadStopEnd = System.currentTimeMillis();
        Log.info("ThreadedHostedGameMode", "Server thread stopped in " + (threadStopEnd - threadStopStart) + "ms");

        // Dispose the underlying hosted game mode
        long hostedModeStart = System.currentTimeMillis();
        hostedGameMode.dispose();
        long hostedModeEnd = System.currentTimeMillis();
        Log.info("ThreadedHostedGameMode", "Hosted game mode disposed in " + (hostedModeEnd - hostedModeStart) + "ms");

        long totalTime = System.currentTimeMillis() - startTime;
        Log.info("ThreadedHostedGameMode", "Threaded hosted game mode disposed in " + totalTime + "ms");
    }

    @Override
    public boolean isActive() {
        return running.get() && hostedGameMode.isActive();
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

    /**
     * Triggers map regeneration with a random seed.
     * This is a debug/admin function for testing map regeneration.
     */
    public void debugRegenerateMap() {
        if (!initialized.get()) {
            Log.warn("ThreadedHostedGameMode", "Cannot regenerate map - not initialized");
            return;
        }
        hostedGameMode.debugRegenerateMap();
    }

    /**
     * Triggers map regeneration with a specific seed.
     *
     * @param seed The seed for the new map
     * @param reason Optional reason for regeneration
     */
    public void regenerateMapWithSeed(long seed, String reason) {
        if (!initialized.get()) {
            Log.warn("ThreadedHostedGameMode", "Cannot regenerate map - not initialized");
            return;
        }
        hostedGameMode.regenerateMapWithSeed(seed, reason);
    }
}
