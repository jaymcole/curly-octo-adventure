package curly.octo.client.clientStates.mapTransferStates.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import curly.octo.client.ui.UIAssetCache;
import curly.octo.server.playerManagement.ClientUniqueId;

public class ClientProgressElement extends Table {

    private final Label clientIdLabel;
    private final ProgressBar progressBar;
    private final ClientUniqueId clientUniqueId;

    public ClientProgressElement(ClientUniqueId clientUniqueId) {
        this(clientUniqueId, UIAssetCache.getDefaultSkin());
    }

    public ClientProgressElement(ClientUniqueId clientUniqueId, Skin skin) {
        this.clientUniqueId = clientUniqueId;

        // Create client ID label
        clientIdLabel = new Label(clientUniqueId.toString(), skin);
        clientIdLabel.setAlignment(Align.center);
        clientIdLabel.setColor(Color.WHITE);

        // Create progress bar (0-100%)
        progressBar = new ProgressBar(0, 100, 1f, false, skin);
        progressBar.setValue(0);

        // Layout: client ID on top, progress bar below
        this.add(clientIdLabel).expandX().fillX().pad(5).row();
        this.add(progressBar).width(200).height(20).pad(5);
    }

    /**
     * Update the progress (0-100)
     */
    public void setProgress(float progress) {
        progressBar.setValue(Math.max(0, Math.min(100, progress)));
    }

    /**
     * Update the progress from chunk counts
     */
    public void setProgress(int chunksReceived, int totalChunks) {
        if (totalChunks > 0) {
            float progress = (chunksReceived * 100.0f) / totalChunks;
            setProgress(progress);
        }
    }

    /**
     * Get the current progress value (0-100)
     */
    public float getProgress() {
        return progressBar.getValue();
    }

    /**
     * Get the client unique ID this element represents
     */
    public ClientUniqueId getClientUniqueId() {
        return clientUniqueId;
    }

    /**
     * Set the color of the client ID label
     */
    public void setLabelColor(Color color) {
        clientIdLabel.setColor(color);
    }
}
