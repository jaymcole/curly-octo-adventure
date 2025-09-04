package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.generators.templated.TemplateManager;
import curly.octo.map.generators.templated.TemplateRoom;

import java.util.*;

import static curly.octo.map.generators.templated.TemplateManager.OPEN_ROOM;

public class TemplateGenerator extends MapGenerator {

    private static final int MAX_ROOMS = 10;

    private TemplateManager manager;

    private HashMap<String, TemplateRoom> rooms;
    private HashMap<String, TemplateRoom> connectors;

    private ArrayList<String> expansionKeys;
    private int roomsPlaced;

    public TemplateGenerator(Random random, GameMap map) {
        super(random, map);
        roomsPlaced = 0;
        manager = new TemplateManager(new String[]{"templates"});
        expansionKeys = new ArrayList<>();
        rooms = new HashMap<>();
        connectors = new HashMap<>();
        System.out.println("Done");
//        closeMap();
    }

    private void placeRoom(int x, int y, int z, TemplateRoom room) {
        Vector3 start = new Vector3(x,y,z);
        rooms.put(constructRoomKey(start), room);
        roomsPlaced++;
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
        return roomKey1 + "-" + roomKey2;
    }

    private Vector3 extractCoordinatesFromRoomKey(String roomKey) {
        String[] keyParts = roomKey.split(",");
        return new Vector3(Integer.parseInt(keyParts[0]),Integer.parseInt(keyParts[1]),Integer.parseInt(keyParts[2]));
    }

    @Override
    public void generate() {
        placeRoom(0,0,0, manager.getTemplateByFileName(OPEN_ROOM));
        addSpawn(new Vector3(0,1,0));

        while(!expansionKeys.isEmpty() && roomsPlaced < MAX_ROOMS) {
            String key = expansionKeys.remove(0);
            HashSet<Direction> requirements = gatherValidRoomEntranceRequirements(key);
            List<TemplateRoom> possibleRooms = manager.getValidRoomOptions(requirements);
            TemplateRoom nextRoom = possibleRooms.get(random.nextInt(possibleRooms.size()));
            Vector3 extractedCoords = extractCoordinatesFromRoomKey(key);
            placeRoom((int)extractedCoords.x,(int)extractedCoords.y,(int)extractedCoords.z, nextRoom);
        }

        for(Map.Entry<String, TemplateRoom> room : rooms.entrySet()) {
            Vector3 roomCoordinates = extractCoordinatesFromRoomKey(room.getKey());
            if (room.getValue().exits.contains(Direction.NORTH)) {
                String neighborKey = constructRoomKey(Direction.advanceVector(Direction.NORTH, roomCoordinates.cpy()));
                HashSet<Direction> requiredDirections = new HashSet<>();
                requiredDirections.add(Direction.NORTH);
                ArrayList<TemplateRoom> connectorOptions = manager.getValidConnectorOptions(requiredDirections);
                connectors.put(constructConnectionKey(room.getKey(), neighborKey), connectorOptions.get(random.nextInt(connectorOptions.size())));
            }
            if (room.getValue().exits.contains(Direction.EAST)) {
                String neighborKey = constructRoomKey(Direction.advanceVector(Direction.EAST, roomCoordinates.cpy()));
                HashSet<Direction> requiredDirections = new HashSet<>();
                requiredDirections.add(Direction.EAST);
                ArrayList<TemplateRoom> connectorOptions = manager.getValidConnectorOptions(requiredDirections);
                connectors.put(constructConnectionKey(room.getKey(), neighborKey), connectorOptions.get(random.nextInt(connectorOptions.size())));
            }
        }

        System.out.print("Som");
    }
}
