package curly.octo.game.clientStates;

import com.esotericsoftware.minlog.Log;
import curly.octo.Main;
import curly.octo.game.clientStates.MainMenuState.MainMenuScreen;
import curly.octo.game.clientStates.MainMenuState.MainMenuState;
import curly.octo.game.clientStates.mapTransfer.*;
import curly.octo.game.clientStates.mapTransfer.ui.MapTransferScreen;
import curly.octo.game.clientStates.playing.ClientPlayingState;
import curly.octo.network.GameClient;
import curly.octo.network.NetworkManager;
import curly.octo.network.messages.ClientStateChangeMessage;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.HashMap;

public class StateManager{
    private static BaseGameStateClient currentState;
    private static HashMap<Class, BaseGameStateClient> cachedStates;
    private static HashMap<Class, BaseScreen> cachedScreens;
    private static Main mainGame;
    private static GameClient gameClient;
    private static curly.octo.game.ClientGameWorld clientGameWorld;
    private static curly.octo.game.ClientGameMode clientGameMode;

    public static void initialize(Main main) {
        mainGame = main;
        cachedStates = new HashMap<>();
        cachedScreens = new HashMap<>();

        MainMenuScreen mainMenuScreen = new MainMenuScreen(main);
        cachedScreens.put(MainMenuScreen.class, mainMenuScreen);
        cachedStates.put(MainMenuState.class, new MainMenuState(mainMenuScreen));
        setCurrentState(MainMenuState.class);

        cachedStates.put(ClientPlayingState.class, new ClientPlayingState(null));
        cacheMapTransferStates();
    }

    private static void cacheMapTransferStates() {
        MapTransferScreen mapTransferScreen = new MapTransferScreen();
        cachedScreens.put(MapTransferScreen.class, mapTransferScreen);
        cachedStates.put(MapTransferInitiatedState.class, new MapTransferInitiatedState(mapTransferScreen));
        cachedStates.put(MapTransferDisposeState.class, new MapTransferDisposeState(mapTransferScreen));
        cachedStates.put(MapTransferConnectBulkState.class, new MapTransferConnectBulkState(mapTransferScreen));
        cachedStates.put(MapTransferTransferState.class, new MapTransferTransferState(mapTransferScreen));
        cachedStates.put(MapTransferReassemblyState.class, new MapTransferReassemblyState(mapTransferScreen));
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
            throw new NotImplementedException();
        }

        Log.info("StateManager", "Old game state: " + currentStateString + ", new game state: " + nextStateString);

        if (currentState == cachedStates.get(nextState)) {
            return;
        }

        if (currentState != null) {
            currentState.end();
        }

        BaseGameStateClient oldState = currentState;

        currentState = cachedStates.get(nextState);
        currentState.start();
        mainGame.setScreen(currentState.getStateScreen(), currentState.getGamePlaying());

        if (oldState != null) {
            NetworkManager.sendToServer(new ClientStateChangeMessage(currentState.getClass(), oldState.getClass()));
        }
    }

    public static BaseGameStateClient getCachedState(Class clientStateClass) {
        return cachedStates.get(clientStateClass);
    }

    public static BaseScreen getCachedScreen(Class screenClass) {
        return cachedScreens.get(screenClass);
    }

    public static void resize(int width, int height) {
        for(BaseScreen screen : cachedScreens.values()) {
            screen.resize(width, height);
        }
    }

    /**
     * Sets the GameClient reference for map transfer states to use.
     * Should be called after GameClient is initialized.
     */
    public static void setGameClient(GameClient client) {
        gameClient = client;
        Log.info("StateManager", "GameClient reference set for map transfer states");
    }

    /**
     * Gets the current client state.
     * @return the current BaseGameStateClient instance, or null if not set
     */
    public static BaseGameStateClient getCurrentState() {
        return currentState;
    }

    /**
     * Gets the GameClient reference.
     * @return the GameClient instance, or null if not set
     */
    public static GameClient getGameClient() {
        return gameClient;
    }

    /**
     * Sets the ClientGameWorld reference for map transfer states to use.
     * Should be called after ClientGameWorld is initialized.
     */
    public static void setClientGameWorld(curly.octo.game.ClientGameWorld world) {
        clientGameWorld = world;
        Log.info("StateManager", "ClientGameWorld reference set for map transfer states");
    }

    /**
     * Gets the ClientGameWorld reference.
     * @return the ClientGameWorld instance, or null if not set
     */
    public static curly.octo.game.ClientGameWorld getClientGameWorld() {
        return clientGameWorld;
    }

    /**
     * Sets the ClientGameMode reference for playing state to use.
     * Should be called after ClientGameMode is initialized.
     */
    public static void setClientGameMode(curly.octo.game.ClientGameMode mode) {
        clientGameMode = mode;
        Log.info("StateManager", "ClientGameMode reference set for playing state");
    }

    /**
     * Gets the ClientGameMode reference.
     * @return the ClientGameMode instance, or null if not set
     */
    public static curly.octo.game.ClientGameMode getClientGameMode() {
        return clientGameMode;
    }

    public static void dispose() {
        for(BaseScreen screen : cachedScreens.values()) {
            screen.dispose();
        }

        for(BaseGameStateClient state : cachedStates.values()) {
            state.dispose();
        }

        gameClient = null;
        clientGameWorld = null;
        clientGameMode = null;
    }
}
