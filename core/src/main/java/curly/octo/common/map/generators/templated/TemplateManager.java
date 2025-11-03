package curly.octo.common.map.generators.templated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.map.enums.Direction;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import static com.badlogic.gdx.net.HttpRequestBuilder.json;

public class TemplateManager {

    public static final String TEMPLATE_FOLDER = "templates";
    public static final String TEMPLATE_EXTENSION = ".png";
    public static final String CONFIG_EXTENSION = ".json";

    public static final String COLLECTION_METADATA_FILENAME = "collection.json";

    public static final String CONNECTOR_PREFIX = "connector";
    public static int ROOM_SIZE = 7;
    public static String SPAWN_ROOM = "spawn_nesw";
    public static boolean USE_CONNECTORS;

    public ArrayList<TemplateRoom> roomTemplates;

    public HashMap<String, ArrayList<TemplateRoom>> collectionNameToRoomTemplatesMap;
    public HashMap<String, TemplateRoom> templateNameToRoomTemplateMap;



    private final HashMap<String, ArrayList<TemplateRoom>> validRoomCache;

    public TemplateManager(String[] templatePaths) {
        collectionNameToRoomTemplatesMap = new HashMap<>();
        templateNameToRoomTemplateMap = new HashMap<>();
        roomTemplates = new ArrayList<>();
        validRoomCache = new HashMap<>();
        loadTemplatesFromAssetsFile();
    }

    private void loadTemplatesFromAssetsFile() {
        FileHandle assetsFile = Gdx.files.internal("assets.txt");
        try (BufferedReader reader = new BufferedReader(assetsFile.reader())) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith(TEMPLATE_FOLDER) && trimmedLine.endsWith(TEMPLATE_EXTENSION)) {
                    String[] directoryParts = trimmedLine.split("/");
                    String collectionName = directoryParts[1];
                    String templateName = directoryParts[2].split("\\.")[0];

                    String configsPath = TEMPLATE_FOLDER + "/" + collectionName + "/" + templateName + CONFIG_EXTENSION;
                    FileHandle room_configs = Gdx.files.internal(configsPath);
                    if (!room_configs.exists()) {
                        Log.error("loadTemplatesFromAssetsFile", "missing config file for: " + configsPath);
                        continue;
                    }
                    TemplateRoomConfigs configs = json.fromJson(TemplateRoomConfigs.class, room_configs);

                    String templatePath = TEMPLATE_FOLDER + "/" + collectionName + "/" + templateName + TEMPLATE_EXTENSION;
                    FileHandle room_template = Gdx.files.internal(templatePath);
                    if (!room_template.exists()) {
                        Log.error("loadTemplatesFromAssetsFile", "missing template file for: " + templatePath);
                        continue;
                    }
                    TemplateRoom room = loadTemplate(collectionName, room_template, configs);
                    roomTemplates.add(room);
                    templateNameToRoomTemplateMap.put(templateName, room);
                    if (!collectionNameToRoomTemplatesMap.containsKey(collectionName)) {
                        collectionNameToRoomTemplatesMap.put(collectionName, new ArrayList<>());
                    }
                    collectionNameToRoomTemplatesMap.get(collectionName).add(room);
                }
            }
        } catch (IOException e) {
            Log.error("TemplateManager", "Error reading assets.txt", e);
        }
    }

    private TemplateRoom loadTemplate(String collectionName,FileHandle templateFile, TemplateRoomConfigs configs) {
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
            return new TemplateRoom(collectionName, templateName, configs, walls);

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

    public ArrayList<TemplateRoom> getValidRoomOptions(String[] collections, String[] templateNames, HashSet<Direction> enteringDirections) {
        String cacheKey = constructValidRoomCacheKey(enteringDirections);
        if (validRoomCache.containsKey(cacheKey)) {
            return validRoomCache.get(cacheKey);
        }
        ArrayList<TemplateRoom> possibleRooms = new ArrayList<>();
        for(String collection : collections) {
            if (collectionNameToRoomTemplatesMap.containsKey(collection)) {
                possibleRooms.addAll(collectionNameToRoomTemplatesMap.get(collection));
            }
        }

        for(String templateName : templateNames) {
            if (templateNameToRoomTemplateMap.containsKey(templateName)) {
                possibleRooms.add(templateNameToRoomTemplateMap.get(templateName));
            }
        }

        ArrayList<TemplateRoom> validRooms =  getValidTemplateOptions(enteringDirections, possibleRooms);
        validRoomCache.put(cacheKey, validRooms);
        return validRooms;
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
