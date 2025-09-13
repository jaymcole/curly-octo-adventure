package curly.octo.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.state.GameState;
import curly.octo.game.state.StateContext;

/**
 * UI screen displayed during map regeneration process.
 * Shows progress, status messages, and information about the regeneration.
 */
public class MapRegenerationScreen implements StateScreen {

    private final Skin skin;
    private Table mainTable;

    // UI Components
    private Label titleLabel;
    private Label stateLabel;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label timeLabel;
    private Table infoTable;

    // Individual stage progress bars
    private ProgressBar cleanupProgressBar;
    private ProgressBar downloadingProgressBar;
    private ProgressBar rebuildingProgressBar;
    private ProgressBar completeProgressBar;

    // Stage labels with status indicators
    private Label cleanupStatusLabel;
    private Label downloadingStatusLabel;
    private Label rebuildingStatusLabel;
    private Label completeStatusLabel;

    // Progress tracking
    private long displayStartTime;
    private StateContext lastContext;

    public MapRegenerationScreen(Skin skin) {
        this.skin = skin;
        this.displayStartTime = System.currentTimeMillis();
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
            titleLabel = new Label("Map Regeneration", skin, "title");
        } catch (Exception e) {
            try {
                titleLabel = new Label("Map Regeneration", skin, "subtitle");
            } catch (Exception e2) {
                titleLabel = new Label("Map Regeneration", skin);
            }
        }
        titleLabel.setColor(Color.WHITE);
        titleLabel.setAlignment(Align.center);
        containerTable.add(titleLabel).colspan(2).padBottom(20).row();

        // Current state - use fallback style if "subtitle" doesn't exist
        try {
            stateLabel = new Label("Initializing...", skin, "subtitle");
        } catch (Exception e) {
            stateLabel = new Label("Initializing...", skin);
        }
        stateLabel.setColor(Color.LIGHT_GRAY);
        stateLabel.setAlignment(Align.center);
        containerTable.add(stateLabel).colspan(2).padBottom(15).row();

        // Progress bar
        progressBar = new ProgressBar(0f, 1f, 0.01f, false, skin);
        progressBar.setValue(0f);
        containerTable.add(progressBar).width(400).height(20).colspan(2).padBottom(10).row();

        // Progress percentage
        progressLabel = new Label("0%", skin);
        progressLabel.setColor(Color.WHITE);
        progressLabel.setAlignment(Align.center);
        containerTable.add(progressLabel).colspan(2).padBottom(15).row();

        // Individual stage progress section
        createStageProgressSection(containerTable);

        // Status message
        statusLabel = new Label("Starting map regeneration...", skin);
        statusLabel.setColor(Color.CYAN);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWrap(true);
        containerTable.add(statusLabel).width(400).colspan(2).padBottom(20).row();

        // Information section (moved below stage progress)
        createInfoSection(containerTable);

        // Time display
        timeLabel = new Label("Time: 0s", skin);
        timeLabel.setColor(Color.GRAY);
        timeLabel.setAlignment(Align.center);
        containerTable.add(timeLabel).colspan(2).padTop(10).row();

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
        addStageProgressRow(stageTable, "Cleanup", cleanupProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           cleanupStatusLabel = new Label("Waiting...", skin));

        // Downloading stage
        addStageProgressRow(stageTable, "Downloading", downloadingProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           downloadingStatusLabel = new Label("Waiting...", skin));

        // Rebuilding stage
        addStageProgressRow(stageTable, "Rebuilding", rebuildingProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           rebuildingStatusLabel = new Label("Waiting...", skin));

        // Complete stage
        addStageProgressRow(stageTable, "Finalizing", completeProgressBar = new ProgressBar(0f, 1f, 0.01f, false, skin),
                           completeStatusLabel = new Label("Waiting...", skin));

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

        infoTable = new Table();
        infoTable.pad(10);

        // Add some helpful information
        addInfoRow("What's happening:", "The game world is being regenerated");
        addInfoRow("Your progress:", "Saved automatically");
        addInfoRow("Connection:", "Maintained with server");

        containerTable.add(infoTable).colspan(2).padBottom(15).row();
    }

    private void addInfoRow(String label, String value) {
        Label labelWidget = new Label(label, skin);
        labelWidget.setColor(Color.LIGHT_GRAY);

        Label valueWidget = new Label(value, skin);
        valueWidget.setColor(Color.WHITE);

        infoTable.add(labelWidget).left().padRight(10);
        infoTable.add(valueWidget).left().row();
    }

    @Override
    public void updateContext(StateContext context) {
        if (context == null) {
            return;
        }

        this.lastContext = context;

        // Update all UI components based on current state
        updateStateDisplay(context);
        updateProgress(context);
        updateStatusMessage(context);
        updateTimeDisplay(context);
        updateInfoPanel(context);
    }

    private void updateStateDisplay(StateContext context) {
        GameState currentState = context.getCurrentState();
        if (stateLabel != null) {
            String stateText = getStateDisplayText(currentState);
            stateLabel.setText(stateText);
        }
    }

    private String getStateDisplayText(GameState state) {
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
                return state.getDisplayName();
        }
    }

    private void updateProgress(StateContext context) {
        if (progressBar != null && progressLabel != null) {
            GameState currentState = context.getCurrentState();
            float stateProgress = context.getProgress();

            // Calculate overall progress across all stages
            float overallProgress = calculateOverallProgress(currentState, stateProgress);
            progressBar.setValue(overallProgress);
            Log.info("MapRegenerationScreen", "PROGRESS BAR UPDATED - State: " + currentState + ", StateProgress: " + stateProgress + ", Overall: " + overallProgress + ", ProgressBarValue: " + progressBar.getValue());

            int percentage = Math.round(overallProgress * 100);
            progressLabel.setText(percentage + "%");

            // Update individual stage progress bars
            updateStageProgressBars(context, currentState, stateProgress);
        }
    }

    private float calculateOverallProgress(GameState state, float stateProgress) {
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

    private void updateStageProgressBars(StateContext context, GameState currentState, float stateProgress) {
        // Update cleanup stage
        updateStageProgress(cleanupProgressBar, cleanupStatusLabel,
                           GameState.MAP_REGENERATION_CLEANUP, currentState, stateProgress, context);

        // Update downloading stage
        updateStageProgress(downloadingProgressBar, downloadingStatusLabel,
                           GameState.MAP_REGENERATION_DOWNLOADING, currentState, stateProgress, context);

        // Update rebuilding stage
        updateStageProgress(rebuildingProgressBar, rebuildingStatusLabel,
                           GameState.MAP_REGENERATION_REBUILDING, currentState, stateProgress, context);

        // Update complete stage
        updateStageProgress(completeProgressBar, completeStatusLabel,
                           GameState.MAP_REGENERATION_COMPLETE, currentState, stateProgress, context);
    }

    private void updateStageProgress(ProgressBar progressBar, Label statusLabel,
                                   GameState targetState, GameState currentState,
                                   float stateProgress, StateContext context) {
        String stageName = getStageNameForState(targetState);

        if (currentState.ordinal() > targetState.ordinal()) {
            // Stage is complete
            progressBar.setValue(1.0f);
            statusLabel.setText("✓ Complete");
            statusLabel.setColor(Color.GREEN);
            Log.debug("MapRegenerationScreen", stageName + " stage: Complete");
        } else if (currentState == targetState) {
            // Stage is currently active
            progressBar.setValue(stateProgress);
            statusLabel.setText(context.getStatusMessage());
            statusLabel.setColor(Color.CYAN);
            Log.debug("MapRegenerationScreen", stageName + " stage: Active - " + stateProgress);
        } else {
            // Stage is waiting
            progressBar.setValue(0.0f);
            statusLabel.setText("Waiting...");
            statusLabel.setColor(Color.GRAY);
            Log.debug("MapRegenerationScreen", stageName + " stage: Waiting");
        }
    }

    private String getStageNameForState(GameState state) {
        switch (state) {
            case MAP_REGENERATION_CLEANUP: return "Cleanup";
            case MAP_REGENERATION_DOWNLOADING: return "Downloading";
            case MAP_REGENERATION_REBUILDING: return "Rebuilding";
            case MAP_REGENERATION_COMPLETE: return "Complete";
            default: return "Unknown";
        }
    }

    private void updateStatusMessage(StateContext context) {
        if (statusLabel != null) {
            String message = context.getStatusMessage();
            if (message != null && !message.trim().isEmpty()) {
                statusLabel.setText(message);
            }
        }
    }

    private void updateTimeDisplay(StateContext context) {
        if (timeLabel != null) {
            long elapsedSeconds = (System.currentTimeMillis() - displayStartTime) / 1000;
            timeLabel.setText("Time: " + elapsedSeconds + "s");
        }
    }

    private void updateInfoPanel(StateContext context) {
        // Update info based on current state and context data
        if (infoTable != null) {
            // Clear existing info
            infoTable.clear();

            // Add general information that's always relevant
            addInfoRow("What's happening:", "The game world is being regenerated");
            addInfoRow("Your progress:", "Saved automatically");
            addInfoRow("Connection:", "Maintained with server");

            // Show regeneration reason if available
            String reason = context.getStateData("regeneration_reason", String.class);
            if (reason != null && !reason.trim().isEmpty()) {
                addInfoRow("Reason:", reason);
            }

            // Show new map seed if available
            Long newSeed = context.getStateData("new_map_seed", Long.class);
            if (newSeed != null) {
                addInfoRow("New Seed:", String.valueOf(newSeed));
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
                        addInfoRow("Chunks:", chunksReceived + "/" + totalChunks);
                    }
                    if (totalBytes != null && bytesReceived != null) {
                        float mbTotal = totalBytes / (1024f * 1024f);
                        float mbReceived = bytesReceived / (1024f * 1024f);
                        addInfoRow("Data:", String.format("%.1f/%.1f MB", mbReceived, mbTotal));
                    }
                    break;

                case MAP_REGENERATION_REBUILDING:
                    // Show timing information for rebuilding
                    Long rebuildStartTime = context.getStateData("rebuilding_start_time", Long.class);
                    if (rebuildStartTime != null) {
                        long elapsed = (System.currentTimeMillis() - rebuildStartTime) / 1000;
                        addInfoRow("Build Time:", elapsed + "s");
                    }
                    break;

                case MAP_REGENERATION_COMPLETE:
                    // Show completion summary
                    addInfoRow("Status:", "Ready to resume gameplay");
                    break;
            }
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
        this.displayStartTime = System.currentTimeMillis();
    }

    /**
     * Get the last updated context
     */
    public StateContext getLastContext() {
        return lastContext;
    }
}
