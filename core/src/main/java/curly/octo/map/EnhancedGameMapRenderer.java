package curly.octo.map;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import curly.octo.lighting.*;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.MapHint;

/**
 * Enhanced map renderer that uses the new unified lighting system.
 * Supports dynamic lights with/without shadows and baked lightmaps.
 */
public class EnhancedGameMapRenderer implements Disposable {

    private final UnifiedLightingRenderer lightingRenderer;

    // Map data
    private GameMap currentMap;
    private Array<ModelInstance> mapInstances;
    private ObjectMap<String, Texture> lightmaps;

    // Lightmap baking regions (calculated dynamically based on map size)
    private int regionSize = 32; // Default, will be calculated per map

    // Statistics
    private int totalLights = 0;
    private int bakedRegions = 0;
    private long lastBakeTime = 0;

    private boolean disposed = false;

    // Debug rendering
    private boolean debugRenderBakedLights = false;
    private com.badlogic.gdx.graphics.g3d.utils.ModelBuilder debugModelBuilder;
    private com.badlogic.gdx.graphics.g3d.Model debugLightSphere;
    private Array<com.badlogic.gdx.graphics.g3d.ModelInstance> debugLightInstances;


    public EnhancedGameMapRenderer(int maxShadowedLights, int maxUnshadowedLights) {
        lightingRenderer = new UnifiedLightingRenderer(maxShadowedLights, maxUnshadowedLights);
        mapInstances = new Array<>();
        lightmaps = new ObjectMap<>();
        debugLightInstances = new Array<>();

        initializeDebugRendering();

        Log.info("EnhancedGameMapRenderer", "Initialized with " + maxShadowedLights +
            " shadowed and " + maxUnshadowedLights + " unshadowed lights");
    }

    /**
     * Update the map and initialize lighting
     */
    public void updateMap(GameMap map) {
        Log.info("EnhancedGameMapRenderer", "Updating map with enhanced lighting system");
        long startTime = System.currentTimeMillis();

        this.currentMap = map;
        totalLights = 0;
        bakedRegions = 0;

        // Clear previous data
        clearMapData();

        // Calculate optimal region size for this map
        regionSize = calculateOptimalRegionSize(map);

        // Extract and categorize lights from map
        extractAndRegisterLights(map);

        // Bake lightmaps for static lights
        if (hasStaticLights()) {
            bakeMapLightmaps(map);
        }

        // Copy geometry from legacy renderer
        // In a real implementation, you'd create geometry directly in the new system
        // For now, we'll use the existing geometry generation

        long endTime = System.currentTimeMillis();
        lastBakeTime = endTime - startTime;

        Log.info("EnhancedGameMapRenderer", "Map update completed in " + lastBakeTime +
            "ms with " + totalLights + " lights and " + bakedRegions + " baked regions");
    }

    private void clearMapData() {
        // Clear old lightmaps
        for (Texture lightmap : lightmaps.values()) {
            if (lightmap != null) {
                lightmap.dispose();
            }
        }
        lightmaps.clear();

        // Clear light manager
        lightingRenderer.getLightManager().clearAllLights();

        // Clear map instances to force regeneration
        mapInstances.clear();

        Log.info("EnhancedGameMapRenderer", "Cleared map data and instances");
    }

    private void extractAndRegisterLights(GameMap map) {
        LightManager lightManager = lightingRenderer.getLightManager();
        int lightId = 0;

        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    MapTile tile = map.getTile(x, y, z);

                    for (MapHint hint : tile.getHints()) {
                        if (hint instanceof LightHint) {
                            LightHint lightHint = (LightHint) hint;

                            // Create light with enhanced properties
                            float lightX = tile.x + MapTile.TILE_SIZE / 2f;
                            float lightY = tile.y + MapTile.TILE_SIZE / 2f + 2f;
                            float lightZ = tile.z + MapTile.TILE_SIZE / 2f;
                            
                            Log.info("EnhancedGameMapRenderer", "Creating light at tile(" + tile.x + "," + tile.y + "," + tile.z + 
                                ") -> world(" + lightX + "," + lightY + "," + lightZ + ")");
                            
                            Light light = new Light.Builder()
                                .setId("map_light_" + lightId++)
                                .setType(lightHint.lightType)
                                .setPosition(lightX, lightY, lightZ)
                                .setColor(lightHint.color_r, lightHint.color_g, lightHint.color_b)
                                .setIntensity(lightHint.intensity)
                                .setRange(lightHint.range)
                                .setCastsShadows(lightHint.castsShadows)
                                .setBakingPriority(lightHint.bakingPriority)
                                .setEnabled(true)
                                .build();

                            lightManager.addLight(light);
                            totalLights++;

                            Log.info("EnhancedGameMapRenderer", "Added light: " + light);
                        }
                    }
                }
            }
        }

        Log.info("EnhancedGameMapRenderer", "Registered " + totalLights + " lights from map");

        // Print breakdown by light type for debugging  
        Array<Light> baked = lightingRenderer.getLightManager().getBakedLights();
        Log.info("EnhancedGameMapRenderer", "Light breakdown: " + baked.size + " baked lights");
        
        // Update debug light visualization
        updateDebugLightVisualization();
    }

    /**
     * Calculate optimal region size based on map dimensions
     * Aims for 4-16 regions total for good performance/quality balance
     */
    private int calculateOptimalRegionSize(GameMap map) {
        int width = map.getWidth();
        int height = map.getHeight();
        int depth = map.getDepth();

        // Use the largest horizontal dimension (width/depth) as basis
        int maxHorizontal = Math.max(width, depth);

        // Calculate region size to get 4-16 total regions
        int targetRegionSize;
        if (maxHorizontal <= 25) {
            targetRegionSize = maxHorizontal; // Very small maps: 1 region per dimension
        } else if (maxHorizontal <= 50) {
            targetRegionSize = maxHorizontal / 2; // Small maps: 4 regions total (2x2)
        } else if (maxHorizontal <= 100) {
            targetRegionSize = maxHorizontal / 4; // Medium maps: 16 regions total (4x4)
        } else {
            targetRegionSize = 32; // Large maps: cap at 32 for memory management
        }
        // Ensure minimum region size of 8 tiles
        targetRegionSize = Math.max(8, targetRegionSize);

        // Calculate actual number of regions this will create
        int regionsX = (width + targetRegionSize - 1) / targetRegionSize;
        int regionsZ = (depth + targetRegionSize - 1) / targetRegionSize;
        int totalRegions = regionsX * regionsZ;

        Log.info("EnhancedGameMapRenderer", String.format(
            "Calculated region size %d for map %dx%dx%d → %dx%d = %d regions",
            targetRegionSize, width, height, depth, regionsX, regionsZ, totalRegions));

        return targetRegionSize;
    }

    private boolean hasStaticLights() {
        int bakedLightCount = lightingRenderer.getLightManager().getBakedLights().size;
        Log.info("EnhancedGameMapRenderer", "Checking for static lights: found " + bakedLightCount + " baked lights");
        return bakedLightCount > 0;
    }

    private void bakeMapLightmaps(GameMap map) {
        if (!hasStaticLights()) {
            Log.info("EnhancedGameMapRenderer", "No static lights found, skipping lightmap baking");
            return;
        }

        Log.info("EnhancedGameMapRenderer", "Baking lightmaps for static lights...");
        LightmapBaker baker = lightingRenderer.getLightmapBaker();
        Array<Light> staticLights = lightingRenderer.getLightManager().getBakedLights();

        // Bake lightmaps in regions to handle large maps efficiently
        int mapWidth = map.getWidth();
        int mapHeight = map.getHeight();
        int mapDepth = map.getDepth();

        for (int regionX = 0; regionX < mapWidth; regionX += regionSize) {
            for (int regionY = 0; regionY < mapHeight; regionY += regionSize) {
                for (int regionZ = 0; regionZ < mapDepth; regionZ += regionSize) {

                    int regionWidth = Math.min(regionSize, mapWidth - regionX);
                    int regionHeight = Math.min(regionSize, mapHeight - regionY);
                    int regionDepth = Math.min(regionSize, mapDepth - regionZ);

                    LightmapBaker.BakedLightingResult result = baker.bakeRegionLighting(
                        map, staticLights, regionX, regionY, regionZ,
                        regionWidth, regionHeight, regionDepth
                    );

                    if (result.hasLightmap()) {
                        lightmaps.put(result.regionId, result.lightmapTexture);
                        bakedRegions++;
                        Log.info("EnhancedGameMapRenderer", "Stored lightmap for region: " + result.regionId + 
                            ", texture=" + result.lightmapTexture + ", total lightmaps=" + lightmaps.size);
                    } else {
                        Log.warn("EnhancedGameMapRenderer", "No lightmap texture for region: " + result.regionId);
                    }
                }
            }
        }

        Log.info("EnhancedGameMapRenderer", "Lightmap baking completed: " + bakedRegions + " regions baked");
    }

    /**
     * Render the map with enhanced lighting
     */
    public void render(PerspectiveCamera camera, Environment environment) {
        render(camera, environment, camera.position);
    }

    public void render(PerspectiveCamera camera, Environment environment, Vector3 viewerPosition) {
        // Get or generate map instances
        Array<ModelInstance> instances = getMapInstances();

        if (instances.size == 0) {
            Log.warn("EnhancedGameMapRenderer", "No geometry instances available - cannot render");
            return;
        }

        // Extract ambient light from environment
        Vector3 ambientLight = extractAmbientLight(environment);

        Log.debug("EnhancedGameMapRenderer", "Rendering " + instances.size + " instances with enhanced lighting");

        // Try to render with enhanced lighting system
        Log.info("EnhancedGameMapRenderer", "Passing " + lightmaps.size + " lightmaps to renderer: " + lightmaps.keys().toArray());
        try {
            lightingRenderer.render(instances, camera, viewerPosition, lightmaps, ambientLight);
            Log.debug("EnhancedGameMapRenderer", "Enhanced lighting render successful");
        } catch (Exception e) {
            Log.error("EnhancedGameMapRenderer", "Enhanced lighting render failed: " + e.getMessage());
            Log.error("EnhancedGameMapRenderer", "ERROR DETAILS: " + e.getClass().getSimpleName() + " - " + e.toString());
            e.printStackTrace();

            // Create simple fallback rendering using LibGDX ModelBatch
            Log.warn("EnhancedGameMapRenderer", "FALLING BACK to ModelBatch rendering - baked lights will not work!");
            try {
                com.badlogic.gdx.graphics.g3d.ModelBatch modelBatch = new com.badlogic.gdx.graphics.g3d.ModelBatch();
                modelBatch.begin(camera);
                for (ModelInstance instance : instances) {
                    modelBatch.render(instance, environment);
                }
                modelBatch.end();
                modelBatch.dispose();
                Log.info("EnhancedGameMapRenderer", "Used fallback ModelBatch rendering successfully");
            } catch (Exception fallbackError) {
                Log.error("EnhancedGameMapRenderer", "Even fallback rendering failed: " + fallbackError.getMessage());
            }
        }

        // Render debug light visualizations
        renderDebugLights(camera, environment);
    }

    private Vector3 extractAmbientLight(Environment environment) {
        // Try to extract from environment, fallback to default
        try {
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute ambient =
                environment.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.class,
                              com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight);
            if (ambient != null) {
                return new Vector3(ambient.color.r, ambient.color.g, ambient.color.b);
            }
        } catch (Exception e) {
            Log.debug("EnhancedGameMapRenderer", "Could not extract ambient light from environment");
        }

        return new Vector3(0.02f, 0.02f, 0.03f); // Default low ambient
    }

    private Array<ModelInstance> getMapInstances() {
        // Return the map instances we maintain ourselves
        if (mapInstances.size == 0 && currentMap != null) {
            // Generate geometry if we haven't already
            Log.debug("EnhancedGameMapRenderer", "Generating map geometry for enhanced renderer");
            generateMapGeometry();
        }

        return mapInstances;
    }

    private void generateMapGeometry() {
        if (currentMap == null) {
            Log.warn("EnhancedGameMapRenderer", "Cannot generate geometry - no map loaded");
            return;
        }

        Log.info("EnhancedGameMapRenderer", "Generating geometry for map...");
        mapInstances.clear();

        // Create the geometry using the same system that the old GameMapRenderer used
        // We'll integrate directly with your existing map model building system
        try {
            // Use your existing map geometry builders
            curly.octo.map.rendering.BFSVisibleMapModelBuilder builder =
                new curly.octo.map.rendering.BFSVisibleMapModelBuilder(currentMap);

            // Create materials like the old renderer did
            com.badlogic.gdx.graphics.g3d.Material stoneMaterial = createMaterial(com.badlogic.gdx.graphics.Color.GRAY, 0.2f, 8f);
            com.badlogic.gdx.graphics.g3d.Material dirtMaterial = createMaterial(com.badlogic.gdx.graphics.Color.BROWN, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material grassMaterial = createMaterial(com.badlogic.gdx.graphics.Color.GREEN, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material pinkWall = createMaterial(com.badlogic.gdx.graphics.Color.PINK, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material spawnMaterial = createMaterial(com.badlogic.gdx.graphics.Color.LIME, 0.1f, 4f);

            // Build main opaque geometry
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            modelBuilder.begin();
            builder.buildGeometry(modelBuilder, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall, null);
            com.badlogic.gdx.graphics.g3d.Model model = modelBuilder.end();

            // Create model instance  
            com.badlogic.gdx.graphics.g3d.ModelInstance instance = new com.badlogic.gdx.graphics.g3d.ModelInstance(model);
            
            // TEMPORARY FIX: Set userData to the first available lightmap
            // This allows the single geometry to use at least one lightmap
            if (lightmaps.size > 0) {
                String firstRegionId = lightmaps.keys().toArray().iterator().next();
                instance.userData = firstRegionId;
                Log.info("EnhancedGameMapRenderer", "Set geometry userData to first available lightmap: " + firstRegionId);
            }
            
            mapInstances.add(instance);

            Log.info("EnhancedGameMapRenderer", "Generated geometry: " + mapInstances.size + " instances");
            Log.info("EnhancedGameMapRenderer", "Geometry stats: " + builder.getTotalFacesBuilt() + " faces, " +
                builder.getTotalTilesProcessed() + " tiles processed");

        } catch (Exception e) {
            Log.error("EnhancedGameMapRenderer", "Failed to generate map geometry: " + e.getMessage());
            e.printStackTrace();

            // Create fallback test cube so we can at least see something
            createFallbackGeometry();
        }
    }

    private com.badlogic.gdx.graphics.g3d.Material createMaterial(com.badlogic.gdx.graphics.Color diffuse, float specular, float shininess) {
        com.badlogic.gdx.graphics.g3d.Material material = new com.badlogic.gdx.graphics.g3d.Material();
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse, diffuse));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular, specular, specular, specular, 1f));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess, shininess));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.IntAttribute(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace, GL20.GL_BACK));
        return material;
    }

    private void createFallbackGeometry() {
        Log.info("EnhancedGameMapRenderer", "Creating fallback test geometry");
        mapInstances.clear();

        // Create a simple test cube to verify the rendering pipeline works
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        modelBuilder.begin();

        // Create a test material
        com.badlogic.gdx.graphics.g3d.Material testMaterial = new com.badlogic.gdx.graphics.g3d.Material();
        testMaterial.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.RED));

        // Create a test cube
        modelBuilder.node().id = "test_cube";
        modelBuilder.part("test_cube", GL20.GL_TRIANGLES,
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position |
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, testMaterial)
            .box(4f, 4f, 4f);

        com.badlogic.gdx.graphics.g3d.Model testModel = modelBuilder.end();
        com.badlogic.gdx.graphics.g3d.ModelInstance testInstance = new com.badlogic.gdx.graphics.g3d.ModelInstance(testModel);
        testInstance.transform.setToTranslation(25, 5, 25); // Position it at map center

        mapInstances.add(testInstance);
        Log.info("EnhancedGameMapRenderer", "Created fallback test cube at (25,5,25)");
    }

    // Configuration methods
    public void setLightingQuality(float shadowBias, float lightmapBlend, float lightmapIntensity) {
        lightingRenderer.setShadowBias(shadowBias);
        lightingRenderer.setLightmapBlendFactor(lightmapBlend);
        lightingRenderer.setLightmapIntensity(lightmapIntensity);

        Log.info("EnhancedGameMapRenderer", String.format(
            "Lighting quality set: shadowBias=%.3f, lightmapBlend=%.2f, lightmapIntensity=%.2f",
            shadowBias, lightmapBlend, lightmapIntensity));
    }

    // Add lights dynamically (for effects, player lights, etc.)
    public void addDynamicLight(String id, Vector3 position, Color color, float intensity,
                               LightType type, boolean castsShadows) {
        Light light = new Light.Builder()
            .setId(id)
            .setType(type)
            .setPosition(position)
            .setColor(color)
            .setIntensity(intensity)
            .setCastsShadows(castsShadows)
            .setEnabled(true)
            .build();

        lightingRenderer.getLightManager().addLight(light);
        Log.info("EnhancedGameMapRenderer", "Added dynamic light: " + id);
    }

    public void removeDynamicLight(String id) {
        lightingRenderer.getLightManager().removeLight(id);
        Log.info("EnhancedGameMapRenderer", "Removed dynamic light: " + id);
    }

    public boolean updateDynamicLightPosition(String id, Vector3 newPosition) {
        boolean updated = lightingRenderer.getLightManager().updateLightPosition(id, newPosition);
        if (updated) {
            Log.debug("EnhancedGameMapRenderer", "Updated light position: " + id);
        }
        return updated;
    }

    // Debug rendering methods
    private void initializeDebugRendering() {
        debugModelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();

        // Create a simple wireframe sphere for light visualization
        debugModelBuilder.begin();
        com.badlogic.gdx.graphics.g3d.Material debugMaterial = new com.badlogic.gdx.graphics.g3d.Material();
        debugMaterial.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW));
        debugMaterial.set(new com.badlogic.gdx.graphics.g3d.attributes.IntAttribute(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace, GL20.GL_NONE));

        debugModelBuilder.node().id = "debug_light_sphere";
        debugModelBuilder.part("debug_light_sphere", GL20.GL_LINES,
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, debugMaterial)
            .sphere(2f, 2f, 2f, 8, 8); // Small wireframe sphere

        debugLightSphere = debugModelBuilder.end();

        Log.info("EnhancedGameMapRenderer", "Debug light visualization initialized");
    }

    private void updateDebugLightVisualization() {
        debugLightInstances.clear();

        if (!debugRenderBakedLights) {
            return;
        }

        Array<Light> bakedLights = lightingRenderer.getLightManager().getBakedLights();
        Log.info("EnhancedGameMapRenderer", "Creating debug visualization for " + bakedLights.size + " baked lights");

        for (Light light : bakedLights) {
            com.badlogic.gdx.graphics.g3d.ModelInstance debugInstance = new com.badlogic.gdx.graphics.g3d.ModelInstance(debugLightSphere);

            // Position the debug sphere at the light's location
            debugInstance.transform.setToTranslation(light.getPosition());

            // Scale based on light intensity (larger = brighter)
            float scale = Math.max(0.5f, Math.min(3.0f, light.getIntensity() * 0.3f));
            debugInstance.transform.scl(scale);

            // Set color based on light color
            com.badlogic.gdx.graphics.Color lightColor = light.getColor();
            for (com.badlogic.gdx.graphics.g3d.Material mat : debugInstance.materials) {
                mat.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(lightColor));
            }

            debugLightInstances.add(debugInstance);
        }

        Log.info("EnhancedGameMapRenderer", "Created " + debugLightInstances.size + " debug light instances");
    }

    public void setDebugRenderBakedLights(boolean enable) {
        this.debugRenderBakedLights = enable;
        updateDebugLightVisualization();
        Log.info("EnhancedGameMapRenderer", "Debug baked light rendering: " + (enable ? "ENABLED" : "DISABLED"));
    }

    public boolean isDebugRenderBakedLights() {
        return debugRenderBakedLights;
    }

    private void renderDebugLights(PerspectiveCamera camera, Environment environment) {
        if (!debugRenderBakedLights || debugLightInstances.size == 0) {
            return;
        }

        try {
            com.badlogic.gdx.graphics.g3d.ModelBatch debugBatch = new com.badlogic.gdx.graphics.g3d.ModelBatch();
            debugBatch.begin(camera);

            for (com.badlogic.gdx.graphics.g3d.ModelInstance debugInstance : debugLightInstances) {
                debugBatch.render(debugInstance, environment);
            }

            debugBatch.end();
            debugBatch.dispose();

            Log.debug("EnhancedGameMapRenderer", "Rendered " + debugLightInstances.size + " debug light instances");
        } catch (Exception e) {
            Log.error("EnhancedGameMapRenderer", "Failed to render debug lights: " + e.getMessage());
        }
    }

    // Statistics and debugging
    public void printLightingStatistics() {
        Log.info("EnhancedGameMapRenderer", "=== Lighting Statistics ===");
        Log.info("EnhancedGameMapRenderer", "Total lights: " + totalLights);
        Log.info("EnhancedGameMapRenderer", "Baked regions: " + bakedRegions);
        Log.info("EnhancedGameMapRenderer", "Last bake time: " + lastBakeTime + "ms");

        lightingRenderer.getLightManager().printStatistics();
        lightingRenderer.printPerformanceStats();
    }

    public int getTotalLights() { return totalLights; }
    public int getBakedRegions() { return bakedRegions; }
    public long getLastBakeTime() { return lastBakeTime; }

    // Methods for compatibility with existing debug UI
    public int getLastTotalLights() {
        return lightingRenderer.getLightManager().getTotalLights();
    }

    public int getLastShadowLights() {
        return lightingRenderer.getLastFrameShadowedLights();
    }

    // Access to subsystems
    public LightManager getLightManager() {
        return lightingRenderer.getLightManager();
    }

    public LightmapBaker getLightmapBaker() {
        return lightingRenderer.getLightmapBaker();
    }

    @Override
    public void dispose() {
        if (disposed) return;

        // Dispose lighting system
        if (lightingRenderer != null) {
            lightingRenderer.dispose();
        }

        // Dispose lightmaps
        for (Texture lightmap : lightmaps.values()) {
            if (lightmap != null) {
                lightmap.dispose();
            }
        }
        lightmaps.clear();

        // Dispose debug rendering resources
        if (debugLightSphere != null) {
            debugLightSphere.dispose();
        }
        debugLightInstances.clear();

        disposed = true;
        Log.info("EnhancedGameMapRenderer", "EnhancedGameMapRenderer disposed");
    }
}
