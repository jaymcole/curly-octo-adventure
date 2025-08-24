package curly.octo.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.GameObjectManager;
import curly.octo.map.ChunkManager;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.MapHint;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.rendering.AllTilesMapModelBuilder;
import curly.octo.map.rendering.BFSVisibleMapModelBuilder;
import curly.octo.map.rendering.ChunkedMapModelBuilder;
import curly.octo.map.rendering.MapModelBuilder;
import curly.octo.rendering.CubeShadowMapRenderer;
import curly.octo.rendering.BloomRenderer;
import curly.octo.rendering.PostProcessingRenderer;
import jdk.javadoc.internal.doclint.Env;
import lights.BaseLight;
import lights.LightType;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    private final CubeShadowMapRenderer cubeShadowMapRenderer;
    private final BloomRenderer bloomRenderer;
    private final PostProcessingRenderer postProcessingRenderer;
    private final Array<ModelInstance> instances;
    private Model model;
    private Model waterModel; // Separate model for transparent water
    private Model lavaModel;  // Separate model for transparent lava
    private Model fogModel;   // Separate model for transparent fog
    private ModelInstance waterInstance;
    private ModelInstance lavaInstance;
    private ModelInstance fogInstance;
    private boolean disposed = false;

    private GameObjectManager objectManager;
    private Environment environment;

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
        BFS_VISIBLE,    // Render only tiles with faces visible to players
        CHUNKED         // Render tiles organized into chunks with BFS prioritization
    }
    private RenderingStrategy renderingStrategy = RenderingStrategy.CHUNKED;

    // Track rendering stats for debug UI
    private long lastFacesBuilt = 0;
    private long lastTilesProcessed = 0;

    public GameMapRenderer(GameObjectManager objectManager) {
        this.objectManager = objectManager;
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowCastingLights);
        bloomRenderer = new BloomRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        postProcessingRenderer = new PostProcessingRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        instances = new Array<>();

        // Load custom shaders for fill types
        loadCustomShaders();

        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (" + maxShadowCastingLights + " shadow-casting lights, 512x512 per face)");
        Log.info("GameMapRenderer", "Loaded custom shaders: water, lava, fog");
        Log.info("GameMapRenderer", "Initialized bloom post-processing for realistic lava glow");
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
        render(camera, environment, null);
    }

    public void render(PerspectiveCamera camera, Environment environment, FrameBuffer targetFrameBuffer) {
        render(camera, environment, targetFrameBuffer, null);
    }

    /**
     * Renders the map with additional dynamic objects (like players) included in shadow casting
     */
    public void render(PerspectiveCamera camera, Environment environment, FrameBuffer targetFrameBuffer, Array<ModelInstance> additionalInstances) {

        // Get all point lights in the environment
        PointLightsAttribute pointLights = environment.get(PointLightsAttribute.class, PointLightsAttribute.Type);

        // Always update light counts for debug UI (even if no lights)
        if (pointLights == null) {
            lastTotalLights = 0;
            lastShadowLights = 0;
            return;
        }

        if (pointLights.lights.size == 0) {
            lastTotalLights = 0;
            lastShadowLights = 0;
            return;
        }

        // Generate shadow maps for the N most significant lights (closest to player)
        Array<PointLight> significantLights = getMostSignificantLights(pointLights, maxShadowCastingLights, camera.position);

        // Update light counts for debug UI
        lastTotalLights = pointLights.lights.size;
        lastShadowLights = significantLights.size;


        if (significantLights.size > 0) {

            // Reset light index for new frame
            cubeShadowMapRenderer.resetLightIndex();

            // Combine map instances with additional dynamic objects for shadow generation
            Array<ModelInstance> allInstances = instances;
            if (additionalInstances != null && additionalInstances.size > 0) {
                allInstances = new Array<>(instances);
                allInstances.addAll(additionalInstances);
            }

            // Generate cube shadow maps for each significant light
            for (PointLight light : significantLights) {
                cubeShadowMapRenderer.generateCubeShadowMap(allInstances, light);
            }

            // CRITICAL: Restore the target framebuffer after shadow map generation
            // Shadow map generation changes framebuffer binding to screen (0)
            if (targetFrameBuffer != null) {
                targetFrameBuffer.begin();
            }

            // Render opaque geometry with shadows
            Vector3 ambientLight = getAmbientLight(environment);
            cubeShadowMapRenderer.renderWithMultipleCubeShadows(allInstances, camera, significantLights, pointLights.lights, ambientLight);

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

    /**
     * Begin bloom rendering - should be called before rendering the 3D scene.
     */
    public void beginBloomRender() {
        if (bloomRenderer != null) {
            bloomRenderer.beginSceneRender();
        }
    }

    /**
     * End bloom rendering and composite to screen - should be called after all 3D rendering is done.
     */
    public void endBloomRender() {
        if (bloomRenderer != null) {
            bloomRenderer.endSceneRenderAndApplyBloom();
        }
    }

    /**
     * Get the bloom framebuffer for external systems that need to restore it.
     */
    public FrameBuffer getBloomFrameBuffer() {
        return (bloomRenderer != null) ? bloomRenderer.getSceneFrameBuffer() : null;
    }

    /**
     * Begin post-processing rendering - should be called before rendering the 3D scene.
     */
    public void beginPostProcessingRender() {
        if (postProcessingRenderer != null) {
            postProcessingRenderer.beginSceneRender();
        }
    }

    /**
     * End post-processing rendering and apply effects - should be called after all 3D rendering is done.
     */
    public void endPostProcessingRender() {
        if (postProcessingRenderer != null) {
            postProcessingRenderer.endSceneRenderAndApplyEffects();
        }
    }

    /**
     * Set the current post-processing effect based on the player's tile location.
     */
    public void setPostProcessingEffect(MapTileFillType effect) {
        if (postProcessingRenderer != null) {
            postProcessingRenderer.setCurrentEffect(effect);
        }
    }

    /**
     * Get the post-processing framebuffer for external systems that need to restore it.
     */
    public FrameBuffer getPostProcessingFrameBuffer() {
        return (postProcessingRenderer != null) ? postProcessingRenderer.getSceneFrameBuffer() : null;
    }

    /**
     * Apply post-processing effects to the current screen content (after bloom).
     */
    public void applyPostProcessingToScreen() {
        if (postProcessingRenderer != null) {
            postProcessingRenderer.captureScreenAndApplyEffects();
        }
    }

    /**
     * Resize the renderer when the window size changes.
     */
    public void resize(int width, int height) {
        if (bloomRenderer != null) {
            bloomRenderer.resize(width, height);
        }
        if (postProcessingRenderer != null) {
            postProcessingRenderer.resize(width, height);
        }
        // Note: CubeShadowMapRenderer uses fixed-size shadow maps, no resize needed
        Log.info("GameMapRenderer", "Resized to " + width + "x" + height);
    }

    private Array<PointLight> getMostSignificantLights(PointLightsAttribute pointLights, int maxLights, Vector3 playerPosition) {
        Array<PointLight> result = new Array<>();

        if (pointLights == null || pointLights.lights.size == 0) {
            return result;
        }

        // Create array of lights with significance scores (distance-based)
        Array<LightSignificance> lightScores = new Array<>();
        for (PointLight light : pointLights.lights) {
            // Score based on distance from player (closer lights are more significant)
            // Use negative distance so closer lights have higher significance when sorted
            float distance = playerPosition.dst(light.position);
            float significance = -distance; // Negative so closer = higher significance
            lightScores.add(new LightSignificance(light, significance, distance, light.intensity));
        }

        // Sort by significance (highest first = closest first)
        // In case of tie distance, use intensity as tiebreaker
        lightScores.sort((a, b) -> {
            int distanceComparison = Float.compare(b.significance, a.significance);
            if (distanceComparison == 0) {
                // Tie in distance, use intensity as tiebreaker (brighter first)
                return Float.compare(b.intensity, a.intensity);
            }
            return distanceComparison;
        });

        // Take the N most significant lights
        int numLights = Math.min(maxLights, lightScores.size);
        for (int i = 0; i < numLights; i++) {
            result.add(lightScores.get(i).light);
        }

        return result;
    }

//    private Array<PointLight> getMostSignificantLights(PointLightsAttribute pointLights, int maxLights) {
//        Array<PointLight> result = new Array<>();
//
//        if (pointLights == null || pointLights.lights.size == 0) {
//            return result;
//        }
//
//        // Create array of lights with significance scores (intensity-based)
//        Array<LightSignificance> lightScores = new Array<>();
//        for (PointLight light : pointLights.lights) {
//            // Score based on light intensity (brighter lights are more significant)
//            float significance = light.intensity;
//            lightScores.add(new LightSignificance(light, significance));
//        }
//
//        // Sort by significance (highest first)
//        lightScores.sort((a, b) -> Float.compare(b.significance, a.significance));
//
//        // Take the N most significant lights
//        int numLights = Math.min(maxLights, lightScores.size);
//        for (int i = 0; i < numLights; i++) {
//            result.add(lightScores.get(i).light);
//        }
//
//        Log.info("GameMapRenderer", "Selected " + result.size + " most significant lights for shadow casting out of " + lightScores.size + " total lights");
//
//        return result;
//    }

    // Helper class for sorting lights by significance
    private static class LightSignificance {
        final PointLight light;
        final float significance;
        final float distance;
        final float intensity;

        LightSignificance(PointLight light, float significance, float distance, float intensity) {
            this.light = light;
            this.significance = significance;
            this.distance = distance;
            this.intensity = intensity;
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


    public void updateMap(GameMap map, Environment environment) {
        long startTime = System.currentTimeMillis();

        // Clear previous model and lights
        dispose();

        // Extract lights from map tiles with LightHints
        extractLightsFromMap(map, environment);

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
            case CHUNKED:
                builder = new ChunkedMapModelBuilder(map);
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
        // Use additive blending for bright glow effect (shader-based bloom)
        BlendingAttribute blending = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.95f);
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


    private void extractLightsFromMap(GameMap map, Environment environment) {
        int lightCount = 0;
        for (MapHint hint : map.getAllHintsOfType(LightHint.class)) {
            LightHint lightHint = (LightHint) hint;
            BaseLight light = new BaseLight(environment, objectManager, lightHint.entityId, lightHint.color_r, lightHint.color_g, lightHint.color_b, lightHint.intensity, null, lightHint.flicker);
            MapTile tile = map.getTile(hint.tileLookupKey);
            light.setPosition(new Vector3(tile.x + MapTile.TILE_SIZE / 2f, tile.y + MapTile.TILE_SIZE / 2f, tile.z + MapTile.TILE_SIZE / 2f));
            objectManager.add(light);
            lightCount++;
        }
        Log.info("GameMapRenderer", "Extracted " + lightCount + " lights from map LightHints");
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
    
    /**
     * Get the ChunkManager if the current rendering strategy supports it.
     * @return ChunkManager instance, or null if not using chunked strategy
     */
    public ChunkManager getChunkManager() {
        // This would require storing a reference to the last builder used
        // For now, return null - external systems should create their own ChunkManager if needed
        return null;
    }

    /**
     * Configure bloom effect parameters.
     * @param threshold Brightness threshold for bloom extraction (0.5-2.0 recommended)
     * @param intensity Bloom intensity multiplier (0.5-2.0 recommended)
     */
    public void setBloomParameters(float threshold, float intensity) {
        if (bloomRenderer != null) {
            bloomRenderer.setBloomParameters(threshold, intensity);
            Log.info("GameMapRenderer", "Set bloom parameters: threshold=" + threshold + ", intensity=" + intensity);
        }
    }

    /**
     * Get current bloom parameters.
     */
    public float getBloomThreshold() {
        return bloomRenderer != null ? bloomRenderer.getBloomThreshold() : 1.0f;
    }

    public float getBloomIntensity() {
        return bloomRenderer != null ? bloomRenderer.getBloomIntensity() : 0.8f;
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

        if (bloomRenderer != null && !disposed) {
            try {
                bloomRenderer.dispose();
                Log.info("GameMapRenderer", "Bloom renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing bloom renderer: " + e.getMessage());
            }
        }

        if (postProcessingRenderer != null && !disposed) {
            try {
                postProcessingRenderer.dispose();
                Log.info("GameMapRenderer", "Post-processing renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing post-processing renderer: " + e.getMessage());
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
