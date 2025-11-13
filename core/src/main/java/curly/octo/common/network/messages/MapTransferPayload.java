package curly.octo.common.network.messages;

import curly.octo.common.GameObject;
import curly.octo.common.map.GameMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for transferring both map data and game objects together.
 * Used in the map transfer workflow to send all game state in a single atomic transfer.
 */
public class MapTransferPayload {
    /**
     * The game map containing terrain, tiles, and map hints.
     */
    public GameMap map;

    /**
     * All active game objects (players, NPCs, world objects, etc.)
     * to be transferred with the map.
     */
    public List<GameObject> gameObjects;

    /**
     * Default constructor for Kryo serialization.
     */
    public MapTransferPayload() {
        this.gameObjects = new ArrayList<>();
    }

    /**
     * Creates a transfer payload with map and game objects.
     * @param map The game map to transfer
     * @param gameObjects The game objects to transfer
     */
    public MapTransferPayload(GameMap map, List<GameObject> gameObjects) {
        this.map = map;
        this.gameObjects = gameObjects != null ? gameObjects : new ArrayList<>();
    }
}
