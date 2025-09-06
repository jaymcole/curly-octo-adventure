package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.generators.templated.TemplateManager;
import curly.octo.map.generators.templated.TemplateRoom;

import java.util.*;
import java.util.stream.Collectors;

import static curly.octo.map.generators.templated.TemplateManager.*;

public class TemplateGenerator extends MapGenerator {

    private static final int MAX_ROOMS = 100;
    private static final String CONNECTION_KEY_DELIMITER = "beans";
    private final TemplateManager manager;

    private final HashMap<String, TemplateRoom> rooms;
    private final HashMap<String, TemplateRoom> connectors;

    private final ArrayList<String> expansionKeys;
    private int roomsPlaced;



    public TemplateGenerator(Random random, GameMap map) {
        super(random, map);
        roomsPlaced = 0;
        manager = new TemplateManager(new String[]{"templates"});
        expansionKeys = new ArrayList<>();
        rooms = new HashMap<>();
        connectors = new HashMap<>();
    }

    private void placeRoom(int x, int y, int z, TemplateRoom room) {
        Vector3 start = new Vector3(x,y,z);
        rooms.put(constructRoomKey(start), room);
        roomsPlaced++;
        Log.info("placeRoom", "Adding expansion keys around " + room.template_name  +" [" + room.exits.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", ")) + "]");
        for(Direction dir : room.exits) {
            String key = constructRoomKey(Direction.advanceVector(dir, start.cpy()));
            if (!rooms.containsKey(key)) {
                expansionKeys.add(key);
            }
        }
    }

    private HashSet<Direction> gatherValidRoomEntranceRequirements(String roomKey) {
        Vector3 roomCoordinates = extractCoordinatesFromRoomKey(roomKey);

        HashSet<Direction> requiredEntrances = new HashSet<>();
        String key = constructRoomKey(Direction.advanceVector(Direction.NORTH, roomCoordinates.cpy()));
        if (rooms.containsKey(key) && rooms.get(key).exits.contains(Direction.SOUTH)) {
            requiredEntrances.add(Direction.NORTH);
        }
        key = constructRoomKey(Direction.advanceVector(Direction.EAST, roomCoordinates.cpy()));
        if (rooms.containsKey(key) && rooms.get(key).exits.contains(Direction.WEST)) {
            requiredEntrances.add(Direction.EAST);
        }
        key = constructRoomKey(Direction.advanceVector(Direction.SOUTH, roomCoordinates.cpy()));
        if (rooms.containsKey(key) && rooms.get(key).exits.contains(Direction.NORTH)) {
            requiredEntrances.add(Direction.SOUTH);
        }
        key = constructRoomKey(Direction.advanceVector(Direction.WEST, roomCoordinates.cpy()));
        if (rooms.containsKey(key) && rooms.get(key).exits.contains(Direction.EAST)) {
            requiredEntrances.add(Direction.WEST);
        }
        return requiredEntrances;
    }

    private String constructRoomKey(Vector3 coordinates) {
        return constructRoomKey((int)coordinates.x,(int)coordinates.y,(int)coordinates.z);
    }

    private String constructRoomKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private String constructConnectionKey(String roomKey1, String roomKey2) {
        return roomKey1 + CONNECTION_KEY_DELIMITER + roomKey2;
    }

    private Vector3 extractCoordinatesFromRoomKey(String roomKey) {
        String[] keyParts = roomKey.split(",");
        if (keyParts.length != 3) {
            throw new IllegalArgumentException("Invalid room key format: " + roomKey + " (expected x,y,z)");
        }
        for (String part : keyParts) {
            if (part.trim().isEmpty()) {
                throw new IllegalArgumentException("Empty coordinate in room key: " + roomKey);
            }
        }
        return new Vector3(Integer.parseInt(keyParts[0]),Integer.parseInt(keyParts[1]),Integer.parseInt(keyParts[2]));
    }

    @Override
    public void generate() {
        placeRoom(0,0,0, manager.getTemplateByFileName(SPAWN_ROOM));
        addSpawn(new Vector3(5,1,5));

//
//        String roomName = "corridor_nesw";
//        Vector3 north = Direction.advanceVector(Direction.NORTH, new Vector3(0,0,0));
//        placeRoom((int)north.x, (int)north.y, (int)north.z, manager.getTemplateByFileName(roomName));

//        Direction.advanceVector(Direction.NORTH, north);
//        placeRoom((int)north.x, (int)north.y, (int)north.z, manager.getTemplateByFileName(roomName));
//
//        Direction.advanceVector(Direction.NORTH, north);
//        placeRoom((int)north.x, (int)north.y, (int)north.z, manager.getTemplateByFileName(roomName));

        while(!expansionKeys.isEmpty() && roomsPlaced < MAX_ROOMS) {
            String key = expansionKeys.remove(0);
            HashSet<Direction> requirements = gatherValidRoomEntranceRequirements(key);
            List<TemplateRoom> possibleRooms = manager.getValidRoomOptions(requirements);
            if (!possibleRooms.isEmpty()) {
                TemplateRoom nextRoom = possibleRooms.get(random.nextInt(possibleRooms.size()));
                Vector3 extractedCoords = extractCoordinatesFromRoomKey(key);
                placeRoom((int)extractedCoords.x,(int)extractedCoords.y,(int)extractedCoords.z, nextRoom);
            }
        }

        Log.info("generate", "Placed " + roomsPlaced + " rooms");
        for(Map.Entry<String, TemplateRoom> room : rooms.entrySet()) {
            Vector3 roomCoordinates = extractCoordinatesFromRoomKey(room.getKey());
            for(Direction exitDirection : room.getValue().exits) {
                addRoomConnection(exitDirection, roomCoordinates);
            }
        }

        copyRoomTemplates();
        copyConnectorTemplates();
        closeMap();
    }

    private void addRoomConnection(Direction exitDirection, Vector3 roomCoordinates) {
        String roomKey = constructRoomKey(roomCoordinates);
        String neighborKey = constructRoomKey(Direction.advanceVector(exitDirection, roomCoordinates.cpy()));
        if (!connectors.containsKey(constructConnectionKey(roomKey, neighborKey)) && !connectors.containsKey(constructConnectionKey(neighborKey, roomKey))) {
            Log.info("Placing connection on : " + constructConnectionKey(roomKey, neighborKey) + " going " + exitDirection);
            HashSet<Direction> requiredDirections = new HashSet<>();
            requiredDirections.add(exitDirection);
            ArrayList<TemplateRoom> connectorOptions = manager.getValidConnectorOptions(requiredDirections);
            connectors.put(constructConnectionKey(roomKey, neighborKey), connectorOptions.get(random.nextInt(connectorOptions.size())));
        } else {
            Log.info("Skipping connection as we already have one here: " + constructConnectionKey(roomKey, neighborKey) + ", ", constructConnectionKey(neighborKey, roomKey));
        }
    }

    private void copyRoomTemplates() {
        for (Map.Entry<String, TemplateRoom> roomEntry : rooms.entrySet()) {
            Vector3 roomCoordinates = extractCoordinatesFromRoomKey(roomEntry.getKey());
            TemplateRoom template = roomEntry.getValue();

            // Rooms are 7x7x7, so translate room coordinates to world coordinates
            int baseX = (int)roomCoordinates.x * 8;
            int baseY = (int)roomCoordinates.y * 8;
            int baseZ = (int)roomCoordinates.z * 8;

            // Copy template data to map using touchTile
            for (int slice = 0; slice < template.walls.length; slice++) {
                for (int x = 0; x < template.walls[slice].length; x++) {
                    for (int z = 0; z < template.walls[slice][x].length; z++) {
                        if (template.walls[slice][z][x] == 1) {
                            map.touchTile(baseZ + z + 1, baseY + slice, baseX + x + 1);
                        }
                    }
                }
            }
            addLight(new Vector3(baseZ + 5,baseY + 6,  baseX + 5));
        }
    }

    private void copyConnectorTemplates() {
        for (Map.Entry<String, TemplateRoom> connectorEntry : connectors.entrySet()) {
            // Parse connection key to get room coordinates
            String[] connectionParts = connectorEntry.getKey().split(CONNECTION_KEY_DELIMITER);
            if (connectionParts.length != 2) {
                System.err.println("Invalid connection key format: " + connectorEntry.getKey());
                continue;
            }
            Vector3 room1Coords = extractCoordinatesFromRoomKey(connectionParts[0]);

            TemplateRoom template = connectorEntry.getValue();

            // Connectors are 9x7x9, positioned between rooms with 1 tile padding
            // Calculate connector position based on the midpoint between rooms
            int baseX = (int)room1Coords.x * 8;
            int baseY = (int)room1Coords.y * 8;
            int baseZ = (int)room1Coords.z * 8;

            // Copy template data to map using touchTile
            for (int slice = 0; slice < template.walls.length; slice++) {
                for (int z = 0; z < template.walls[slice].length; z++) {
                    for (int x = 0; x < template.walls[slice][z].length; x++) {
                        if (template.walls[slice][z][x] == 1) {
                            map.touchTile(baseZ + z, baseY + slice, baseX + x);
                        }
                    }
                }
            }
        }
    }
}
