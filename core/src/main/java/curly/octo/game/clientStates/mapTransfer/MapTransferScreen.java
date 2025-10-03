package curly.octo.game.clientStates.mapTransfer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import curly.octo.game.clientStates.BaseScreen;
import curly.octo.ui.UIAssetCache;


public class MapTransferScreen extends BaseScreen {

    private static Label transferPhaseMessage; // Describes what the current stage is during
    private static Label transferStateName;

    @Override
    protected void createStage() {
        stage = new Stage(new ScreenViewport());
        Table mainTable = new Table();
        mainTable.setFillParent(true);

        transferStateName = new Label("currentState", UIAssetCache.getDefaultSkin());
        transferStateName.setAlignment(Align.center);
        transferStateName.setColor(Color.WHITE);
        mainTable.add(transferStateName).row();

        transferPhaseMessage = new Label("<phase message>", UIAssetCache.getDefaultSkin());
        transferPhaseMessage.setAlignment(Align.center);
        transferStateName.setColor(Color.WHITE);
        mainTable.add(transferPhaseMessage).row();

        stage.addActor(mainTable);

    }

    public static void setPhaseMessage(String message) {
        transferStateName.setText("Current State: " + message);
    }

    public static void setStateName(String stateName) {

    }

}
