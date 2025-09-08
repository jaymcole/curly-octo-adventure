package curly.octo.map.generators.templated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Json;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.Direction;

import java.util.*;

public class TemplateManager {

    public static final String COLLECTION_METADATA_FILENAME = "collection.json";

    public static final String CONNECTOR_PREFIX = "connector";
    public static int ROOM_SIZE = 7;
    public static String SPAWN_ROOM = "spawn_nesw";
    public static boolean USE_CONNECTORS;

    public ArrayList<TemplateRoom> roomTemplates;
    public ArrayList<TemplateRoom> connectorTemplates;
    private final HashMap<String, ArrayList<TemplateRoom>> validRoomCache;
    private final HashMap<String, ArrayList<TemplateRoom>> validConnectorCache;

    public TemplateManager(String[] templatePaths) {
        roomTemplates = new ArrayList<>();
        connectorTemplates = new ArrayList<>();
        validRoomCache = new HashMap<>();
        validConnectorCache = new HashMap<>();

        HashMap<String, TemplateCollection> collections = loadCollections(templatePaths);
        for(Map.Entry<String, TemplateCollection> collectionMetadata : collections.entrySet()) {
            for (String templateName : collectionMetadata.getValue().templates) {
                FileHandle templateFile = Gdx.files.internal(collectionMetadata.getKey() + "/" + templateName + ".png");
                if (templateFile.exists()) {
                    try {
                        TemplateRoom room = null;
                        if (templateName.startsWith(CONNECTOR_PREFIX)) {
                            room = loadTemplate(templateFile);
                            connectorTemplates.add(room);
                        } else {
                            room = loadTemplate(templateFile);
                            roomTemplates.add(room);

                        }
                        Gdx.app.log("Assets", "Loaded template: " + templateFile.path());
                    } catch (Exception e) {
                        Log.error("TemplateLoader", "Failed to load template " + templateName + ": " + e.getMessage());
                    }
                }
            }
            Log.info("TemplateManager", "Done importing templates");
        }
    }

    private HashMap<String, TemplateCollection> loadCollections(String[] templatePaths) {
        Json json = new Json();
        HashMap<String, TemplateCollection> collections = new HashMap<>();
        for(String collectionPath : templatePaths) {
            FileHandle templateFile = Gdx.files.internal(collectionPath + "/" + COLLECTION_METADATA_FILENAME);
            if (templateFile.exists()) {
                TemplateCollection collection = json.fromJson(TemplateCollection.class, templateFile);
                if (collection != null) {
                    collections.put(collectionPath, collection);
                    SPAWN_ROOM = collection.spawn_room;
                    ROOM_SIZE = collection.templateDimensionDepth;
                    USE_CONNECTORS = collection.use_connectors;
                } else {
                    Log.error("loadCollections", "Failed to parse collection metadata file for " + collectionPath);
                }
            } else {
                Log.error("loadCollections", "Missing collection metadata for " + collectionPath);
            }
        }

        return collections;
    }

    private TemplateRoom loadTemplate(FileHandle templateFile) {
        Pixmap pixmap = null;

        try {
            pixmap = new Pixmap(templateFile);

            int imageWidth = pixmap.getWidth();
            int imageHeight = pixmap.getHeight();

            if (imageWidth % imageHeight != 0) {
                throw new IllegalArgumentException("Image width (" + imageWidth +
                    ") is not an even multiple of sliceWidth (" + imageHeight + ")");
            }

            int depth = imageWidth / imageHeight;
            int[][][] walls = new int[depth][imageHeight][imageHeight];

            for (int slice = 0; slice < depth; slice++) {
                for (int x = 0; x < imageHeight; x++) {
                    for (int z = 0; z < imageHeight; z++) {
                        int pixelX = x + (slice * imageHeight);
                        int pixel = pixmap.getPixel(pixelX, z);
                        int alpha = (pixel & 0x000000FF);
                        walls[slice][z][x] = (alpha == 0) ? 1 : 0;
                    }
                }
            }

            String templateName = templateFile.nameWithoutExtension();
            return new TemplateRoom(templateName, walls);

        } catch (Exception e) {
            Log.error("TemplateLoader", "Failed to load template " + templateFile.path() + ": " + e.getMessage());
            throw new RuntimeException("Could not load template: " + templateFile.path(), e);
        } finally {
            if (pixmap != null) {
                pixmap.dispose();
            }
        }
    }

    public TemplateRoom getTemplateByFileName (String fileName) {
        for(TemplateRoom room : roomTemplates) {
            if (room.template_name.startsWith(fileName)) {
                return room;
            }
        }
        return null;
    }

    public ArrayList<TemplateRoom> getValidRoomOptions(HashSet<Direction> enteringDirections) {
        String cacheKey = constructValidRoomCacheKey(enteringDirections);
        if (validRoomCache.containsKey(cacheKey)) {
            return validRoomCache.get(cacheKey);
        }
        ArrayList<TemplateRoom> validRooms =  getValidTemplateOptions(enteringDirections, roomTemplates);
        validRoomCache.put(cacheKey, validRooms);
        return validRooms;
    }

    public ArrayList<TemplateRoom> getValidConnectorOptions(HashSet<Direction> enteringDirections) {
        String cacheKey = constructValidRoomCacheKey(enteringDirections);
        if (validConnectorCache.containsKey(cacheKey)) {
            return validConnectorCache.get(cacheKey);
        }
        ArrayList<TemplateRoom> validConnectors =  getValidTemplateOptions(enteringDirections, connectorTemplates);
        validConnectorCache.put(cacheKey, validConnectors);
        return validConnectors;
    }

    private ArrayList<TemplateRoom> getValidTemplateOptions(HashSet<Direction> enteringDirections, ArrayList<TemplateRoom> templates) {
        ArrayList<TemplateRoom> validTemplates = new ArrayList<>();
        for(TemplateRoom possibleRoomTemplate : templates) {
            if (possibleRoomTemplate.isValidRoom(enteringDirections)) {
                validTemplates.add(possibleRoomTemplate);
            }
        }
        return validTemplates;
    }

    private String constructValidRoomCacheKey(HashSet<Direction> enteringDirections) {
        String cacheKey = "";
        if (enteringDirections.contains(Direction.NORTH)) {
            cacheKey += "n";
        }
        if (enteringDirections.contains(Direction.EAST)) {
            cacheKey += "e";
        }
        if (enteringDirections.contains(Direction.SOUTH)) {
            cacheKey += "s";
        }
        if (enteringDirections.contains(Direction.WEST)) {
            cacheKey += "w";
        }
        return cacheKey;
    }

}
