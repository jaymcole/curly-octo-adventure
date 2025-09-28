package curly.octo.game.stateV2;

import com.badlogic.gdx.Screen;
import com.esotericsoftware.minlog.Log;
import curly.octo.Main;
import curly.octo.game.stateV2.MainMenuState.MainMenuScreen;
import curly.octo.game.stateV2.MainMenuState.MainMenuState;
import curly.octo.game.stateV2.MapTransferState.*;

import java.util.HashMap;

public class StateManager{
    private static BaseGameState currentState;
    private static HashMap<Class, BaseGameState> cachedStates;
    private static HashMap<Class, BaseScreen> cachedScreens;
    private static Main mainGame;

    public static void initialize(Main main) {
        mainGame = main;
        cachedStates = new HashMap<>();
        cachedScreens = new HashMap<>();

        cachedScreens.put(MainMenuScreen.class, new MainMenuScreen(main));
        cachedStates.put(MainMenuState.class, new MainMenuState());
        setCurrentState(MainMenuState.class);

        cacheMapTransferScreens();
        cacheMapTransferStates();
    }

    private static void cacheMapTransferScreens() {
        cachedScreens.put(MapTransferScreen.class, new MapTransferScreen());
    }

    private static void cacheMapTransferStates() {
        cachedStates.put(MapTransferInitiatedState.class, new MapTransferInitiatedState());
        cachedStates.put(MapTransferDisposeState.class, new MapTransferDisposeState());
        cachedStates.put(MapTransferTransferState.class, new MapTransferTransferState());
        cachedStates.put(MapTransferBuildAssetsState.class, new MapTransferBuildAssetsState());
        cachedStates.put(MapTransferCompleteState.class, new MapTransferCompleteState());
    }

    public static void update(float delta) {
        if (currentState != null) {
            currentState.updateState(delta);
        }
    }

    public static void setCurrentState(Class nextState) {
        String currentStateString = "<no previous state>";
        if (currentState != null) {
            currentStateString = currentState.getClass().getSimpleName();
        }

        String nextStateString = "<??null next state??>";
        if (nextState != null) {
            nextStateString = nextState.getSimpleName();
        }


        if (!cachedStates.containsKey(nextState)) {
            Log.error("StateManager", "Cannot switch from " + currentStateString + " to " + nextStateString + " because it does not exist in cached states");
        }

        Log.info("StateManager", "Old game state: " + currentStateString + ", new game state: " + nextStateString);

        if (currentState != null) {
            currentState.end();
        }

        currentState = cachedStates.get(nextState);
        currentState.start();
        mainGame.setScreen(currentState.getStateScreen(), currentState.getGamePlaying());
    }

    public static BaseScreen getCachedScreen(Class screenClass) {
        return cachedScreens.get(screenClass);
    }

    public static void resize(int width, int height) {
        for(BaseScreen screen : cachedScreens.values()) {
            screen.resize(width, height);
        }
    }

    public static void dispose() {
        for(BaseScreen screen : cachedScreens.values()) {
            screen.dispose();
        }

        for(BaseGameState state : cachedStates.values()) {
            state.dispose();
        }
    }
}
