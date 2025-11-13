package curly.octo.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.esotericsoftware.minlog.Log;
import curly.octo.Main;
import curly.octo.common.DualLogger;

import static curly.octo.common.Constants.DEFAULT_SCREEN_HEIGHT;
import static curly.octo.common.Constants.DEFAULT_SCREEN_WIDTH;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    private static DualLogger dualLogger;

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.

        // Set up file logging before creating the application
        setupLogging();

        createApplication();
    }

    /**
     * Sets up dual logging (console + file) using the client's preferred name.
     */
    private static void setupLogging() {
        // Get client preferred name from config (loaded as static field in Main)
        String logFileName = Main.clientPreferredName;

        // Use default if preferred name is null or empty
        if (logFileName == null || logFileName.trim().isEmpty()) {
            logFileName = "client";
        }

        // Create dual logger with file name
        dualLogger = new DualLogger(logFileName + ".log");
        Log.setLogger(dualLogger);

        Log.info("Lwjgl3Launcher", "File logging enabled: logs/" + logFileName + ".log");
    }

    /**
     * Gets the DualLogger instance for log file switching.
     * @return The DualLogger, or null if not initialized
     */
    public static DualLogger getDualLogger() {
        return dualLogger;
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("CurlyOctoAdventure");
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

        configuration.setWindowedMode(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        //// They can also be loaded from the root of assets/ .
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
