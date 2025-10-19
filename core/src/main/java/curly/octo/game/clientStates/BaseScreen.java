package curly.octo.game.clientStates;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;

public abstract class BaseScreen implements Screen{

    protected Stage stage;

    public BaseScreen() {
        createStage();
    }

    protected abstract void createStage();

    public Stage getStage() {
        return stage;
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        if (stage != null) {
            stage.draw();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            // Dispose the old stage
            stage.dispose();
            // Recreate the stage
            createStage();
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void dispose() {

    }

}
