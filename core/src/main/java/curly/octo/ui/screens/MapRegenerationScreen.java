package curly.octo.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.regeneration.MapRegenerationCoordinator;
import curly.octo.game.regeneration.RegenerationProgressListener;
import curly.octo.game.regeneration.RegenerationPhase;

/**
 * Simplified UI screen for map regeneration process.
 * Uses the new coordinator system for clean progress updates.
 */
public class MapRegenerationScreen implements StateScreen, RegenerationProgressListener {




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
    private MapRegenerationCoordinator coordinator;
    private RegenerationPhase currentPhase;
    private float currentProgress = 0.0f;
    private String currentMessage = "";

    // UI debug elements
    private Label debugUpdateLabel;
    private int updateCount = 0;

    public MapRegenerationScreen(Skin skin, MapRegenerationCoordinator coordinator) {
        this.skin = skin;
        this.coordinator = coordinator;
        this.displayStartTime = System.currentTimeMillis();
        
        // Register as progress listener
        if (coordinator != null) {
            coordinator.addProgressListener(this);
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

        // Remove test button

        // Information section
        createInfoSection(containerTable);

        // Time display
        timeLabel = new Label("Time: 0s", skin);
        timeLabel.setColor(Color.GRAY);
        timeLabel.setAlignment(Align.center);
        containerTable.add(timeLabel).colspan(2).padTop(10).row();

        // Debug update counter
        debugUpdateLabel = new Label("Coordinator Updates: 0", skin);
        debugUpdateLabel.setColor(Color.GREEN);
        debugUpdateLabel.setAlignment(Align.center);
        containerTable.add(debugUpdateLabel).colspan(2).padTop(5).row();

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
    public void updateContext(Object context) {
        // Legacy method - no longer used with new coordinator system
        // The coordinator updates come through RegenerationProgressListener interface
    }

    private void updateStateDisplay() {
        if (stateLabel != null && currentPhase != null) {
            stateLabel.setText(currentPhase.getDisplayName());
        }

        // Animate title color to show UI is updating
        if (titleLabel != null) {
            float time = (System.currentTimeMillis() - displayStartTime) / 1000.0f;
            float pulse = (float)(Math.sin(time * 2.0) * 0.3 + 0.7); // Pulse between 0.4 and 1.0
            titleLabel.setColor(pulse, pulse, 1.0f, 1.0f); // Blue-ish pulse
        }
    }


    private void updateProgress() {
        if (progressBar != null && progressLabel != null) {
            progressBar.setValue(currentProgress);
            int percentage = Math.round(currentProgress * 100);
            progressLabel.setText(percentage + "%");
        }
    }

    private void updateStatusMessage() {
        if (statusLabel != null && currentMessage != null && !currentMessage.trim().isEmpty()) {
            statusLabel.setText(currentMessage);
        }
    }

    private void updateTimeDisplay() {
        if (timeLabel != null) {
            long elapsedSeconds = (System.currentTimeMillis() - displayStartTime) / 1000;
            timeLabel.setText("Time: " + elapsedSeconds + "s");
        }
    }

    private void updateInfoPanel() {
        // Update info based on current phase
        if (infoTable != null) {
            // Clear existing info
            infoTable.clear();

            if (currentPhase != null) {
                switch (currentPhase) {
                    case CLEANUP:
                        addInfoRow("Phase:", "Cleaning up current map");
                        addInfoRow("Status:", "Removing old resources");
                        break;

                    case DOWNLOADING:
                        addInfoRow("Phase:", "Downloading new map data");
                        addInfoRow("Status:", "Receiving chunks from server");
                        break;

                    case REBUILDING:
                        addInfoRow("Phase:", "Rebuilding game world");
                        addInfoRow("Status:", "Creating new environment");
                        break;

                    case COMPLETE:
                        addInfoRow("Phase:", "Complete");
                        addInfoRow("Status:", "Ready to play!");
                        break;

                    default:
                        addInfoRow("Status:", "Processing...");
                        break;
                }
            }

            // Show regeneration info if available
            if (coordinator != null) {
                String reason = coordinator.getRegenerationReason();
                if (reason != null && !reason.trim().isEmpty()) {
                    addInfoRow("Reason:", reason);
                }

                long newSeed = coordinator.getNewMapSeed();
                if (newSeed != 0) {
                    addInfoRow("New Seed:", String.valueOf(newSeed));
                }
            }
        }
    }

    @Override
    public String getTitle() {
        return "Map Regeneration";
    }

    // Removed duplicate dispose method

    /**
     * Reset the display time (called when screen is first shown)
     */
    public void resetDisplayTime() {
        this.displayStartTime = System.currentTimeMillis();
    }

    // RegenerationProgressListener implementation
    @Override
    public void onPhaseChanged(RegenerationPhase phase, String message) {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            this.currentPhase = phase;
            this.currentMessage = message;
            
            updateCount++;
            if (debugUpdateLabel != null) {
                debugUpdateLabel.setText("Coordinator Updates: " + updateCount);
            }
            
            // Update UI components
            updateStateDisplay();
            updateStatusMessage();
            updateInfoPanel();
            updateTimeDisplay();
            
            Log.info("MapRegenerationScreen", "Phase changed to: " + phase.getDisplayName() + " - " + message);
        });
    }
    
    @Override
    public void onProgressChanged(float progress) {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            this.currentProgress = progress;
            updateProgress();
            
            Log.debug("MapRegenerationScreen", "Progress updated to: " + (progress * 100) + "%");
        });
    }
    
    @Override
    public void onCompleted() {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            Log.info("MapRegenerationScreen", "Regeneration completed successfully");
            if (statusLabel != null) {
                statusLabel.setText("Regeneration complete! Ready to continue.");
            }
        });
    }
    
    @Override
    public void onError(String errorMessage) {
        // Ensure UI modifications happen on the main thread
        com.badlogic.gdx.Gdx.app.postRunnable(() -> {
            Log.error("MapRegenerationScreen", "Regeneration error: " + errorMessage);
            if (statusLabel != null) {
                statusLabel.setText("Error: " + errorMessage);
                statusLabel.setColor(Color.RED);
            }
        });
    }
    
    @Override
    public void dispose() {
        // Unregister from coordinator
        if (coordinator != null) {
            coordinator.removeProgressListener(this);
        }
    }
}
