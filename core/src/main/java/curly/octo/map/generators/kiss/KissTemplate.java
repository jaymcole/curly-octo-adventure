package curly.octo.map.generators.kiss;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import curly.octo.map.enums.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class KissTemplate {

    public static final int WALL_ALPHA_VALUE = 255;
    public static final int ENTRANCE_ALPHA_VALUE = 127;
    public static final int OPEN_ALPHA_VALUE = 0;

    public String name;
    public String originalTemplatePath;
    public Color[][][] templatePixels;

    public ArrayList<Vector3> wallTiles;
    public ArrayList<Vector3> openTiles;
    public final ArrayList<KissEntrance> templatesEntrances;

    private final HashMap<Integer, ArrayList<Vector3>> entranceColorToEntrancePixelsMap;

    public KissTemplate(Color[][][] templatePixels) {
        templatesEntrances = new ArrayList<>();
        entranceColorToEntrancePixelsMap = new HashMap<>();
        this.templatePixels = templatePixels;
        wallTiles = new ArrayList<>();
        openTiles = new ArrayList<>();
        processPixels();
        compileEntrances();
    }

    private void processPixels() {
        for(int slice = 0; slice < templatePixels.length; slice++) {
            for(int x = 0; x < templatePixels[slice].length; x++) {
                for(int z = 0; z < templatePixels[slice][x].length; z++) {
                    processPixel(slice, x, z, templatePixels[slice][x][z]);
                }
            }
        }
    }

    private void processPixel(int slice, int x, int z, Color pixelColor) {
        if (pixelColor.a > 0) {
            String typeValue = (int)(255 * pixelColor.r) + "";
            switch(typeValue) {
                case WALL_ALPHA_VALUE + "":
                    wallTiles.add(new Vector3(x, slice, z));
                    break;
                case ENTRANCE_ALPHA_VALUE + "":
                    addPixelToEntrancesMap(slice, x, z, pixelColor);
                    openTiles.add(new Vector3(x, slice, z));
                    break;
            }
        } else {
            openTiles.add(new Vector3(x, slice, z));
        }

    }

    private void addPixelToEntrancesMap(int slice, int x, int z, Color pixelColor) {
        int intBits = pixelColor.toIntBits();
        if (!entranceColorToEntrancePixelsMap.containsKey(intBits)) {
            entranceColorToEntrancePixelsMap.put(intBits, new ArrayList<>());
        }
        entranceColorToEntrancePixelsMap.get(intBits).add(new Vector3(x, slice, z));
    }

    private void compileEntrances() {
        for(ArrayList<Vector3> entranceGroup : entranceColorToEntrancePixelsMap.values()) {
            HashMap<Direction, Integer> directionCount = new HashMap<>();

            entranceGroup.sort(Comparator
                .comparingDouble((Vector3 v) -> v.x)
                .thenComparingDouble(v -> v.y)
                .thenComparingDouble(v -> v.z));

            int entranceHash = calculateEntranceHash(entranceGroup);

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for(Vector3 pixel : entranceGroup) {
                minX = Math.min(minX, (int)pixel.x);
                minY = Math.min(minY, (int)pixel.y);
                minZ = Math.min(minZ, (int)pixel.z);
                countPossibleEntranceDirections(directionCount, pixel);
            }
            Direction mostLikelyDirection = Direction.UP;
            int bestCount = 0;
            for(Map.Entry<Direction, Integer> entry : directionCount.entrySet()) {
                if (entry.getValue() > bestCount) {
                    bestCount = entry.getValue();
                    mostLikelyDirection = entry.getKey();
                }
            }

            KissEntrance entrance = new KissEntrance(entranceHash, minX, minY, minZ, this, mostLikelyDirection);
            templatesEntrances.add(entrance);
        }
    }

    private void countPossibleEntranceDirections(HashMap<Direction, Integer> directionCount, Vector3 pixel) {
        int x = (int)pixel.x, slice = (int) pixel.y, z = (int) pixel.z;

        if (slice == 0) {
            addDirectionCount(directionCount, Direction.DOWN);
        } else if (slice == templatePixels.length -1) {
            addDirectionCount(directionCount, Direction.UP);
        }

        if (x == 0) {
            addDirectionCount(directionCount, Direction.WEST);
        } else if (x == templatePixels[templatePixels.length-1].length-1) {
            addDirectionCount(directionCount, Direction.EAST);
        }

        if (z == 0) {
            addDirectionCount(directionCount, Direction.SOUTH);
        } else if (z == templatePixels[templatePixels.length-1][templatePixels[0].length-1].length-1) {
            addDirectionCount(directionCount, Direction.NORTH);
        }
    }

    private void addDirectionCount(HashMap<Direction, Integer> directionCount, Direction direction) {
        directionCount.put(direction, directionCount.getOrDefault(direction, 0) + 1);
    }

    /**
     * Calculates a rotation-invariant hash code for an entrance shape.
     * Normalizes coordinates relative to the minimum corner so rotated versions of the same shape have the same hash.
     */
    private int calculateEntranceHash(ArrayList<Vector3> sortedVectors) {
        if (sortedVectors.isEmpty()) {
            return 0;
        }

        // Find the minimum coordinates to use as origin
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (Vector3 v : sortedVectors) {
            minX = Math.min(minX, (int)v.x);
            minY = Math.min(minY, (int)v.y);
            minZ = Math.min(minZ, (int)v.z);
        }

        // Calculate hash using normalized coordinates
        int hash = 17; // Start with a prime number
        for (Vector3 v : sortedVectors) {
            // Normalize relative to minimum corner
            int normalizedX = (int)v.x - minX;
            int normalizedY = (int)v.y - minY;
            int normalizedZ = (int)v.z - minZ;

            // Combine coordinates using prime number multiplication
            hash = 31 * hash + normalizedX;
            hash = 31 * hash + normalizedY;
            hash = 31 * hash + normalizedZ;
        }
        return hash;
    }

}
