package curly.octo.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class UIAssetCache {

    private static Skin cachedSkin;
    public static Skin getDefaultSkin() {
        if (cachedSkin == null) {
            cachedSkin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        }
        return cachedSkin;
    }


}
