package curly.octo.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;
import org.apache.commons.lang3.StringUtils;

/**
 * UI screen displayed during map regeneration process.
 * Shows progress, status messages, and information about the regeneration.
 *
 * NOTE: This class uses static fields and methods for thread-safe, immediate updates
 * without LibGDX postRunnable() delays during map chunk downloads.
 */
public class MapRegenerationScreen implements StateScreen {

    private final Skin skin;
    private Table mainTable;

    // Static UI Components for thread-safe access
    private static Label staticTitleLabel;
    private static Label staticStateLabel;
    private static Label staticStatusLabel;
    private static ProgressBar staticProgressBar;
    private static Label staticProgressLabel;
    private static Label staticTimeLabel;
    private static Table staticInfoTable;

    // Static individual stage progress bars
    private static ProgressBar staticCleanupProgressBar;
    private static ProgressBar staticDownloadingProgressBar;
    private static ProgressBar staticRebuildingProgressBar;
    private static ProgressBar staticCompleteProgressBar;

    // Static stage labels with status indicators
    private static Label staticCleanupStatusLabel;
    private static Label staticDownloadingStatusLabel;
    private static Label staticRebuildingStatusLabel;
    private static Label staticCompleteStatusLabel;

    // Static progress tracking
    private static long staticDisplayStartTime;
    private static StateContext staticLastContext;
    private static Skin staticSkin;

    public MapRegenerationScreen(Skin skin) {
        this.skin = skin;
        // Initialize static references when first screen is created
        if (staticSkin == null) {
            staticSkin = skin;
            staticDisplayStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public Table createUI() {
        if (mainTable == null) {
            buildUI();
        }
        return mainTable;
    }

    private void buildUI() {
        mainTable = new Table();
        mainTable.pad(40);

        // Main container with background
        Table containerTable = new Table();
        containerTable.pad(30);

        // Try to set a background if the skin supports it
        try {
            if (skin.has("window", Window.WindowStyle.class)) {
                Window.WindowStyle windowStyle = skin.get("window", Window.WindowStyle.class);
                if (windowStyle.background != null) {
                    containerTable.setBackground(windowStyle.background);
                }
            }
        } catch (Exception e) {
            // Fallback - no background
            Log.debug("MapRegenerationScreen", "No background style available");
        }

        // Title - use fallback style if "title" doesn't exist
        try {
            staticTitleLabel = new Label("Map Regeneration", skin, "title");
        } catch (Exception e) {
            try {
                staticTitleLabel = new Label("Map Regeneration", skin, "subtitle");
            } catch (Exception e2) {
                staticTitleLabel = new Label("Map Regeneration", skin);
            }
        }
        staticTitleLabel.setColor(Color.WHITE);
        staticTitleLabel.setAlignment(Align.center);
        containerTable.add(staticTitleLabel).colspan(2).padBottom(20).row();

        // Current state - use fallback style if "subtitle" doesn't exist
        try {
            staticStateLabel = new Label("Initializing...", skin, "subtitle");
        } catch (Exception e) {
            staticStateLabel = new Label("Initializing...", skin);
        }
        staticStateLabel.setColor(Color.LIGHT_GRAY);
        staticStateLabel.setAlignment(Align.center);
        containerTable.add(staticStateLabel).colspan(2).padBottom(15).row();

        // Progress bar
        staticProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin);
        staticProgressBar.setValue(0f);
        containerTable.add(staticProgressBar).width(400).height(20).colspan(2).padBottom(10).row();

        // Progress percentage
        staticProgressLabel = new Label("0%", skin);
        staticProgressLabel.setColor(Color.WHITE);
        staticProgressLabel.setAlignment(Align.center);
        containerTable.add(staticProgressLabel).colspan(2).padBottom(15).row();

        // Individual stage progress section
        createStageProgressSection(containerTable);

        // Status message
        staticStatusLabel = new Label("Starting map regeneration...", skin);
        staticStatusLabel.setColor(Color.CYAN);
        staticStatusLabel.setAlignment(Align.center);
        staticStatusLabel.setWrap(true);
        containerTable.add(staticStatusLabel).width(400).colspan(2).padBottom(20).row();

        // Information section (moved below stage progress)
        createInfoSection(containerTable);

        // Time display
        staticTimeLabel = new Label("Time: 0s", skin);
        staticTimeLabel.setColor(Color.GRAY);
        staticTimeLabel.setAlignment(Align.center);
        containerTable.add(staticTimeLabel).colspan(2).padTop(10).row();

        // Add container to main table
        mainTable.add(containerTable).expand().center();
    }

    private void createStageProgressSection(Table containerTable) {
        // Stage progress title
        Label stageTitle;
        try {
            stageTitle = new Label("Progress Stages", skin, "subtitle");
        } catch (Exception e) {
            stageTitle = new Label("Progress Stages", skin);
        }
        stageTitle.setColor(Color.CYAN);
        stageTitle.setAlignment(Align.center);
        containerTable.add(stageTitle).colspan(2).padBottom(10).row();

        // Create table for stage progress bars
        Table stageTable = new Table();
        stageTable.pad(10);

        // Cleanup stage
        addStageProgressRow(stageTable, "Cleanup", staticCleanupProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           staticCleanupStatusLabel = new Label("Waiting...", skin));

        // Downloading stage
        addStageProgressRow(stageTable, "Downloading", staticDownloadingProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           staticDownloadingStatusLabel = new Label("Waiting...", skin));

        // Rebuilding stage
        addStageProgressRow(stageTable, "Rebuilding", staticRebuildingProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           staticRebuildingStatusLabel = new Label("Waiting...", skin));

        // Complete stage
        addStageProgressRow(stageTable, "Finalizing", staticCompleteProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           staticCompleteStatusLabel = new Label("Waiting...", skin));

        containerTable.add(stageTable).colspan(2).padBottom(20).row();
    }

    private void addStageProgressRow(Table table, String stageName, ProgressBar progressBar, Label statusLabel) {
        // Stage name label
        Label nameLabel = new Label(stageName + ":", skin);
        nameLabel.setColor(Color.LIGHT_GRAY);
        table.add(nameLabel).left().width(80).padRight(10);

        // Progress bar
        progressBar.setValue(0f);
        table.add(progressBar).width(200).height(15).padRight(10);

        // Status label
        statusLabel.setColor(Color.WHITE);
        statusLabel.setAlignment(Align.left);
        table.add(statusLabel).left().expandX().row();
    }

    private void createInfoSection(Table containerTable) {
        // Information panel - use fallback style if "subtitle" doesn't exist
        Label infoTitle;
        try {
            infoTitle = new Label("Information", skin, "subtitle");
        } catch (Exception e) {
            infoTitle = new Label("Information", skin);
        }
        infoTitle.setColor(Color.YELLOW);
        infoTitle.setAlignment(Align.center);
        containerTable.add(infoTitle).colspan(2).padBottom(10).row();

        staticInfoTable = new Table();
        staticInfoTable.pad(10);

        // Add some helpful information
        addInfoRow("What's happening:", "The game world is being regenerated");
        addInfoRow("Connection:", "Maintained with server");

        containerTable.add(staticInfoTable).colspan(2).padBottom(15).row();
    }

    private void addInfoRow(String label, String value) {
        // Use static skin reference
        Skin currentSkin = staticSkin != null ? staticSkin : skin;
        Label labelWidget = new Label(label, currentSkin);
        labelWidget.setColor(Color.LIGHT_GRAY);

        Label valueWidget = new Label(value, currentSkin);
        valueWidget.setColor(Color.WHITE);

        staticInfoTable.add(labelWidget).left().padRight(10);
        staticInfoTable.add(valueWidget).left().row();
    }

    @Override
    public void updateContext(StateContext context) {
        // Delegate to static method for consistency
        updateContextStatic(context);
    }

    /**
     * Static version of updateContext for direct thread-safe updates
     */
    public static void updateContextStatic(StateContext context) {
        if (context == null) {
            return;
        }

        staticLastContext = context;

        // Update all UI components based on current state
        updateTitleStatic(context);
        updateStateDisplayStatic(context);
        updateProgressStatic(context);
        updateStatusMessageStatic(context);
        updateTimeDisplayStatic(context);
        updateInfoPanelStatic(context);
    }

    /**
     * Updates the title based on whether this is initial generation or regeneration
     */
    private static void updateTitleStatic(StateContext context) {
        if (staticTitleLabel != null && context != null) {
            Boolean isInitialGeneration = context.getStateData("is_initial_generation", Boolean.class);
            String titleText;
            if (isInitialGeneration != null && isInitialGeneration) {
                titleText = "Generating Initial Map";
            } else {
                titleText = "Map Regeneration";
            }

            // Ensure the title text is not empty
            if (titleText != null && !titleText.trim().isEmpty()) {
                safeSetText(staticTitleLabel, titleText);
            } else {
                safeSetText(staticTitleLabel, "Map Processing");
            }
        }
    }

    private void updateStateDisplay(StateContext context) {
        updateStateDisplayStatic(context);
    }

    private static void updateStateDisplayStatic(StateContext context) {
        GameState currentState = context.getCurrentState();
        if (staticStateLabel != null) {
            String stateText = getStateDisplayTextStatic(currentState);
            staticStateLabel.setText(stateText);
        }
    }

    private String getStateDisplayText(GameState state) {
        return getStateDisplayTextStatic(state);
    }

    private void updateProgress(StateContext context) {
        updateProgressStatic(context);
    }

    private static void updateProgressStatic(StateContext context) {
        if (staticProgressBar != null && staticProgressLabel != null) {
            GameState currentState = context.getCurrentState();
            float stateProgress = context.getProgress();

            // Calculate overall progress across all stages
            float overallProgress = calculateOverallProgressStatic(currentState, stateProgress);
            staticProgressBar.setValue(overallProgress);
            Log.info("MapRegenerationScreen", "PROGRESS BAR UPDATED - State: " + currentState + ", StateProgress: " + stateProgress + ", Overall: " + overallProgress + ", ProgressBarValue: " + staticProgressBar.getValue());

            int percentage = Math.round(overallProgress * 100);
            staticProgressLabel.setText(percentage + "%");

            // Update individual stage progress bars
            updateStageProgressBarsStatic(currentState, stateProgress);
        }
    }

    private float calculateOverallProgress(GameState state, float stateProgress) {
        return calculateOverallProgressStatic(state, stateProgress);
    }

    private void updateStageProgressBars(StateContext context, GameState currentState, float stateProgress) {
        updateStageProgressBarsStatic(currentState, stateProgress);
    }

    private void updateStageProgress(ProgressBar progressBar, Label statusLabel,
                                   GameState targetState, GameState currentState,
                                   float stateProgress, StateContext context) {
        updateStageProgressStatic(progressBar, statusLabel, targetState, currentState, stateProgress);
    }

    private String getStageNameForState(GameState state) {
        return getStageNameForStateStatic(state);
    }

    private void updateStatusMessage(StateContext context) {
        updateStatusMessageStatic(context);
    }

    private static void updateStatusMessageStatic(StateContext context) {
        if (staticStatusLabel != null) {
            String message = context.getStatusMessage();
            if (message != null && !message.trim().isEmpty()) {
                safeSetText(staticStatusLabel, message);
            } else {
                safeSetText(staticStatusLabel, "Processing...");
            }
        }
    }

    private void updateTimeDisplay(StateContext context) {
        updateTimeDisplayStatic(context);
    }

    private static void updateTimeDisplayStatic(StateContext context) {
        if (staticTimeLabel != null) {
            long elapsedSeconds = (System.currentTimeMillis() - staticDisplayStartTime) / 1000;
            safeSetText(staticTimeLabel, "Time: " + elapsedSeconds + "s");
        }
    }

    private void updateInfoPanel(StateContext context) {
        updateInfoPanelStatic(context);
    }

    private static void updateInfoPanelStatic(StateContext context) {
        // Update info based on current state and context data
        if (staticInfoTable != null) {
            // Clear existing info
            staticInfoTable.clear();

            // Check if this is initial generation
            Boolean isInitialGeneration = context.getStateData("is_initial_generation", Boolean.class);
            boolean isInitial = isInitialGeneration != null && isInitialGeneration;

            if (isInitial) {
                // Messages for initial map generation
                addInfoRowStatic("What's happening:", "Creating your first game world");
                addInfoRowStatic("Status:", "Generating map for host startup");
                addInfoRowStatic("Connection:", "Setting up multiplayer server");
            } else {
                // Messages for map regeneration
                addInfoRowStatic("What's happening:", "The game world is being regenerated");
                addInfoRowStatic("Connection:", "Maintained with server");
            }

            // Show regeneration reason if available
            String reason = context.getStateData("regeneration_reason", String.class);
            if (reason != null && !reason.trim().isEmpty()) {
                String reasonLabel = isInitial ? "Purpose:" : "Reason:";
                addInfoRowStatic(reasonLabel, reason);
            }

            // Show new map seed if available
            Long newSeed = context.getStateData("new_map_seed", Long.class);
            if (newSeed != null) {
                addInfoRowStatic("New Seed:", String.valueOf(newSeed));
            }

            // Add stage-specific detailed information
            GameState currentState = context.getCurrentState();
            switch (currentState) {
                case MAP_REGENERATION_DOWNLOADING:
                    // Show detailed download progress
                    Integer totalChunks = context.getStateData("total_chunks", Integer.class);
                    Integer chunksReceived = context.getStateData("chunks_received", Integer.class);
                    Long totalBytes = context.getStateData("total_bytes", Long.class);
                    Long bytesReceived = context.getStateData("bytes_received", Long.class);

                    if (totalChunks != null && chunksReceived != null) {
                        addInfoRowStatic("Chunks:", chunksReceived + "/" + totalChunks);
                    }
                    if (totalBytes != null && bytesReceived != null) {
                        float mbTotal = totalBytes / (1024f * 1024f);
                        float mbReceived = bytesReceived / (1024f * 1024f);
                        addInfoRowStatic("Data:", String.format("%.1f/%.1f MB", mbReceived, mbTotal));
                    }
                    break;

                case MAP_REGENERATION_REBUILDING:
                    // Show timing information for rebuilding
                    Long rebuildStartTime = context.getStateData("rebuilding_start_time", Long.class);
                    if (rebuildStartTime != null) {
                        long elapsed = (System.currentTimeMillis() - rebuildStartTime) / 1000;
                        addInfoRowStatic("Build Time:", elapsed + "s");
                    }
                    break;

                case MAP_REGENERATION_COMPLETE:
                    // Show completion summary
                    addInfoRowStatic("Status:", "Ready to resume gameplay");
                    break;
            }
        }
    }

    private static void addInfoRowStatic(String label, String value) {
        try {
            // Use static skin reference
            Skin currentSkin = staticSkin;
            if (currentSkin == null) {
                Log.warn("MapRegenerationScreen", "Static skin not initialized, cannot add info row");
                return;
            }

            // Ensure non-empty strings
            String safeLabel = (label != null && !label.trim().isEmpty()) ? label : "Info:";
            String safeValue = (value != null && !value.trim().isEmpty()) ? value : "N/A";

            Log.debug("MapRegenerationScreen", "Adding info row - Label: '" + safeLabel + "' (length: " + safeLabel.length() + "), Value: '" + safeValue + "' (length: " + safeValue.length() + ")");

            Label labelWidget = new Label("Loading...", currentSkin);
            labelWidget.setColor(Color.LIGHT_GRAY);
            safeSetText(labelWidget, safeLabel);

            Label valueWidget = new Label("Loading...", currentSkin);
            valueWidget.setColor(Color.WHITE);
            safeSetText(valueWidget, safeValue);

            staticInfoTable.add(labelWidget).left().padRight(10);
            staticInfoTable.add(valueWidget).left().row();
        } catch (Exception e) {
            Log.error("MapRegenerationScreen", "Error in addInfoRowStatic: " + e.getMessage(), e);
        }
    }

    @Override
    public String getTitle() {
        return "Map Regeneration";
    }

    @Override
    public void dispose() {
        // Nothing specific to dispose for this screen
        // The skin is managed by StateUI
    }

    /**
     * Reset the display time (called when screen is first shown)
     */
    public void resetDisplayTime() {
        staticDisplayStartTime = System.currentTimeMillis();
    }

    /**
     * Get the last updated context
     */
    public StateContext getLastContext() {
        return staticLastContext;
    }

    // =============== STATIC METHODS FOR THREAD-SAFE DIRECT UPDATES ===============

    /**
     * Safe method to set text on a Label, working around LibGDX StringBuilder issues
     */
    private static void safeSetText(Label label, String text) {
        if (label == null) return;

        try {
            // Ensure text is never null or empty
            String safeText = (text != null && !text.trim().isEmpty()) ? text : "Loading...";

            // Create a new Java String to avoid LibGDX StringBuilder issues
            String finalText = new String(safeText.toCharArray());

            // Use postRunnable to ensure main thread execution
            com.badlogic.gdx.Gdx.app.postRunnable(() -> {
                try {
                    label.setText(finalText);
                } catch (Exception e) {
                    Log.error("MapRegenerationScreen", "Failed to set label text to: '" + finalText + "': " + e.getMessage());
                    // Final fallback - set a simple safe string
                    try {
                        label.setText("...");
                    } catch (Exception e2) {
                        Log.error("MapRegenerationScreen", "Even fallback setText failed: " + e2.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.error("MapRegenerationScreen", "Error in safeSetText: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure static UI components are initialized (called when UI is first created)
     */
    public static void ensureStaticInitialization() {
        // This will be automatically called when the first MapRegenerationScreen is created
        // Static components are initialized in the buildUI() method
        Log.info("MapRegenerationScreen", "Static UI components initialized: " + (staticProgressBar != null));
    }

    /**
     * Static method to update progress directly from any thread (especially network thread)
     * This bypasses the postRunnable delay for immediate UI updates
     */
    public static void updateProgressDirect(GameState currentState, float stateProgress, String message) {
        if (staticProgressBar != null && staticProgressLabel != null) {
            try {
                // Calculate overall progress across all stages
                float overallProgress = calculateOverallProgressStatic(currentState, stateProgress);
                staticProgressBar.setValue(overallProgress);

                int percentage = Math.round(overallProgress * 100);
                String percentageText = percentage + "%";
                Log.debug("MapRegenerationScreen", "Setting progress label to: '" + percentageText + "' (length: " + percentageText.length() + ")");
                safeSetText(staticProgressLabel, percentageText);

                // Update status message if provided
                if (message != null && !message.trim().isEmpty() && staticStatusLabel != null) {
                    Log.debug("MapRegenerationScreen", "Setting status label to: '" + message + "' (length: " + message.length() + ")");
                    safeSetText(staticStatusLabel, message);
                } else if (staticStatusLabel != null) {
                    Log.debug("MapRegenerationScreen", "Setting status label to default: 'Processing...'");
                    safeSetText(staticStatusLabel, "Processing...");
                }

                // Update state display
                if (staticStateLabel != null) {
                    String stateText = getStateDisplayTextStatic(currentState);
                    if (stateText != null && !stateText.trim().isEmpty()) {
                        Log.debug("MapRegenerationScreen", "Setting state label to: '" + stateText + "' (length: " + stateText.length() + ")");
                        safeSetText(staticStateLabel, stateText);
                    } else {
                        Log.debug("MapRegenerationScreen", "Setting state label to default: 'Unknown State'");
                        safeSetText(staticStateLabel, "Unknown State");
                    }
                }

                // Update individual stage progress bars
                updateStageProgressBarsStatic(currentState, stateProgress);

                Log.info("MapRegenerationScreen", "DIRECT PROGRESS UPDATE - State: " + currentState +
                         ", StateProgress: " + stateProgress + ", Overall: " + overallProgress +
                         ", ProgressBarValue: " + staticProgressBar.getValue());
            } catch (Exception e) {
                Log.error("MapRegenerationScreen", "Error in updateProgressDirect: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Static method to update chunk progress specifically during map downloads
     */
    public static void updateChunkProgress(int chunksReceived, int totalChunks, String message) {
        try {
            if (totalChunks > 0) {
                float chunkProgress = (float) chunksReceived / totalChunks;
                updateProgressDirect(GameState.MAP_REGENERATION_DOWNLOADING, chunkProgress, message);

                // Update downloading stage specifically
                if (staticDownloadingProgressBar != null && staticDownloadingStatusLabel != null) {
                    staticDownloadingProgressBar.setValue(chunkProgress);
                    String chunksReceivedFormatted = StringUtils.leftPad(chunksReceived + "", (totalChunks + "").length());
                    String chunkText = String.format("%s/%d chunks", chunksReceivedFormatted, totalChunks);
                    Log.debug("MapRegenerationScreen", "Setting downloading status label to: '" + chunkText + "' (length: " + chunkText.length() + ")");
                    if (!chunkText.isEmpty()) {
                        safeSetText(staticDownloadingStatusLabel, chunkText);
                    } else {
                        Log.debug("MapRegenerationScreen", "Chunk text was empty, using fallback");
                        safeSetText(staticDownloadingStatusLabel, "0/0 chunks");
                    }
                    staticDownloadingStatusLabel.setColor(Color.CYAN);
                }
            } else {
                Log.warn("MapRegenerationScreen", "updateChunkProgress called with totalChunks <= 0: " + totalChunks);
                // Set safe fallback values
                if (staticDownloadingProgressBar != null && staticDownloadingStatusLabel != null) {
                    staticDownloadingProgressBar.setValue(0.0f);
                    Log.debug("MapRegenerationScreen", "Setting downloading status label to fallback: 'Waiting...'");
                    safeSetText(staticDownloadingStatusLabel, "Waiting...");
                    staticDownloadingStatusLabel.setColor(Color.GRAY);
                }
            }
        } catch (Exception e) {
            Log.error("MapRegenerationScreen", "Error in updateChunkProgress: " + e.getMessage(), e);
        }
    }

    private static float calculateOverallProgressStatic(GameState state, float stateProgress) {
        switch (state) {
            case MAP_REGENERATION_CLEANUP:
                return 0.0f + (stateProgress * 0.15f);  // 0% → 15%
            case MAP_REGENERATION_DOWNLOADING:
                return 0.15f + (stateProgress * 0.60f); // 15% → 75%
            case MAP_REGENERATION_REBUILDING:
                return 0.75f + (stateProgress * 0.15f); // 75% → 90%
            case MAP_REGENERATION_COMPLETE:
                return 0.90f + (stateProgress * 0.10f); // 90% → 100%
            default:
                return stateProgress;
        }
    }

    private static String getStateDisplayTextStatic(GameState state) {
        if (state == null) {
            return "Unknown State";
        }

        switch (state) {
            case MAP_REGENERATION_CLEANUP:
                return "Cleaning Up Resources";
            case MAP_REGENERATION_DOWNLOADING:
                return "Downloading New Map";
            case MAP_REGENERATION_REBUILDING:
                return "Rebuilding World";
            case MAP_REGENERATION_COMPLETE:
                return "Finalizing";
            default:
                String displayName = state.getDisplayName();
                return (displayName != null && !displayName.trim().isEmpty()) ? displayName : "Unknown State";
        }
    }

    private static void updateStageProgressBarsStatic(GameState currentState, float stateProgress) {
        // Update cleanup stage
        updateStageProgressStatic(staticCleanupProgressBar, staticCleanupStatusLabel,
                                  GameState.MAP_REGENERATION_CLEANUP, currentState, stateProgress);

        // Update downloading stage
        updateStageProgressStatic(staticDownloadingProgressBar, staticDownloadingStatusLabel,
                                  GameState.MAP_REGENERATION_DOWNLOADING, currentState, stateProgress);

        // Update rebuilding stage
        updateStageProgressStatic(staticRebuildingProgressBar, staticRebuildingStatusLabel,
                                  GameState.MAP_REGENERATION_REBUILDING, currentState, stateProgress);

        // Update complete stage
        updateStageProgressStatic(staticCompleteProgressBar, staticCompleteStatusLabel,
                                  GameState.MAP_REGENERATION_COMPLETE, currentState, stateProgress);
    }

    private static void updateStageProgressStatic(ProgressBar progressBar, Label statusLabel,
                                                  GameState targetState, GameState currentState,
                                                  float stateProgress) {
        if (progressBar == null || statusLabel == null) return;

        try {
            String stageName = getStageNameForStateStatic(targetState);

            if (currentState.ordinal() > targetState.ordinal()) {
                // Stage is complete
                progressBar.setValue(1.0f);
                Log.debug("MapRegenerationScreen", "Setting " + stageName + " stage label to: '✓ Complete'");
                safeSetText(statusLabel, "✓ Complete");
                statusLabel.setColor(Color.GREEN);
            } else if (currentState == targetState) {
                // Stage is currently active
                progressBar.setValue(stateProgress);
                Log.debug("MapRegenerationScreen", "Setting " + stageName + " stage label to: 'In progress...'");
                safeSetText(statusLabel, "In progress...");
                statusLabel.setColor(Color.CYAN);
            } else {
                // Stage is waiting
                progressBar.setValue(0.0f);
                Log.debug("MapRegenerationScreen", "Setting " + stageName + " stage label to: 'Waiting...'");
                safeSetText(statusLabel, "Waiting...");
                statusLabel.setColor(Color.GRAY);
            }
        } catch (Exception e) {
            Log.error("MapRegenerationScreen", "Error in updateStageProgressStatic for " + targetState + ": " + e.getMessage(), e);
        }
    }

    private static String getStageNameForStateStatic(GameState state) {
        switch (state) {
            case MAP_REGENERATION_CLEANUP: return "Cleanup";
            case MAP_REGENERATION_DOWNLOADING: return "Downloading";
            case MAP_REGENERATION_REBUILDING: return "Rebuilding";
            case MAP_REGENERATION_COMPLETE: return "Complete";
            default: return "Unknown";
        }
    }
}
