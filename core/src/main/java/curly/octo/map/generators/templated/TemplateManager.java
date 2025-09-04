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


    public TemplateManager(String[] templatePaths) {
        roomTemplates = new ArrayList<>();
        connectorTemplates = new ArrayList<>();
        validRoomCache = new HashMap<>();
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
                            room = loadConnectorTemplate(templateFile);
                            connectorTemplates.add(room);
                        } else {
                            room = loadTemplate(templateFile, ROOM_SIZE);
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

    private TemplateRoom loadConnectorTemplate (FileHandle templateFile) {
        return loadTemplate(templateFile, ROOM_SIZE + 2);
    }

    private TemplateRoom loadTemplate(FileHandle templateFile, int sliceWidth) {
        Pixmap pixmap = null;

        try {
            pixmap = new Pixmap(templateFile);

            int imageWidth = pixmap.getWidth();
            int imageHeight = pixmap.getHeight();

            if (imageWidth % sliceWidth != 0) {
                throw new IllegalArgumentException("Image width (" + imageWidth +
                    ") is not an even multiple of sliceWidth (" + sliceWidth + ")");
            }

            int depth = imageWidth / sliceWidth;
            int[][][] walls = new int[imageHeight][sliceWidth][depth];

            for (int slice = 0; slice < depth; slice++) {
                for (int x = 0; x < sliceWidth; x++) {
                    for (int z = 0; z < sliceWidth; z++) {
                        int pixelX = x + (slice * sliceWidth);
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
        return getValidTemplateOptions(enteringDirections, roomTemplates);
    }

    public ArrayList<TemplateRoom> getValidConnectorOptions(HashSet<Direction> enteringDirections) {
        return getValidTemplateOptions(enteringDirections, connectorTemplates);
    }

    private ArrayList<TemplateRoom> getValidTemplateOptions(HashSet<Direction> enteringDirections, ArrayList<TemplateRoom> templates) {
        ArrayList<TemplateRoom> validRooms = new ArrayList<>();
        String cacheKey = constructValidRoomCacheKey(enteringDirections);
        if (validRoomCache.containsKey(cacheKey)) {
            return validRoomCache.get(cacheKey);
        }

        for(TemplateRoom possibleRoomTemplate : templates) {
            if (possibleRoomTemplate.isValidRoom(enteringDirections)) {
                validRooms.add(possibleRoomTemplate);
            }
        }
        validRoomCache.put(cacheKey, validRooms);
        return validRooms;
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
