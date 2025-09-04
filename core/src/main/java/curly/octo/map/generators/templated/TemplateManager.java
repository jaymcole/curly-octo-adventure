package curly.octo.map.generators.templated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.enums.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TemplateManager {

    public static final String CONNECTOR_PREFIX = "connector";
    public static int ROOM_SIZE = 7;
    public static final String OPEN_ROOM = "room_nesw";


    public ArrayList<TemplateRoom> roomTemplates;
    public ArrayList<TemplateRoom> connectorTemplates;
    private final HashMap<String, ArrayList<TemplateRoom>> validRoomCache;
    private final HashMap<String, ArrayList<TemplateRoom>> validConnectorCache;


    public TemplateManager(String[] templatePaths) {
        roomTemplates = new ArrayList<>();
        connectorTemplates = new ArrayList<>();
        validRoomCache = new HashMap<>();
        validConnectorCache = new HashMap<>();
        for(String path : templatePaths) {
            // Known template files in the templates directory
            String[] templateFiles = {
                "corridor_e.png", "corridor_n.png", "corridor_s.png", "corridor_w.png",
                "corridor_es.png", "corridor_ew.png", "corridor_ne.png", "corridor_ns.png",
                "corridor_nw.png", "corridor_sw.png", "corridor_nes.png", "corridor_new.png",
                "corridor_nsw.png", "corridor_esw.png", "corridor_nesw.png",
                "connector_e_five.png",
                "connector_e_four.png",
                "connector_e_seven.png",
                "connector_n_five.png",
                "connector_n_four.png",
                "connector_n_seven.png",
                "connector_s_five.png",
                "connector_s_four.png",
                "connector_s_seven.png",
                "connector_w_five.png",
                "connector_w_four.png",
                "connector_w_seven.png",
                "stairs_es_es.png", "room_nesw.png"
            };

            for (String templateName : templateFiles) {
                FileHandle templateFile = Gdx.files.internal(path + "/" + templateName);
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
