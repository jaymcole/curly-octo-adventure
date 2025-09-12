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

        // Status message
        statusLabel = new Label("Starting map regeneration...", skin);
        statusLabel.setColor(Color.CYAN);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWrap(true);
        containerTable.add(statusLabel).width(400).colspan(2).padBottom(20).row();

        // Information section
        createInfoSection(containerTable);

        // Time display
        timeLabel = new Label("Time: 0s", skin);
        timeLabel.setColor(Color.GRAY);
        timeLabel.setAlignment(Align.center);
        containerTable.add(timeLabel).colspan(2).padTop(10).row();

        // Add container to main table
        mainTable.add(containerTable).expand().center();
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
            float progress = context.getProgress();
            progressBar.setValue(progress);
            Log.info("updateProgress", "Setting progress bar to: " + progress);
            int percentage = Math.round(progress * 100);
            progressLabel.setText(percentage + "%");
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

            // Add current state-specific information
            GameState currentState = context.getCurrentState();

            switch (currentState) {
                case MAP_REGENERATION_CLEANUP:
                    addInfoRow("Phase:", "Cleaning up current map");
                    addInfoRow("Status:", "Removing old resources");
                    break;

                case MAP_REGENERATION_DOWNLOADING:
                    addInfoRow("Phase:", "Downloading new map data");

                    // Show download progress if available
                    Integer totalChunks = context.getStateData("total_chunks", Integer.class);
                    Integer chunksReceived = context.getStateData("chunks_received", Integer.class);

                    if (totalChunks != null && chunksReceived != null) {
                        addInfoRow("Progress:", chunksReceived + "/" + totalChunks + " chunks");
                    } else {
                        addInfoRow("Status:", "Waiting for data...");
                    }
                    break;

                case MAP_REGENERATION_REBUILDING:
                    addInfoRow("Phase:", "Rebuilding game world");
                    addInfoRow("Status:", "Creating new environment");
                    break;

                case MAP_REGENERATION_COMPLETE:
                    addInfoRow("Phase:", "Finalizing");
                    addInfoRow("Status:", "Almost ready!");
                    break;

                default:
                    addInfoRow("Status:", "Processing...");
                    break;
            }

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
