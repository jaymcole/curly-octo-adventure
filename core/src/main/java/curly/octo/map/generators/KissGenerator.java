package curly.octo.map.generators;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;
import curly.octo.map.enums.Direction;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.enums.MapTileGeometryType;
import curly.octo.map.generators.kiss.*;

import java.io.BufferedReader;
import java.io.IOException;
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
//        templates.addAll(KissTemplateReader.createTemplates("template_kiss/shaft"));
//        templates.addAll(KissTemplateReader.createTemplates("template_kiss/big_cave"));
//        templates.addAll(KissTemplateReader.createTemplates("template_kiss/corridor"));
        templates.addAll(KissTemplateReader.createTemplates("template_kiss/corridor_expandable"));
        templates.addAll(KissTemplateReader.createTemplates("template_kiss/spawn_dome"));

        for(String templatePath : loadTemplatesFromAssetsFile("template_kiss/open_room_9x9")) {
            templates.addAll(KissTemplateReader.createTemplates(templatePath));
        }

        Log.info("KissGenerator", "Loaded " + templates.size() + " templates");
        catalog = new KissCatalog();
        for(KissTemplate template : templates) {
            catalog.addTemplate(template);
        }

        ArrayList<KissTemplate> possibleSpawnRooms = catalog.getTemplateByTag(KissTags.SPAWN);
        spawnRoom = possibleSpawnRooms.get(random.nextInt(possibleSpawnRooms.size()));
        Log.info("KissGenerator", "Spawn room: " + spawnRoom.name + " with " + spawnRoom.templatesEntrances.size() + " entrances");
    }

    private ArrayList<String> loadTemplatesFromAssetsFile(String directory) {
        ArrayList<String> filesInDirectory = new ArrayList<>();
        FileHandle assetsFile = Gdx.files.internal("assets.txt");
        try (BufferedReader reader = new BufferedReader(assetsFile.reader())) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith(directory) && trimmedLine.endsWith(".gox")) {
                    filesInDirectory.add(trimmedLine.replace(".gox", ""));
                }
            }
        } catch (IOException e) {
            Log.error("TemplateManager", "Error reading assets.txt", e);
        }
        return filesInDirectory;
    }

    @Override
    public void generate() {
        ArrayList<Vector3> possibleLightLocations = new ArrayList<>();
        ArrayList<PlacedTemplate> placedTemplates = new ArrayList<>();
        ArrayList<EntranceOffset> availableEntrances = new ArrayList<>();
        ArrayList<Vector3> floodTiles = new ArrayList<>();

        // Step 1: Place spawn room at origin
        Vector3 spawnOffset = new Vector3(5, 0, 5);
        placedTemplates.add(new PlacedTemplate(spawnRoom, spawnOffset));
        addSpawn(spawnRoom.spawnTiles.get(random.nextInt(spawnRoom.spawnTiles.size())));

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
        int maxRooms = 10; // Limit number of rooms
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
            // The matching entrance should be adjacent to (not overlapping with) the current entrance
            // First, calculate the position where entrances would overlap:
            // newTemplateOffset + matchingEntrance.offset = currentEntrance.worldOffset
            // Then, push the new template away by 1 block in the direction the current entrance faces
            Vector3 overlapOffset = new Vector3(
                currentEntrance.worldOffset.x - matchingEntrance.offsetX,
                currentEntrance.worldOffset.y - matchingEntrance.offsetY,
                currentEntrance.worldOffset.z - matchingEntrance.offsetZ
            );

            // Push templates apart by adding directional offset
            Vector3 separationOffset = directionToOffset(currentEntrance.entrance.outwardFacingDirection);
            Vector3 newTemplateOffset = new Vector3(
                overlapOffset.x + separationOffset.x,
                overlapOffset.y + separationOffset.y,
                overlapOffset.z + separationOffset.z
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

            for(Vector3 templateLightPosition : placed.template.lightTiles) {
                Vector3 lightPos = new Vector3(
                    templateLightPosition.x + placed.worldOffset.x,
                    templateLightPosition.y + placed.worldOffset.y,
                    templateLightPosition.z + placed.worldOffset.z
                );
                addLight(lightPos);
            }

            for(Vector3 floodTile : placed.template.floodTiles) {
                Vector3 floodPosition = new Vector3(
                    floodTile.x + placed.worldOffset.x,
                    floodTile.y + placed.worldOffset.y,
                    floodTile.z + placed.worldOffset.z
                );
                floodTiles.add(floodPosition);
            }
        }

        closeMap();
        floodMap(floodTiles);
    }

    private void floodMap(ArrayList<Vector3> floodTiles) {
        for(Vector3 floodTile : floodTiles) {
            initiateFlood(floodTile, MapTileFillType.WATER);
        }
    }

    private Vector3 directionToOffset(Direction direction) {
        switch(direction) {
            case NORTH: return new Vector3(0, 0, 1);
            case SOUTH: return new Vector3(0, 0, -1);
            case EAST: return new Vector3(1, 0, 0);
            case WEST: return new Vector3(-1, 0, 0);
            case UP: return new Vector3(0, 1, 0);
            case DOWN: return new Vector3(0, -1, 0);
            default: return new Vector3(0, 0, 0);
        }
    }

    /**
     * Checks if a template at the given offset would overlap with any already-placed templates.
     * Uses axis-aligned bounding box (AABB) collision detection with tolerance for doorway connections.
     * Allows small overlaps (up to 1 block in any dimension) to accommodate entrance connections.
     */
    private boolean wouldOverlap(KissTemplate template, Vector3 newOffset, ArrayList<PlacedTemplate> placedTemplates) {
        // Tolerance for doorway overlaps (in voxels)
        final int DOORWAY_TOLERANCE = 1;

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
                // Calculate overlap dimensions and bounds
                int overlapMinX = Math.max(newMinX, placedMinX);
                int overlapMaxX = Math.min(newMaxX, placedMaxX);
                int overlapMinY = Math.max(newMinY, placedMinY);
                int overlapMaxY = Math.min(newMaxY, placedMaxY);
                int overlapMinZ = Math.max(newMinZ, placedMinZ);
                int overlapMaxZ = Math.min(newMaxZ, placedMaxZ);

                int overlapSizeX = overlapMaxX - overlapMinX;
                int overlapSizeY = overlapMaxY - overlapMinY;
                int overlapSizeZ = overlapMaxZ - overlapMinZ;

                // Allow small overlaps for doorways (must be small in at least one dimension)
                boolean isSmallOverlap = overlapSizeX <= DOORWAY_TOLERANCE ||
                                        overlapSizeY <= DOORWAY_TOLERANCE ||
                                        overlapSizeZ <= DOORWAY_TOLERANCE;

                if (!isSmallOverlap) {
                    return true; // Large overlap detected
                }

                // Perform tile-level validation in the overlap region
                // Check if new template would place walls where existing geometry exists
                for (int x = overlapMinX; x < overlapMaxX; x++) {
                    for (int y = overlapMinY; y < overlapMaxY; y++) {
                        for (int z = overlapMinZ; z < overlapMaxZ; z++) {
                            // Check if new template has a wall at this position
                            int templateX = x - (int)newOffset.x;
                            int templateY = y - (int)newOffset.y;
                            int templateZ = z - (int)newOffset.z;

                            // Check bounds within template
                            if (templateX >= 0 && templateX < template.templatePixels[0].length &&
                                templateY >= 0 && templateY < template.templatePixels.length &&
                                templateZ >= 0 && templateZ < template.templatePixels[0][0].length) {

                                // Check if new template has a wall tile at this position
                                boolean newTemplateHasWall = false;
                                for (Vector3 wallTile : template.wallTiles) {
                                    if ((int)wallTile.x == templateX && (int)wallTile.y == templateY && (int)wallTile.z == templateZ) {
                                        newTemplateHasWall = true;
                                        break;
                                    }
                                }

                                // If new template wants to place a wall, check if placed template has a wall there
                                if (newTemplateHasWall && placedTemplateHasWallAt(x, y, z, placed)) {
                                    Log.info("KissGenerator", "Tile-level conflict detected at (" + x + "," + y + "," + z +
                                            ") - new template would place wall on existing wall from " + placed.template.name);
                                    return true; // Conflict: new wall would overwrite existing geometry
                                }
                            }
                        }
                    }
                }

                // Overlap is allowed - it's either all open space or doorway connection
                Log.info("KissGenerator", "Small overlap allowed - validated as doorway connection");
            }
        }

        return false; // No significant overlap
    }

    /**
     * Checks if a placed template has a wall tile at the given world coordinates.
     * Converts world coordinates to template-local coordinates and checks the template's wallTiles.
     */
    private boolean placedTemplateHasWallAt(int worldX, int worldY, int worldZ, PlacedTemplate placedTemplate) {
        // Convert world coordinates to template-local coordinates
        int templateX = worldX - (int)placedTemplate.worldOffset.x;
        int templateY = worldY - (int)placedTemplate.worldOffset.y;
        int templateZ = worldZ - (int)placedTemplate.worldOffset.z;

        // Check if any wall tile in this placed template is at this position
        for (Vector3 wallTile : placedTemplate.template.wallTiles) {
            if ((int)wallTile.x == templateX &&
                (int)wallTile.y == templateY &&
                (int)wallTile.z == templateZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stamps a template onto the game map at the specified world offset.
     * Converts template voxels to map tiles and places them in the world.
     * All conflicts are prevented by wouldOverlap() during placement, so stamping is straightforward.
     */
    private void stampTemplate(KissTemplate template, Vector3 worldOffset) {
        // Stamp wall tiles (solid blocks)
        for (Vector3 tilePos : template.wallTiles) {
            int worldX = (int)(tilePos.x + worldOffset.x);
            int worldY = (int)(tilePos.y + worldOffset.y);
            int worldZ = (int)(tilePos.z + worldOffset.z);

            MapTile tile = map.touchTile(worldX, worldY, worldZ, template.name);
            tile.geometryType = MapTileGeometryType.FULL;
        }

        // Stamp open tiles (air spaces within the template)
        for (Vector3 tilePos : template.openTiles) {
            int worldX = (int)(tilePos.x + worldOffset.x);
            int worldY = (int)(tilePos.y + worldOffset.y);
            int worldZ = (int)(tilePos.z + worldOffset.z);

            MapTile tile = map.touchTile(worldX, worldY, worldZ, template.name);
            tile.geometryType = MapTileGeometryType.EMPTY;
        }
    }
}
