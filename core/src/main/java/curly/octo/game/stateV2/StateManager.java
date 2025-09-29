package curly.octo.game.stateV2;

import com.badlogic.gdx.Screen;
import com.esotericsoftware.minlog.Log;
import curly.octo.Main;
import curly.octo.game.stateV2.MainMenuState.MainMenuScreen;
import curly.octo.game.stateV2.MainMenuState.MainMenuState;
import curly.octo.game.stateV2.MapTransferState.*;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.ClientStateChangeMessage;

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

        MainMenuScreen mainMenuScreen = new MainMenuScreen(main);
        cachedScreens.put(MainMenuScreen.class, mainMenuScreen);
        cachedStates.put(MainMenuState.class, new MainMenuState(mainMenuScreen));
        setCurrentState(MainMenuState.class);

        cacheMapTransferStates();
    }

    private static void cacheMapTransferStates() {
        MapTransferScreen mapTransferScreen = new MapTransferScreen();
        cachedScreens.put(MapTransferScreen.class, mapTransferScreen);
        cachedStates.put(MapTransferInitiatedState.class, new MapTransferInitiatedState(mapTransferScreen));
        cachedStates.put(MapTransferDisposeState.class, new MapTransferDisposeState(mapTransferScreen));
        cachedStates.put(MapTransferTransferState.class, new MapTransferTransferState(mapTransferScreen));
        cachedStates.put(MapTransferBuildAssetsState.class, new MapTransferBuildAssetsState(mapTransferScreen));
        cachedStates.put(MapTransferCompleteState.class, new MapTransferCompleteState(mapTransferScreen));
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

        BaseGameState oldState = currentState;

        currentState = cachedStates.get(nextState);
        currentState.start();
        mainGame.setScreen(currentState.getStateScreen(), currentState.getGamePlaying());

        if (oldState != null) {
            NetworkManager.sendToServer(new ClientStateChangeMessage(oldState.getClass(), currentState.getClass()));
        }
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
