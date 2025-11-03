package curly.octo.client.clientStates.mapTransferStates.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import curly.octo.Main;
import curly.octo.client.clientStates.BaseScreen;
import curly.octo.client.clientStates.StateManager;
import curly.octo.client.ui.UIAssetCache;

import java.util.HashMap;
import java.util.Map;

import static curly.octo.common.Constants.DEFAULT_SCREEN_HEIGHT;
import static curly.octo.common.Constants.DEFAULT_SCREEN_WIDTH;

public class MapTransferScreen extends BaseScreen {

    private static Label transferPhaseMessage; // Describes what the current stage is during
    private static Label transferStateName;
    private static Label clientUniqueIdLabel;
    private static ProgressBar clientMapTransferProgress;
    private static Table clientProgressTable; // Container for client progress elements
    private static Map<String, ClientProgressElement> clientProgressElements = new HashMap<>();

    @Override
    protected void createStage() {
        stage = new Stage(new ExtendViewport(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT));
        Table mainTable = new Table();
        mainTable.setFillParent(true);

        transferStateName = new Label("currentState", UIAssetCache.getDefaultSkin());
        transferStateName.setAlignment(Align.center);
        transferStateName.setColor(Color.WHITE);
        mainTable.add(transferStateName).colspan(2).row();

        transferPhaseMessage = new Label("<phase message>", UIAssetCache.getDefaultSkin());
        transferPhaseMessage.setAlignment(Align.center);
        transferPhaseMessage.setColor(Color.WHITE);
        mainTable.add(transferPhaseMessage).colspan(2).row();

        clientUniqueIdLabel = new Label("uniqueId", UIAssetCache.getDefaultSkin());
        clientUniqueIdLabel.setAlignment(Align.center);
        clientUniqueIdLabel.setColor(Color.WHITE);
        mainTable.add(clientUniqueIdLabel).colspan(2).row();

        clientMapTransferProgress = new ProgressBar(0, 1, 0.01f, false, UIAssetCache.getDefaultSkin());
        mainTable.add(clientMapTransferProgress).colspan(2).row();

        // Add a section for other clients' progress
        Label otherClientsLabel = new Label("Other Clients:", UIAssetCache.getDefaultSkin());
        otherClientsLabel.setAlignment(Align.center);
        otherClientsLabel.setColor(Color.LIGHT_GRAY);
        mainTable.add(otherClientsLabel).colspan(2).pad(20, 0, 10, 0).row();

        // Create the two-column table for client progress elements
        clientProgressTable = new Table();
        mainTable.add(clientProgressTable).colspan(2).expandX().fillX().pad(10).row();

        stage.addActor(mainTable);

    }

    public static void updateMapTransferProgress(int chunksReceived, int totalChunks) {
        clientMapTransferProgress.setValue((chunksReceived + 0.0f) / totalChunks);
    }

    public static void setPhaseMessage(String message) {
        transferStateName.setText("Current State: " + message);
    }

    public static void setStateName(String stateName) {

    }

    /**
     * Update all client progress from the server.
     * Rebuilds the UI if the client list has changed, otherwise just updates progress values.
     *
     * @param clientProgress Map of clientUniqueId to chunk progress
     * @param currentClientId The unique ID of the current client (to exclude from the list)
     * @param totalChunks Total number of chunks for percentage calculation
     */
    public static void updateAllClientProgress(Map<String, Integer> clientProgress, String currentClientId, int totalChunks) {
        if (clientProgress == null || clientProgressTable == null) {
            return;
        }

        // Filter out the current client
        Map<String, Integer> otherClients = new HashMap<>();
        for (Map.Entry<String, Integer> entry : clientProgress.entrySet()) {
            if (!entry.getKey().equals(currentClientId)) {
                otherClients.put(entry.getKey(), entry.getValue());
            }
        }

        // Check if we need to rebuild (client list changed)
        boolean needsRebuild = otherClients.size() != clientProgressElements.size() ||
                               !clientProgressElements.keySet().equals(otherClients.keySet());

        if (needsRebuild) {
            rebuildClientProgressUI(otherClients, totalChunks);
        } else {
            updateClientProgressValues(otherClients, totalChunks);
        }
    }

    /**
     * Rebuild the entire client progress UI from scratch.
     * Creates a two-column layout of ClientProgressElements.
     */
    private static void rebuildClientProgressUI(Map<String, Integer> clientProgress, int totalChunks) {
        // Clear existing elements
        clientProgressTable.clear();
        clientProgressElements.clear();

        if (clientProgress.isEmpty()) {
            return;
        }

        // Create new elements in two-column layout
        int index = 0;
        for (Map.Entry<String, Integer> entry : clientProgress.entrySet()) {
            String clientId = entry.getKey();
            int chunks = entry.getValue();

            ClientProgressElement element = new ClientProgressElement(clientId);
            element.setProgress(chunks, totalChunks);
            clientProgressElements.put(clientId, element);

            // Add to table in two columns
            clientProgressTable.add(element).pad(10).expandX().fillX();

            // Start new row after every 2 elements
            if (index % 2 == 1) {
                clientProgressTable.row();
            }

            index++;
        }

        // If we ended with an odd number, add an empty cell to balance the layout
        if (index % 2 == 1) {
            clientProgressTable.add().expandX();
        }
    }

    /**
     * Update progress values for existing client progress elements.
     * Does not rebuild the UI structure.
     */
    private static void updateClientProgressValues(Map<String, Integer> clientProgress, int totalChunks) {
        clientUniqueIdLabel.setText(Main.clientUniqueId);
        for (Map.Entry<String, Integer> entry : clientProgress.entrySet()) {
            String clientId = entry.getKey();
            int chunks = entry.getValue();

            ClientProgressElement element = clientProgressElements.get(clientId);
            if (element != null) {
                element.setProgress(chunks, totalChunks);
            }
        }
    }

}
