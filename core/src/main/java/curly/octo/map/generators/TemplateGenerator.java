package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.generators.templated.TemplateManager;
import curly.octo.map.generators.templated.TemplateRoom;

import java.util.*;
import java.util.stream.Collectors;

import static curly.octo.map.generators.templated.TemplateManager.*;

public class TemplateGenerator extends MapGenerator {

    private static final int MAX_ROOMS = 1000;
    private static final String CONNECTION_KEY_DELIMITER = "beans";
    private final TemplateManager manager;

    private final HashMap<String, TemplateRoom> rooms;
    private final HashMap<String, TemplateRoom> connectors;

    private final ArrayList<String> expansionKeys;
    private int roomsPlaced;



    public TemplateGenerator(Random random, GameMap map) {
        super(random, map);
        roomsPlaced = 0;
        manager = new TemplateManager(new String[]{"templates/9x9_no_connectors"});
        expansionKeys = new ArrayList<>();
        rooms = new HashMap<>();
        connectors = new HashMap<>();
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

        if (USE_CONNECTORS) {
            Log.info("generate", "Placed " + roomsPlaced + " rooms");
            for(Map.Entry<String, TemplateRoom> room : rooms.entrySet()) {
                Vector3 roomCoordinates = extractCoordinatesFromRoomKey(room.getKey());
                for(Direction exitDirection : room.getValue().exits) {
                    addRoomConnection(exitDirection, roomCoordinates);
                }
            }
        }

        replaceInvalidRooms();
        replaceDeadends();

        copyRoomTemplates();
        copyConnectorTemplates();
        closeMap();

        initiateFlood(new Vector3(5,0,5), MapTileFillType.FOG);
    }

    private void addRoomConnection(Direction exitDirection, Vector3 roomCoordinates) {
        String roomKey = constructRoomKey(roomCoordinates);
        String neighborKey = constructRoomKey(Direction.advanceVector(exitDirection, roomCoordinates.cpy()));
        if (!connectors.containsKey(constructConnectionKey(roomKey, neighborKey)) && !connectors.containsKey(constructConnectionKey(neighborKey, roomKey))) {
            HashSet<Direction> requiredDirections = new HashSet<>();
            requiredDirections.add(exitDirection);
            ArrayList<TemplateRoom> connectorOptions = manager.getValidConnectorOptions(requiredDirections);
            connectors.put(constructConnectionKey(roomKey, neighborKey), connectorOptions.get(random.nextInt(connectorOptions.size())));
        }
    }

    private void copyRoomTemplates() {
        for (Map.Entry<String, TemplateRoom> roomEntry : rooms.entrySet()) {
            Vector3 roomCoordinates = extractCoordinatesFromRoomKey(roomEntry.getKey());
            TemplateRoom template = roomEntry.getValue();

            // Rooms are 7x7x7, so translate room coordinates to world coordinates
            int baseX = (int)roomCoordinates.x * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));
            int baseY = (int)roomCoordinates.y * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));
            int baseZ = (int)roomCoordinates.z * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));

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
            if (random.nextFloat() > 0.8f) {
                addLight(new Vector3(baseZ + 5,baseY + 6,  baseX + 5));
            }
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
            int baseX = (int)room1Coords.x * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));
            int baseY = (int)room1Coords.y * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));
            int baseZ = (int)room1Coords.z * (ROOM_SIZE + (USE_CONNECTORS ? 1 : 0));

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

    private void replaceInvalidRooms() {
        int invalidRooms = 0;
        int fixedRooms = 0;

        // Create a copy of the entry set to avoid concurrent modification
        Set<Map.Entry<String, TemplateRoom>> roomEntries = new HashSet<>(rooms.entrySet());

        for (Map.Entry<String, TemplateRoom> roomEntry : roomEntries) {
            String roomKey = roomEntry.getKey();
            TemplateRoom currentRoom = roomEntry.getValue();

            // Skip spawn room - it's always valid
            if (currentRoom.template_name.startsWith("spawn")) {
                continue;
            }

            // Determine what connections this room actually needs
            HashSet<Direction> requiredConnections = gatherValidRoomEntranceRequirements(roomKey);

            // Check if current room exactly matches the requirements (no more, no less)
            boolean isValid = currentRoom.entrances.equals(requiredConnections);

            if (!isValid) {
                invalidRooms++;

                // Find a better matching room that has EXACTLY the required connections
                List<TemplateRoom> allOptions = manager.getValidRoomOptions(requiredConnections);
                List<TemplateRoom> exactOptions = allOptions.stream()
                    .filter(room -> room.entrances.equals(requiredConnections))
                    .collect(Collectors.toList());

                if (!exactOptions.isEmpty()) {
                    // Replace in-place with an exact match
                    TemplateRoom replacement = exactOptions.get(random.nextInt(exactOptions.size()));
                    rooms.put(roomKey, replacement);
                    fixedRooms++;
                } else if (!allOptions.isEmpty()) {
                    // Fall back to any valid option if no exact match
                    TemplateRoom replacement = allOptions.get(random.nextInt(allOptions.size()));
                    rooms.put(roomKey, replacement);
                    fixedRooms++;
                }
            }
        }
    }

    private void replaceDeadends() {
        int deadendRooms = 0;
        int fixedRooms = 0;

        // Create a copy of the entry set to avoid concurrent modification
        Set<Map.Entry<String, TemplateRoom>> roomEntries = new HashSet<>(rooms.entrySet());

        for (Map.Entry<String, TemplateRoom> roomEntry : roomEntries) {
            String roomKey = roomEntry.getKey();
            TemplateRoom currentRoom = roomEntry.getValue();
            Vector3 roomCoords = extractCoordinatesFromRoomKey(roomKey);

            // Skip spawn room - it's always valid
            if (currentRoom.template_name.startsWith("spawn")) {
                continue;
            }

            // Check which exits lead to empty spaces (no neighboring room)
            HashSet<Direction> exitsToEmpty = new HashSet<>();
            for (Direction exitDir : currentRoom.exits) {
                Vector3 neighborCoords = Direction.advanceVector(exitDir, roomCoords.cpy());
                String neighborKey = constructRoomKey(neighborCoords);
                if (!rooms.containsKey(neighborKey)) {
                    exitsToEmpty.add(exitDir);
                }
            }

            if (!exitsToEmpty.isEmpty()) {
                // This room has exits that lead to empty space - it's a deadend candidate

                deadendRooms++;

                // Calculate what connections this room should actually have
                // (connections to existing neighboring rooms only - no exits to empty space)
                HashSet<Direction> requiredConnections = gatherValidRoomEntranceRequirements(roomKey);

                // Find rooms that match the required connections exactly (no deadend exits)
                List<TemplateRoom> allOptions = manager.getValidRoomOptions(requiredConnections);
                List<TemplateRoom> exactOptions = allOptions.stream()
                    .filter(room -> {
                        // Room must have exactly the required connections and no extras
                        return room.entrances.equals(requiredConnections) && room.exits.equals(requiredConnections);
                    })
                    .collect(Collectors.toList());

                if (!exactOptions.isEmpty()) {
                    // Replace in-place with an exact match that won't have deadend exits
                    TemplateRoom replacement = exactOptions.get(random.nextInt(exactOptions.size()));
                    rooms.put(roomKey, replacement);
                    fixedRooms++;
                } else {
                    // Try to find rooms with fewer total exits to minimize deadends
                    List<TemplateRoom> betterOptions = allOptions.stream()
                        .filter(room -> {
                            // Only consider rooms that have the required connections
                            // and ideally fewer total exits than current room
                            return room.entrances.containsAll(requiredConnections) &&
                                   room.exits.size() <= currentRoom.exits.size();
                        })
                        .sorted((a, b) -> Integer.compare(a.exits.size(), b.exits.size())) // Prefer rooms with fewer exits
                        .collect(Collectors.toList());

                    if (!betterOptions.isEmpty()) {
                        TemplateRoom replacement = betterOptions.get(0); // Use the one with fewest exits
                        rooms.put(roomKey, replacement);
                        fixedRooms++;
                    }
                }
            }
        }
    }
}
