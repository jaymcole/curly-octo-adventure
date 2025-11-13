package curly.octo.common;

import com.esotericsoftware.minlog.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Custom MinLog Logger that writes to both console and a file.
 * Thread-safe and supports dynamic log file switching.
 * Singleton pattern for easy access from anywhere in the application.
 *
 * Supports dual-file mode for hosted servers where both client and server
 * run in the same JVM - logs are routed based on category.
 */
public class DualLogger extends Log.Logger {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();
    private static DualLogger instance;

    private PrintWriter clientFileWriter;  // Primary file (or client file in dual mode)
    private PrintWriter serverFileWriter;  // Server file (only in dual mode)
    private String clientLogFile;
    private String serverLogFile;
    private boolean isDualFileMode = false;

    /**
     * Creates a dual logger that writes to both console and file.
     * @param logFileName The name of the log file (e.g., "Alice.log")
     */
    public DualLogger(String logFileName) {
        instance = this; // Set singleton instance
        setLogFile(logFileName);
    }

    /**
     * Gets the singleton instance of DualLogger.
     * @return The DualLogger instance, or null if not yet initialized
     */
    public static DualLogger getInstance() {
        return instance;
    }

    /**
     * Sets or changes the log file.
     * Closes the previous file (if any) and opens a new one.
     * Disables dual-file mode if it was active.
     * @param logFileName The new log file name
     */
    public void setLogFile(String logFileName) {
        synchronized (LOCK) {
            // Close existing file writers if open
            closeFileWriters();

            // Disable dual-file mode
            isDualFileMode = false;
            serverFileWriter = null;
            serverLogFile = null;

            clientLogFile = logFileName;
            clientFileWriter = openLogFile(logFileName, "client");
        }
    }

    /**
     * Enables dual-file mode for hosted servers.
     * Client logs go to the existing client file, server logs go to a new server file.
     * ERROR level logs go to both files.
     *
     * @param serverLogFileName The server log file name (e.g., "server-Alice.log")
     */
    public void enableDualFileMode(String serverLogFileName) {
        synchronized (LOCK) {
            if (isDualFileMode) {
                Log.warn("DualLogger", "Dual-file mode already enabled");
                return;
            }

            Log.info("DualLogger", "Enabling dual-file mode - server logs: " + serverLogFileName);

            serverLogFile = serverLogFileName;
            serverFileWriter = openLogFile(serverLogFileName, "server");

            isDualFileMode = true;

            Log.info("DualLogger", "Dual-file mode enabled: client=" + clientLogFile + ", server=" + serverLogFile);
        }
    }

    /**
     * Opens a log file and returns a PrintWriter.
     * @param logFileName The log file name
     * @param type "client" or "server" for logging purposes
     * @return PrintWriter for the file, or null if failed
     */
    private PrintWriter openLogFile(String logFileName, String type) {
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // Open new log file in append mode
            File logFile = new File(logsDir, logFileName);
            PrintWriter writer = new PrintWriter(new FileWriter(logFile, true));

            // Write header to new log file
            writer.println("=".repeat(80));
            writer.println("Log session started: " + DATE_FORMAT.format(new Date()));
            writer.println("Log file: " + logFile.getAbsolutePath());
            writer.println("Type: " + type);
            writer.println("=".repeat(80));
            writer.flush();

            System.out.println("DualLogger: " + type + " logging to " + logFile.getAbsolutePath());
            return writer;
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create " + type + " log file '" + logFileName + "': " + e.getMessage());
            e.printStackTrace();
            return null; // Disable file logging if we can't create the file
        }
    }

    /**
     * Closes all file writers.
     */
    private void closeFileWriters() {
        if (clientFileWriter != null) {
            try {
                clientFileWriter.println("=".repeat(80));
                clientFileWriter.println("Log session ended: " + DATE_FORMAT.format(new Date()));
                clientFileWriter.println("=".repeat(80));
                clientFileWriter.close();
            } catch (Exception e) {
                System.err.println("Error closing client log file: " + e.getMessage());
            }
            clientFileWriter = null;
        }

        if (serverFileWriter != null) {
            try {
                serverFileWriter.println("=".repeat(80));
                serverFileWriter.println("Log session ended: " + DATE_FORMAT.format(new Date()));
                serverFileWriter.println("=".repeat(80));
                serverFileWriter.close();
            } catch (Exception e) {
                System.err.println("Error closing server log file: " + e.getMessage());
            }
            serverFileWriter = null;
        }
    }

    @Override
    public void log(int level, String category, String message, Throwable ex) {
        synchronized (LOCK) {
            // Format the log message
            String levelStr = getLevelString(level);
            String timestamp = DATE_FORMAT.format(new Date());

            // Write to console (original MinLog behavior - no timestamp)
            StringBuilder consoleMsg = new StringBuilder();
            if (category != null) {
                consoleMsg.append("[").append(category).append("] ");
            }
            consoleMsg.append(message);

            switch (level) {
                case Log.LEVEL_ERROR:
                    System.err.println(consoleMsg);
                    if (ex != null) ex.printStackTrace(System.err);
                    break;
                case Log.LEVEL_WARN:
                    System.out.println(consoleMsg);
                    break;
                default:
                    System.out.println(consoleMsg);
                    break;
            }

            // Write to file(s) (with timestamp and level)
            StringBuilder fileMsg = new StringBuilder();
            fileMsg.append(timestamp).append(" ");
            fileMsg.append("[").append(levelStr).append("] ");
            if (category != null) {
                fileMsg.append("[").append(category).append("] ");
            }
            fileMsg.append(message);

            if (isDualFileMode) {
                // Dual-file mode: route based on category
                boolean isServerLog = isServerCategory(category);
                boolean isErrorLog = (level == Log.LEVEL_ERROR);

                // Write to appropriate file(s)
                if (isServerLog && serverFileWriter != null) {
                    serverFileWriter.println(fileMsg);
                    if (ex != null) ex.printStackTrace(serverFileWriter);
                    serverFileWriter.flush();
                }

                if (!isServerLog && clientFileWriter != null) {
                    clientFileWriter.println(fileMsg);
                    if (ex != null) ex.printStackTrace(clientFileWriter);
                    clientFileWriter.flush();
                }

                // ERROR logs go to BOTH files for visibility
                if (isErrorLog) {
                    if (isServerLog && clientFileWriter != null) {
                        clientFileWriter.println(fileMsg);
                        if (ex != null) ex.printStackTrace(clientFileWriter);
                        clientFileWriter.flush();
                    } else if (!isServerLog && serverFileWriter != null) {
                        serverFileWriter.println(fileMsg);
                        if (ex != null) ex.printStackTrace(serverFileWriter);
                        serverFileWriter.flush();
                    }
                }
            } else {
                // Single-file mode: write to client file only
                if (clientFileWriter != null) {
                    clientFileWriter.println(fileMsg);
                    if (ex != null) ex.printStackTrace(clientFileWriter);
                    clientFileWriter.flush();
                }
            }
        }
    }

    /**
     * Determines if a log category belongs to server-side code.
     * @param category The log category (class name)
     * @return true if this is a server category, false otherwise
     */
    private boolean isServerCategory(String category) {
        if (category == null) {
            return false; // Unknown categories go to client by default
        }

        // Server-side categories (case-insensitive matching)
        String lowerCategory = category.toLowerCase();
        return lowerCategory.contains("server") ||
               lowerCategory.contains("hostedgamemode") ||
               lowerCategory.contains("networklistener") ||
               lowerCategory.contains("bulktransfer");
    }

    /**
     * Converts MinLog level integer to string.
     */
    private String getLevelString(int level) {
        switch (level) {
            case Log.LEVEL_ERROR: return "ERROR";
            case Log.LEVEL_WARN: return "WARN";
            case Log.LEVEL_INFO: return "INFO";
            case Log.LEVEL_DEBUG: return "DEBUG";
            case Log.LEVEL_TRACE: return "TRACE";
            default: return "UNKNOWN";
        }
    }

    /**
     * Closes all log files.
     * Call this before application shutdown.
     */
    public void close() {
        synchronized (LOCK) {
            closeFileWriters();
        }
    }
}
