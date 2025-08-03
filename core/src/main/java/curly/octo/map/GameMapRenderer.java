package curly.octo.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.MapHint;
import curly.octo.map.rendering.AllTilesMapModelBuilder;
import curly.octo.map.rendering.BFSVisibleMapModelBuilder;
import curly.octo.map.rendering.MapModelBuilder;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    private final CubeShadowMapRenderer cubeShadowMapRenderer;
    private final Array<ModelInstance> instances;
    private final Array<PointLight> mapLights; // Lights generated from LightHints
    private Model model;
    private Model waterModel; // Separate model for transparent water
    private Model lavaModel;  // Separate model for transparent lava
    private Model fogModel;   // Separate model for transparent fog
    private ModelInstance waterInstance;
    private ModelInstance lavaInstance;
    private ModelInstance fogInstance;
    private boolean disposed = false;

    // Custom shaders for fill types
    private ShaderProgram waterShader;
    private ShaderProgram lavaShader;
    private ShaderProgram fogShader;

    // Configurable number of shadow-casting lights (performance vs quality tradeoff)
    private int maxShadowCastingLights = 8; // Reduced from 8 to 2 for better performance

    // Track light counts for debug UI
    private int lastTotalLights = 0;
    private int lastShadowLights = 0;

    // Rendering strategy
    public enum RenderingStrategy {
        ALL_TILES,      // Render all occupied tiles (original approach)
        BFS_VISIBLE     // Render only tiles with faces visible to players
    }
    private RenderingStrategy renderingStrategy = RenderingStrategy.BFS_VISIBLE;

    // Track rendering stats for debug UI
    private long lastFacesBuilt = 0;
    private long lastTilesProcessed = 0;

    public GameMapRenderer() {
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowCastingLights);
        instances = new Array<>();
        mapLights = new Array<>();

        // Load custom shaders for fill types
        loadCustomShaders();

        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (" + maxShadowCastingLights + " shadow-casting lights, 512x512 per face)");
        Log.info("GameMapRenderer", "Loaded custom shaders: water, lava, fog");
    }

    /**
     * Set the maximum number of shadow-casting lights (performance tuning)
     * @param maxLights Number of closest lights that will cast shadows (1-4 recommended)
     */
    public void setMaxShadowCastingLights(int maxLights) {
        if (maxLights < 1 || maxLights > 8) {
            Log.warn("GameMapRenderer", "Max shadow-casting lights should be between 1-8, got: " + maxLights);
            return;
        }
        this.maxShadowCastingLights = maxLights;
        Log.info("GameMapRenderer", "Set max shadow-casting lights to: " + maxLights);
    }
    
    private void loadCustomShaders() {
        // Load water shader
        String waterVertexShader = Gdx.files.internal("shaders/water.vertex.glsl").readString();
        String waterFragmentShader = Gdx.files.internal("shaders/water.fragment.glsl").readString();
        waterShader = new ShaderProgram(waterVertexShader, waterFragmentShader);
        if (!waterShader.isCompiled()) {
            Log.error("GameMapRenderer", "Water shader compilation failed: " + waterShader.getLog());
            throw new RuntimeException("Water shader compilation failed");
        }
        
        // Load lava shader
        String lavaVertexShader = Gdx.files.internal("shaders/lava.vertex.glsl").readString();
        String lavaFragmentShader = Gdx.files.internal("shaders/lava.fragment.glsl").readString();
        lavaShader = new ShaderProgram(lavaVertexShader, lavaFragmentShader);
        if (!lavaShader.isCompiled()) {
            Log.error("GameMapRenderer", "Lava shader compilation failed: " + lavaShader.getLog());
            throw new RuntimeException("Lava shader compilation failed");
        }
        
        // Load fog shader
        String fogVertexShader = Gdx.files.internal("shaders/fog.vertex.glsl").readString();
        String fogFragmentShader = Gdx.files.internal("shaders/fog.fragment.glsl").readString();
        fogShader = new ShaderProgram(fogVertexShader, fogFragmentShader);
        if (!fogShader.isCompiled()) {
            Log.error("GameMapRenderer", "Fog shader compilation failed: " + fogShader.getLog());
            throw new RuntimeException("Fog shader compilation failed");
        }
    }

    public void render(PerspectiveCamera camera, Environment environment) {
        // Get all point lights in the environment
        PointLightsAttribute pointLights = environment.get(PointLightsAttribute.class, PointLightsAttribute.Type);

        // Always update light counts for debug UI (even if no lights)
        if (pointLights == null || pointLights.lights.size == 0) {
            lastTotalLights = 0;
            lastShadowLights = 0;
            Log.warn("GameMapRenderer", "No lights found, skipping shadow rendering");
            return;
        }

        // Generate shadow maps for the N most significant lights (brightest overall)
        Array<PointLight> significantLights = getMostSignificantLights(pointLights, maxShadowCastingLights);

        // Update light counts for debug UI
        lastTotalLights = pointLights.lights.size;
        lastShadowLights = significantLights.size;

        if (significantLights.size > 0) {
            // Reset light index for new frame
            cubeShadowMapRenderer.resetLightIndex();

            // Generate cube shadow maps for each significant light
            for (PointLight light : significantLights) {
                cubeShadowMapRenderer.generateCubeShadowMap(instances, light);
            }

            // Render opaque geometry with shadows
            Vector3 ambientLight = getAmbientLight(environment);
            cubeShadowMapRenderer.renderWithMultipleCubeShadows(instances, camera, significantLights, pointLights.lights, ambientLight);

            // Render transparent surfaces with custom shaders
            if (waterInstance != null) {
                renderCustomShaderSurface(waterInstance, waterShader, camera, pointLights.lights, ambientLight);
            }
            if (lavaInstance != null) {
                renderCustomShaderSurface(lavaInstance, lavaShader, camera, null, null); // Lava doesn't need lights
            }
            if (fogInstance != null) {
                renderCustomShaderSurface(fogInstance, fogShader, camera, pointLights.lights, ambientLight);
            }
        } else {
            Log.warn("GameMapRenderer", "No lights found for shadow casting");
        }
    }

    private Array<PointLight> getMostSignificantLights(PointLightsAttribute pointLights, int maxLights) {
        Array<PointLight> result = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return result;
        }

        // Create array of lights with significance scores (intensity-based)
        Array<LightSignificance> lightScores = new Array<>();
        for (PointLight light : pointLights.lights) {
            // Score based on light intensity (brighter lights are more significant)
            float significance = light.intensity;
            lightScores.add(new LightSignificance(light, significance));
        }

        // Sort by significance (highest first)
        lightScores.sort((a, b) -> Float.compare(b.significance, a.significance));

        // Take the N most significant lights
        int numLights = Math.min(maxLights, lightScores.size);
        for (int i = 0; i < numLights; i++) {
            result.add(lightScores.get(i).light);
        }

        Log.debug("GameMapRenderer", "Selected " + result.size + " most significant lights for shadow casting");

        return result;
    }

    // Helper class for sorting lights by significance
    private static class LightSignificance {
        final PointLight light;
        final float significance;

        LightSignificance(PointLight light, float significance) {
            this.light = light;
            this.significance = significance;
        }
    }

    private Vector3 getAmbientLight(Environment environment) {
        // Extract ambient light color from environment
        ColorAttribute ambient = environment.get(ColorAttribute.class, ColorAttribute.AmbientLight);
        if (ambient != null) {
            return new Vector3(ambient.color.r, ambient.color.g, ambient.color.b);
        }
        return new Vector3(0.02f, 0.02f, 0.03f); // Default very low ambient
    }

    private void renderCustomShaderSurface(ModelInstance surfaceInstance, ShaderProgram shader, PerspectiveCamera camera, Array<PointLight> lights, Vector3 ambientLight) {
        if (surfaceInstance == null || shader == null) return;

        // Set up blending for transparency
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false); // Don't write to depth buffer for transparent objects

        // Begin shader
        shader.begin();
        
        // Set common uniforms
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformMatrix("u_worldTrans", surfaceInstance.transform);
        // Use a more reasonable time scale for animations (avoid precision issues)
        float time = (System.currentTimeMillis() % 60000) / 1000.0f; // Reset every minute to avoid precision loss
        shader.setUniformf("u_time", time);
        
        // Set lighting uniforms (if lights provided and shader supports them)
        if (lights != null && ambientLight != null && shader.hasUniform("u_numLights")) {
            shader.setUniformi("u_numLights", Math.min(lights.size, 8));
            
            if (shader.hasUniform("u_ambientLight")) {
                shader.setUniformf("u_ambientLight", ambientLight);
            }
            
            for (int i = 0; i < Math.min(lights.size, 8); i++) {
                PointLight light = lights.get(i);
                if (shader.hasUniform("u_lightPositions[" + i + "]")) {
                    shader.setUniformf("u_lightPositions[" + i + "]", light.position);
                }
                if (shader.hasUniform("u_lightColors[" + i + "]")) {
                    shader.setUniformf("u_lightColors[" + i + "]", light.color.r, light.color.g, light.color.b);
                }
                if (shader.hasUniform("u_lightIntensities[" + i + "]")) {
                    shader.setUniformf("u_lightIntensities[" + i + "]", light.intensity);
                }
            }
        } else if (shader.hasUniform("u_numLights")) {
            shader.setUniformi("u_numLights", 0);
        }

        // Render the surface
        for (Node node : surfaceInstance.nodes) {
            for (NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    Mesh mesh = nodePart.meshPart.mesh;
                    mesh.render(shader, nodePart.meshPart.primitiveType,
                               nodePart.meshPart.offset, nodePart.meshPart.size);
                }
            }
        }
        
        shader.end();

        // Restore depth mask
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }


    public void updateMap(GameMap map) {
        Log.info("GameMapRenderer", "Starting optimized map update for " + map.getWidth() + "x" + map.getHeight() + "x" + map.getDepth() + " map");
        long startTime = System.currentTimeMillis();

        // Clear previous model and lights
        dispose();
        clearMapLights();

        // Extract lights from map tiles with LightHints
        extractLightsFromMap(map);

        // Create materials
        Material stoneMaterial = createMaterial(Color.GRAY, 0.2f, 8f);
        Material dirtMaterial = createMaterial(Color.BROWN, 0.1f, 4f);
        Material grassMaterial = createMaterial(Color.GREEN, 0.1f, 4f);
        Material pinkWall = createMaterial(Color.PINK, 0.1f, 4f);
        Material spawnMaterial = createMaterial(Color.LIME, 0.1f, 4f);
        Material waterMaterial = createWaterMaterial();
        Material lavaMaterial = createLavaMaterial();
        Material fogMaterial = createFogMaterial();

        // Create the appropriate model builder based on strategy
        MapModelBuilder builder;
        switch (renderingStrategy) {
            case BFS_VISIBLE:
                builder = new BFSVisibleMapModelBuilder(map);
                break;
            case ALL_TILES:
            default:
                builder = new AllTilesMapModelBuilder(map);
                break;
        }

        // Build main opaque geometry (no water surfaces)
        ModelBuilder opaqueBuilder = new ModelBuilder();
        opaqueBuilder.begin();
        builder.buildGeometry(opaqueBuilder, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall, null); // null waterMaterial = no water in main model
        model = opaqueBuilder.end();

        // Build separate surface models for transparency with simple materials
        ModelBuilder waterBuilder = new ModelBuilder();
        waterBuilder.begin();
        builder.buildWaterGeometry(waterBuilder, createSimpleMaterial());
        waterModel = waterBuilder.end();

        ModelBuilder lavaBuilder = new ModelBuilder();
        lavaBuilder.begin();
        builder.buildLavaGeometry(lavaBuilder, createSimpleMaterial());
        lavaModel = lavaBuilder.end();

        ModelBuilder fogBuilder = new ModelBuilder();
        fogBuilder.begin();
        builder.buildFogGeometry(fogBuilder, createSimpleMaterial());
        fogModel = fogBuilder.end();

        // Update stats for debug UI
        lastFacesBuilt = builder.getTotalFacesBuilt();
        lastTilesProcessed = builder.getTotalTilesProcessed();

        // Create model instances for rendering
        instances.clear();
        instances.add(new ModelInstance(model));

        if (waterModel != null) {
            waterInstance = new ModelInstance(waterModel);
        }
        if (lavaModel != null) {
            lavaInstance = new ModelInstance(lavaModel);
        }
        if (fogModel != null) {
            fogInstance = new ModelInstance(fogModel);
        }

        long endTime = System.currentTimeMillis();
        Log.info("GameMapRenderer", "Completed map update in " + (endTime - startTime) + "ms using " + builder.getStrategyDescription());
    }

    private Material createMaterial(Color diffuse, float specular, float shininess) {
        Material material = new Material();
        material.set(new ColorAttribute(ColorAttribute.Diffuse, diffuse));
        material.set(new ColorAttribute(ColorAttribute.Specular, specular, specular, specular, 1f));
        material.set(new FloatAttribute(FloatAttribute.Shininess, shininess));
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_BACK));
        return material;
    }

    private Material createWaterMaterial() {
        Material material = new Material();
        // Simple material for custom shader - color handled in shader
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));              // No culling for transparency
        // Enable blending for transparency with proper depth handling
        BlendingAttribute blending = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.4f);
        blending.blended = true; // Explicitly enable blending
        material.set(blending);
        return material;
    }

    private Material createLavaMaterial() {
        Material material = new Material();
        // Simple material for custom shader - color handled in shader
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));              // No culling for transparency
        // Enable blending for transparency with proper depth handling
        BlendingAttribute blending = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.9f);
        blending.blended = true; // Explicitly enable blending
        material.set(blending);
        return material;
    }

    private Material createFogMaterial() {
        Material material = new Material();
        // Simple material for custom shader - color handled in shader
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));              // No culling for transparency
        // Enable blending for transparency with proper depth handling
        BlendingAttribute blending = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f);
        blending.blended = true; // Explicitly enable blending
        material.set(blending);
        return material;
    }

    private Material createSimpleMaterial() {
        Material material = new Material();
        // Very simple material with no special attributes - custom shaders will handle everything
        material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));
        return material;
    }


    private void extractLightsFromMap(GameMap map) {
        int lightCount = 0;

        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int z = 0; z < map.getDepth(); z++) {
                    MapTile tile = map.getTile(x, y, z);

                    // Check if this tile has any LightHints
                    for (MapHint hint : tile.getHints()) {
                        if (hint instanceof LightHint) {
                            LightHint lightHint = (LightHint) hint;

                            // Create a PointLight from the LightHint
                            PointLight mapLight = new PointLight();
                            mapLight.set(
                                lightHint.color_r, lightHint.color_g, lightHint.color_b,  // Color
                                tile.x + MapTile.TILE_SIZE / 2f,                        // X position (center of tile)
                                tile.y + MapTile.TILE_SIZE / 2f + 2f,                   // Y position (slightly above tile)
                                tile.z + MapTile.TILE_SIZE / 2f,                        // Z position (center of tile)
                                lightHint.intensity                                      // Intensity/range
                            );

                            mapLights.add(mapLight);
                            lightCount++;

                            Log.info("GameMapRenderer", "Created light from LightHint at (" + tile.x + "," + tile.y + "," + tile.z +
                                ") with intensity " + lightHint.intensity + " and color (" + lightHint.color_r + "," + lightHint.color_g + "," + lightHint.color_b + ")");
                        }
                    }
                }
            }
        }

        Log.info("GameMapRenderer", "Extracted " + lightCount + " lights from map LightHints");
    }

    private void clearMapLights() {
        mapLights.clear();
    }

    /**
     * Get the lights generated from map LightHints
     * @return Array of PointLights created from map tiles
     */
    public Array<PointLight> getMapLights() {
        return mapLights;
    }

    /**
     * Add the map lights to an environment for rendering
     * @param environment The environment to add lights to
     */
    public void addMapLightsToEnvironment(Environment environment) {
        for (PointLight light : mapLights) {
            environment.add(light);
        }
        if (mapLights.size > 0) {
            Log.info("GameMapRenderer", "Added " + mapLights.size + " map lights to environment");
        }
    }

    /**
     * Get the total number of lights rendered in the last frame
     */
    public int getLastTotalLights() {
        return lastTotalLights;
    }

    /**
     * Get the number of shadow-casting lights rendered in the last frame
     */
    public int getLastShadowLights() {
        return lastShadowLights;
    }

    /**
     * Set the rendering strategy.
     * @param strategy The strategy to use for building visible geometry
     */
    public void setRenderingStrategy(RenderingStrategy strategy) {
        this.renderingStrategy = strategy;
    }

    /**
     * Get the current rendering strategy.
     * @return The current strategy
     */
    public RenderingStrategy getRenderingStrategy() {
        return renderingStrategy;
    }

    /**
     * Get the number of faces built in the last map update.
     * @return Face count
     */
    public long getLastFacesBuilt() {
        return lastFacesBuilt;
    }

    /**
     * Get the number of tiles processed in the last map update.
     * @return Tile count
     */
    public long getLastTilesProcessed() {
        return lastTilesProcessed;
    }

    @Override
    public void dispose() {
        if (disposed) {
            Log.info("GameMapRenderer", "Already disposed, skipping");
            return;
        }

        if (model != null) {
            try {
                model.dispose();
                Log.info("GameMapRenderer", "Model disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing model: " + e.getMessage());
            }
            model = null;
            instances.clear();
        }

        if (waterModel != null) {
            try {
                waterModel.dispose();
                Log.info("GameMapRenderer", "Water model disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing water model: " + e.getMessage());
            }
            waterModel = null;
            waterInstance = null;
        }

        if (lavaModel != null) {
            try {
                lavaModel.dispose();
                Log.info("GameMapRenderer", "Lava model disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing lava model: " + e.getMessage());
            }
            lavaModel = null;
            lavaInstance = null;
        }

        if (fogModel != null) {
            try {
                fogModel.dispose();
                Log.info("GameMapRenderer", "Fog model disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing fog model: " + e.getMessage());
            }
            fogModel = null;
            fogInstance = null;
        }

        disposed = true;
    }

    public void disposeAll() {
        dispose();
        if (cubeShadowMapRenderer != null && !disposed) {
            try {
                cubeShadowMapRenderer.dispose();
                Log.info("GameMapRenderer", "Cube shadow map renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing cube shadow map renderer: " + e.getMessage());
            }
        }

        // Dispose of custom shaders
        if (waterShader != null) {
            waterShader.dispose();
            waterShader = null;
        }
        if (lavaShader != null) {
            lavaShader.dispose();
            lavaShader = null;
        }
        if (fogShader != null) {
            fogShader.dispose();
            fogShader = null;
        }
        Log.info("GameMapRenderer", "Custom shaders disposed");
    }
}
