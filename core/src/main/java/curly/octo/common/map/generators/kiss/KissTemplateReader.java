package curly.octo.common.map.generators.kiss;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.esotericsoftware.minlog.Log;

import java.util.ArrayList;

import static com.badlogic.gdx.net.HttpRequestBuilder.json;

public class KissTemplateReader {

    public static ArrayList<KissTemplate> createTemplates(String templatePath) {
        ArrayList<KissTemplate> templates = new ArrayList<>();
        KissJson json = readKissJsonFile(templatePath + ".json");

        for(String rotation : json.rotations) {
            templates.add(readTemplate(templatePath, json, rotation));
        }
        return templates;
    }

    private static KissTemplate readTemplate(String templatePath, KissJson json, String rotation) {
        Color[][][] templatePixels = readTemplatePixelsFromPNG(templatePath, json);

        switch(rotation) {
            case "90":
                templatePixels = rotatePixelArray90Degrees(templatePixels);
                break;
            case "180":
                templatePixels = rotatePixelArray180Degrees(templatePixels);
                break;
            case "270":
                templatePixels = rotatePixelArray270Degrees(templatePixels);
                break;
        }

        KissTemplate newTemplate = new KissTemplate(templatePixels, json);
        newTemplate.name = extractTemplateNameFromFilePath(templatePath) + "_" + rotation;
        newTemplate.originalTemplatePath = templatePath;
//        printTemplateSlices(newTemplate);
        return newTemplate;
    }

    private static String extractTemplateNameFromFilePath(String templatePath) {
        int lastSlash = Math.max(templatePath.lastIndexOf('/'), templatePath.lastIndexOf('\\'));
        String filename = templatePath.substring(lastSlash + 1);

        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    private static Color[][][] rotatePixelArray270Degrees(Color[][][] pixels) {
        Color[][][] rotatedPixels = rotatePixelArray180Degrees(pixels);
        return rotatePixelArray90Degrees(rotatedPixels);
    }

    private static Color[][][] rotatePixelArray180Degrees(Color[][][] pixels) {
        Color[][][] rotatedPixels = rotatePixelArray90Degrees(pixels);
        return rotatePixelArray90Degrees(rotatedPixels);
    }

    private static Color[][][] rotatePixelArray90Degrees(Color[][][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        int depth = pixels[0][0].length;
        Color[][][] rotated = new Color[height][depth][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    int newX = z;
                    int newZ = width - 1 - x;
                    rotated[y][newX][newZ] = pixels[y][x][z];
                }
            }
        }
        return rotated;
    }

    /**
     * Maps RGB color values to alpha values for tile type encoding.
     * Since Goxel doesn't support per-voxel alpha, we use RGB colors to represent different tile types.
     *
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return Alpha value (0.0-1.0) representing the tile type
     */
    private static float mapColorToAlpha(int r, int g, int b) {
        // White or near-white = Solid walls (alpha 1.0)
        if (r >= 200 && g >= 200 && b >= 200) {
            return 1.0f;  // KissTemplate.WALL_ALPHA_VALUE
        }

        // Gray or mid-range colors = Entrances (alpha 0.5)
        if (r >= 100 && r <= 180 && g >= 100 && g <= 180 && b >= 100 && b <= 180) {
            return 0.5f;  // KissTemplate.ENTRANCE_ALPHA_VALUE
        }

        // Black or very dark = Open/air tiles (alpha 0.0)
        if (r < 50 && g < 50 && b < 50) {
            return 0.0f;  // KissTemplate.OPEN_ALPHA_VALUE
        }

        // Default to solid wall for any other color
        return 1.0f;
    }

    private static Color[][][] readTemplatePixelsFromPNG(String templatePath, KissJson json) {
        Color[][][] templatePixels = new Color[json.height][json.width][json.depth];

        FileHandle templateFileHandle = Gdx.files.internal(templatePath + ".png");
        Pixmap pixmap = new Pixmap(templateFileHandle);
        for(int slice = 0; slice < pixmap.getWidth() / json.width; slice++) {
            for(int x = 0; x < json.width; x++) {
                for(int z = 0; z < json.depth; z++) {
                    int xOffset = slice * json.width;
                    // Flip Z-axis: Pixmap Y-axis is top-to-bottom (0=top), but we want bottom-to-top in 3D space
                    int pixmapY = (json.depth - 1) - z;
                    Color pixelColor = new Color(pixmap.getPixel(x + xOffset, pixmapY));
                    templatePixels[slice][x][z] = pixelColor;
                }
            }
        }
        pixmap.dispose();
        return templatePixels;
    }

    private static KissJson readKissJsonFile(String templatePath) {
        try {
            String jsonPath = templatePath.replace(".png", ".json");
            FileHandle jsonFileHandle = Gdx.files.internal(jsonPath);
            return json.fromJson(KissJson.class, jsonFileHandle);
        } catch (Exception e) {
            Log.error("readKissJsonFile","Missing companion json file for: " + templatePath);
            return new KissJson();
        }
    }

    private static void printTemplateSlices(KissTemplate newTemplate) {
        for(int slice = 0; slice < newTemplate.templatePixels.length; slice++) {
            printSlice(newTemplate.name, newTemplate.originalTemplatePath, newTemplate.templatePixels[slice], slice);
        }
    }

    private static void printSlice(String templateName, String templatePath, Color[][] slice, int sliceLayer) {
        // Extract the directory and filename from templatePath
        String directory = templatePath.substring(0, templatePath.lastIndexOf('/') + 1);
        // Create the new filename with slice layer
        String outputFilename = directory + templateName + "_slice_" + sliceLayer + ".png";
        // Create a Pixmap from the 2D color array
        // slice[x][z] matches how we read: pixmap.getPixel(x + xOffset, z)
        int width = slice.length;
        int depth = slice[0].length;
        Pixmap pixmap = new Pixmap(width, depth, Pixmap.Format.RGBA8888);

        // Fill the pixmap with colors from the array, using z for vertical axis to match reading
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                pixmap.setColor(slice[x][z]);
                pixmap.drawPixel(x, z);
            }
        }
        // Write the pixmap to a file
        try {
            FileHandle outputFile = Gdx.files.local(outputFilename);
            PixmapIO.writePNG(outputFile, pixmap);
            Log.info("printOutSlice", "Saved slice to: " + outputFilename);
        } catch (Exception e) {
            Log.error("printOutSlice", "Failed to save slice: " + outputFilename, e);
        } finally {
            pixmap.dispose();
        }
    }
}
