package lights;

import curly.octo.Main;

public class LightPresets {

    public static final float[] LIGHT_FLICKER_ON_OFF = new float[] {1f,0f};
    public static final float[] LIGHT_FLICKER_PULSE = new float[] {1f,.9f,.8f,.7f,.6f,.5f, .4f, .3f, .2f, .1f,.0f,.0f,.0f,.1f, .2f, .3f, .4f, .5f, .6f, .7f, .8f, .9f};
    public static final float[] LIGHT_FLICKER_1 = new float[] {1f,.9f,.7f,.8f,.7f,.9f};
    public static final float[] LIGHT_FLICKER_2 = new float[] {1f,.9f,.4f,.7f,1f,.5f};

    public static final float[][] ALL_FLICKERS = {
        LIGHT_FLICKER_PULSE,
        LIGHT_FLICKER_1,
//        LIGHT_FLICKER_2
    };

    public static float[] getRandomFlicker() {
        return ALL_FLICKERS[Main.random.nextInt(ALL_FLICKERS.length)];
    }
}
