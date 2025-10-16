package curly.octo.map.generators;

import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.generators.kiss.KissCatalog;
import curly.octo.map.generators.kiss.KissEntrance;
import curly.octo.map.generators.kiss.KissTemplate;
import curly.octo.map.generators.kiss.KissTemplateReader;

import java.util.ArrayList;
import java.util.Random;

public class KissGenerator extends MapGenerator{

    private class EntranceOffset {
        public KissEntrance entrance;
        public Vector3 worldOffset;

        public EntranceOffset(KissEntrance entrance, Vector3 worldOffset) {
            this.entrance = entrance;
            this.worldOffset = worldOffset;
        }
    }

    private class PlacedTemplate {
        public KissTemplate template;
        public Vector3 worldOffset;

        public PlacedTemplate(KissTemplate template, Vector3 worldOffset) {
            this.template = template;
            this.worldOffset = worldOffset;
        }
    }

    private final KissCatalog catalog;
    private final KissTemplate spawnRoom;

    public KissGenerator(Random random, GameMap map) {
        super(random, map);
        ArrayList<KissTemplate> templates = KissTemplateReader.createTemplates("template_kiss/direction_room");
        templates.addAll(KissTemplateReader.createTemplates("template_kiss/shaft"));
        Log.info("KissGenerator", "Loaded " + templates.size() + " templates");
        spawnRoom = templates.get(random.nextInt(templates.size()));
        Log.info("KissGenerator", "Spawn room: " + spawnRoom.name + " with " + spawnRoom.templatesEntrances.size() + " entrances");
        catalog = new KissCatalog();
        for(KissTemplate template : templates) {
            catalog.addTemplate(template);
        }
    }

    @Override
    public void generate() {
        ArrayList<PlacedTemplate> placedTemplates = new ArrayList<>();
        ArrayList<EntranceOffset> availableEntrances = new ArrayList<>();

        // Step 1: Place spawn room at origin
        Vector3 spawnOffset = new Vector3(0, 0, 0);
        placedTemplates.add(new PlacedTemplate(spawnRoom, spawnOffset));
        addSpawn(new Vector3(2,2,2));

        // Step 2: Extract spawn room entrances and add to queue with world offsets
        for (KissEntrance entrance : spawnRoom.templatesEntrances) {
            Vector3 entranceWorldPos = new Vector3(
                entrance.offsetX + spawnOffset.x,
                entrance.offsetY + spawnOffset.y,
                entrance.offsetZ + spawnOffset.z
            );
            availableEntrances.add(new EntranceOffset(entrance, entranceWorldPos));
        }

        // Step 3: Iteratively place templates
        int maxRooms = 20; // Limit number of rooms
        Log.info("KissGenerator", "Starting placement with " + availableEntrances.size() + " available entrances");

        while (!availableEntrances.isEmpty() && placedTemplates.size() < maxRooms) {
            // Pick random entrance from queue
            int entranceIndex = random.nextInt(availableEntrances.size());
            EntranceOffset currentEntrance = availableEntrances.remove(entranceIndex);

            Log.info("KissGenerator", "Attempting placement #" + placedTemplates.size() + ", entrances remaining: " + availableEntrances.size());
            Log.info("KissGenerator", "Current entrance key: " + currentEntrance.entrance.getKey() + " matching key: " + currentEntrance.entrance.getMatchingKey());

            // Get compatible entrances from catalog
            ArrayList<KissEntrance> compatibleEntrances = catalog.getCompatibleEntrances(currentEntrance.entrance);

            Log.info("KissGenerator", "Found " + compatibleEntrances.size() + " compatible entrances");

            if (compatibleEntrances.isEmpty()) {
                continue; // No compatible templates, skip this entrance
            }

            // Pick random compatible entrance
            KissEntrance matchingEntrance = compatibleEntrances.get(random.nextInt(compatibleEntrances.size()));
            Log.info("KissGenerator", "Selected matching entrance from template: " + matchingEntrance.associatedTemplate.name);

            // Calculate world offset for new template
            // The matching entrance should align with current entrance
            // newTemplateOffset + matchingEntrance.offset = currentEntrance.worldOffset
            // Therefore: newTemplateOffset = currentEntrance.worldOffset - matchingEntrance.offset
            Vector3 newTemplateOffset = new Vector3(
                currentEntrance.worldOffset.x - matchingEntrance.offsetX,
                currentEntrance.worldOffset.y - matchingEntrance.offsetY,
                currentEntrance.worldOffset.z - matchingEntrance.offsetZ
            );

            // Check for overlap before placing
            if (wouldOverlap(matchingEntrance.associatedTemplate, newTemplateOffset, placedTemplates)) {
                Log.info("KissGenerator", "Template would overlap, skipping placement");
                continue; // Skip this entrance and try another
            }

            // Add new template to placed templates
            placedTemplates.add(new PlacedTemplate(matchingEntrance.associatedTemplate, newTemplateOffset));
            Log.info("KissGenerator", "Placed template at offset: " + newTemplateOffset);

            // Extract new template's entrances and add to queue (except the one we just used)
            for (KissEntrance entrance : matchingEntrance.associatedTemplate.templatesEntrances) {
                if (entrance == matchingEntrance) {
                    continue; // Skip the entrance we just connected
                }

                Vector3 entranceWorldPos = new Vector3(
                    entrance.offsetX + newTemplateOffset.x,
                    entrance.offsetY + newTemplateOffset.y,
                    entrance.offsetZ + newTemplateOffset.z
                );
                availableEntrances.add(new EntranceOffset(entrance, entranceWorldPos));
            }
        }

        Log.info("KissGenerator", "Placement complete. Placed " + placedTemplates.size() + " templates total");

        // Step 4: Stamp all placed templates onto the map
        for (PlacedTemplate placed : placedTemplates) {
            stampTemplate(placed.template, placed.worldOffset);

            // Add light at center of template
            int centerX = placed.template.templatePixels[0].length / 2;
            int centerY = placed.template.templatePixels.length / 2;
            int centerZ = placed.template.templatePixels[0][0].length / 2;

            Vector3 lightPos = new Vector3(
                centerX + placed.worldOffset.x,
                centerY + placed.worldOffset.y + 2, // Offset up a bit from center
                centerZ + placed.worldOffset.z
            );
            addLight(lightPos);
        }

        closeMap();
    }

    /**
     * Checks if a template at the given offset would overlap with any already-placed templates.
     * Uses axis-aligned bounding box (AABB) collision detection with tolerance for doorway connections.
     * Allows small overlaps (up to 3 blocks in any dimension) to accommodate entrance connections.
     */
    private boolean wouldOverlap(KissTemplate template, Vector3 newOffset, ArrayList<PlacedTemplate> placedTemplates) {
        // Tolerance for doorway overlaps (in voxels)
        final int DOORWAY_TOLERANCE = 3;

        // Calculate bounds of the new template
        int newMinX = (int)newOffset.x;
        int newMinY = (int)newOffset.y;
        int newMinZ = (int)newOffset.z;
        int newMaxX = newMinX + template.templatePixels[0].length;
        int newMaxY = newMinY + template.templatePixels.length;
        int newMaxZ = newMinZ + template.templatePixels[0][0].length;

        // Check against all placed templates
        for (PlacedTemplate placed : placedTemplates) {
            int placedMinX = (int)placed.worldOffset.x;
            int placedMinY = (int)placed.worldOffset.y;
            int placedMinZ = (int)placed.worldOffset.z;
            int placedMaxX = placedMinX + placed.template.templatePixels[0].length;
            int placedMaxY = placedMinY + placed.template.templatePixels.length;
            int placedMaxZ = placedMinZ + placed.template.templatePixels[0][0].length;

            // AABB overlap test: boxes overlap if they overlap in all 3 axes
            boolean overlapX = newMinX < placedMaxX && newMaxX > placedMinX;
            boolean overlapY = newMinY < placedMaxY && newMaxY > placedMinY;
            boolean overlapZ = newMinZ < placedMaxZ && newMaxZ > placedMinZ;

            if (overlapX && overlapY && overlapZ) {
                // Calculate overlap dimensions
                int overlapSizeX = Math.min(newMaxX, placedMaxX) - Math.max(newMinX, placedMinX);
                int overlapSizeY = Math.min(newMaxY, placedMaxY) - Math.max(newMinY, placedMinY);
                int overlapSizeZ = Math.min(newMaxZ, placedMaxZ) - Math.max(newMinZ, placedMinZ);

                // Allow small overlaps for doorways (must be small in at least one dimension)
                boolean isSmallOverlap = overlapSizeX <= DOORWAY_TOLERANCE ||
                                        overlapSizeY <= DOORWAY_TOLERANCE ||
                                        overlapSizeZ <= DOORWAY_TOLERANCE;

                if (!isSmallOverlap) {
                    return true; // Significant overlap detected
                }
            }
        }

        return false; // No significant overlap
    }

    /**
     * Stamps a template onto the game map at the specified world offset.
     * Converts template voxels to map tiles and places them in the world.
     */
    private void stampTemplate(KissTemplate template, Vector3 worldOffset) {
        // Stamp wall tiles (solid blocks)
        for (Vector3 tilePos : template.wallTiles) {
            int worldX = (int)(tilePos.x + worldOffset.x);
            int worldY = (int)(tilePos.y + worldOffset.y);
            int worldZ = (int)(tilePos.z + worldOffset.z);
            MapTile tile = map.touchTile(worldX, worldY, worldZ);
            tile.geometryType = MapTileGeometryType.FULL;
        }

        // Stamp open tiles (air spaces within the template)
        for (Vector3 tilePos : template.openTiles) {
            int worldX = (int)(tilePos.x + worldOffset.x);
            int worldY = (int)(tilePos.y + worldOffset.y);
            int worldZ = (int)(tilePos.z + worldOffset.z);
            // Ensure air tiles exist in the map
            map.touchTile(worldX, worldY, worldZ);
        }
    }
}
