package curly.octo.client.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.common.Constants;
import curly.octo.client.GameObjectManager;
import curly.octo.common.map.ChunkDebugger;
import curly.octo.common.map.ChunkManager;
import curly.octo.common.map.GameMap;
import curly.octo.common.map.MapTile;
import curly.octo.common.map.enums.MapTileFillType;
import curly.octo.common.map.hints.LightHint;
import curly.octo.common.map.hints.MapHint;
import curly.octo.common.map.rendering.ChunkedMapModelBuilder;
import curly.octo.client.rendering.core.RenderingContext;
import curly.octo.client.rendering.shadows.ShadowMapGenerator;
import curly.octo.client.rendering.scene.SceneRenderer;
import curly.octo.client.rendering.scene.LightingManager;
import curly.octo.client.rendering.debug.DebugRenderer;
import curly.octo.common.lights.BaseLight;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    // Refactored rendering components
    private final ShadowMapGenerator shadowMapGenerator;
    private final SceneRenderer sceneRenderer;
    private final LightingManager lightingManager;
    private final DebugRenderer debugRenderer;

    // Post-processing (unchanged - already well-structured)
    private final BloomRenderer bloomRenderer;
    private final PostProcessingRenderer postProcessingRenderer;

    private final Array<ModelInstance> instances;
    private Model model;
    // Surface models are now handled within individual chunks
    private boolean disposed = false;

    private GameObjectManager objectManager;
    private Environment environment;

    // Configurable number of shadow-casting lights (performance vs quality tradeoff)
    private int maxShadowCastingLights = 8; // Reduced from 8 to 2 for better performance

    // Track light counts for debug UI
    private int lastTotalLights = 0;
    private int lastShadowLights = 0;

    // Using chunk-based rendering strategy only

    // Track rendering stats for debug UI
    private long lastFacesBuilt = 0;
    private long lastTilesProcessed = 0;

    // Chunk-based rendering
    private ChunkedMapModelBuilder chunkModelBuilder = null;

    public GameMapRenderer(GameObjectManager objectManager) {
        this.objectManager = objectManager;

        // Initialize rendering components
        shadowMapGenerator = new ShadowMapGenerator(ShadowMapGenerator.QUALITY_HIGH, maxShadowCastingLights);
        sceneRenderer = new SceneRenderer(maxShadowCastingLights, shadowMapGenerator.getFarPlane());
        lightingManager = new LightingManager();
        debugRenderer = new DebugRenderer();

        // Link debug renderer to scene renderer
        sceneRenderer.setDebugRenderer(debugRenderer);

        // Initialize post-processing
        bloomRenderer = new BloomRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        postProcessingRenderer = new PostProcessingRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        instances = new Array<>();

        Log.info("GameMapRenderer", "Initialized with refactored rendering architecture:");
        Log.info("GameMapRenderer", "  - ShadowMapGenerator: HIGH quality (" + maxShadowCastingLights + " shadow lights, 1024x1024 per face)");
        Log.info("GameMapRenderer", "  - SceneRenderer: Two-pass rendering with shadow mapping");
        Log.info("GameMapRenderer", "  - LightingManager: Dynamic light selection and sorting");
        Log.info("GameMapRenderer", "  - DebugRenderer: Water wireframe visualization");
        Log.info("GameMapRenderer", "  - BloomRenderer: Lava glow post-processing");
        Log.info("GameMapRenderer", "Surface shaders are now handled within individual chunk models");
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

        // Use LightingManager to select significant lights
        Array<PointLight> shadowLights = lightingManager.getMostSignificantLights(
            pointLights, maxShadowCastingLights, camera.position);

        // Update light counts for debug UI
        lastTotalLights = pointLights.lights.size;
        lastShadowLights = shadowLights.size;

        if (shadowLights.size == 0) {
            Log.warn("GameMapRenderer", "No lights found for shadow casting");
            return;
        }

        // Get instances to render
        Array<ModelInstance> mapInstances = getMapInstances(camera);
        Array<ModelInstance> allInstances = combineInstances(mapInstances, additionalInstances);

        // Generate shadow maps using ShadowMapGenerator
        shadowMapGenerator.generateAllShadowMaps(allInstances, shadowLights);

        // CRITICAL: Restore the target framebuffer after shadow map generation
        if (targetFrameBuffer != null) {
            targetFrameBuffer.begin();
        } else {
            // IMPORTANT: If rendering to screen (null framebuffer), must explicitly unbind
            // any framebuffers that shadow map generation bound
            FrameBuffer.unbind();
        }

        // Create rendering context
        RenderingContext context =
            new RenderingContext(camera, environment, mapInstances);
        context.setAdditionalInstances(additionalInstances);
        context.setTargetFrameBuffer(targetFrameBuffer);

        // Render scene with SceneRenderer
        Vector3 ambientLight = getAmbientLight(environment);
        float deltaTime = Gdx.graphics.getDeltaTime();
        sceneRenderer.renderWithShadows(
            context,
            shadowMapGenerator.getShadowFrameBuffers(),
            shadowLights,
            pointLights.lights,
            ambientLight,
            deltaTime
        );

        // Render debug visualizations
        debugRenderer.renderWaterWireframes(camera);
    }

    /**
     * Gets map instances based on rendering strategy.
     */
    private Array<ModelInstance> getMapInstances(PerspectiveCamera camera) {
        if (chunkModelBuilder != null) {
            // For chunked strategy, get only chunks near camera
            Array<ModelInstance> chunks = chunkModelBuilder.getChunksNearPosition(camera.position, Constants.CHUNK_RENDER_DISTANCE);
            Log.debug("GameMapRenderer", String.format("Rendering %d chunks near camera position (%.1f, %.1f, %.1f)",
                chunks.size, camera.position.x, camera.position.y, camera.position.z));
            return chunks;
        } else {
            // For traditional strategies, use all instances
            return instances;
        }
    }

    /**
     * Combines map instances with additional dynamic objects.
     */
    private Array<ModelInstance> combineInstances(Array<ModelInstance> mapInstances,
                                                  Array<ModelInstance> additionalInstances) {
        if (additionalInstances != null && additionalInstances.size > 0) {
            Array<ModelInstance> combined = new Array<>(mapInstances);
            combined.addAll(additionalInstances);
            return combined;
        }
        return mapInstances;
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

    // Light selection is now handled by LightingManager

    private Vector3 getAmbientLight(Environment environment) {
        // Extract ambient light color from environment
        ColorAttribute ambient = environment.get(ColorAttribute.class, ColorAttribute.AmbientLight);
        Vector3 ambientVec;
        if (ambient != null) {
            ambientVec = new Vector3(ambient.color.r, ambient.color.g, ambient.color.b);
            Log.debug("GameMapRenderer", "Environment has ambient light: (" + ambientVec.x + "," + ambientVec.y + "," + ambientVec.z + ")");
        } else {
            ambientVec = new Vector3(0.02f, 0.02f, 0.03f); // Default very low ambient
            Log.debug("GameMapRenderer", "No ambient light in environment, using default");
        }

        // Ensure minimum ambient light to prevent completely black scenes
        float minAmbient = 0.02f;
        if (ambientVec.x < minAmbient) ambientVec.x = minAmbient;
        if (ambientVec.y < minAmbient) ambientVec.y = minAmbient;
        if (ambientVec.z < minAmbient) ambientVec.z = minAmbient;

        return ambientVec;
    }

    // Surface rendering is now handled within individual chunk models


    public void updateMap(GameMap map, Environment environment) {
        long startTime = System.currentTimeMillis();

        Log.info("GameMapRenderer", "=== UPDATING MAP ===");
        Log.info("GameMapRenderer", "Map has " + map.getAllTiles().size() + " tiles");
        Log.info("GameMapRenderer", "Map hash code: " + map.hashCode());

        // Log some tile positions to verify map is actually different
        java.util.List<MapTile> tiles = map.getAllTiles();
        if (tiles.size() > 0) {
            Log.info("GameMapRenderer", "First 3 tile positions:");
            for (int i = 0; i < Math.min(3, tiles.size()); i++) {
                MapTile tile = tiles.get(i);
                Log.info("GameMapRenderer", "  Tile " + i + ": (" + tile.x + ", " + tile.y + ", " + tile.z + ") " + tile.fillType);
            }
        }

        // Clear previous model and lights
        dispose();

        // Extract lights from map tiles with LightHints
        extractLightsFromMap(map, environment);
        Log.info("GameMapRenderer", "Extracted lights from new map");

        // Create materials
        Material stoneMaterial = createMaterial(Color.GRAY, 0.2f, 8f);
        Material dirtMaterial = createMaterial(Color.BROWN, 0.1f, 4f);
        Material grassMaterial = createMaterial(Color.GREEN, 0.1f, 4f);
        Material pinkWall = createMaterial(Color.PINK, 0.1f, 4f);
        Material spawnMaterial = createMaterial(Color.LIME, 0.1f, 4f);

        // Create surface materials for water, lava, and fog
        Material waterMaterial = createWaterMaterial();
        Material lavaMaterial = createLavaMaterial();

        // Create chunk-based model builder
        ChunkDebugger.quickDebug(map, "Before ChunkedMapModelBuilder");
        ChunkedMapModelBuilder chunkedBuilder = new ChunkedMapModelBuilder(map);

        // Build individual chunk models with SOLID geometry only (no water to avoid transparency contamination)
        chunkedBuilder.buildGeometry(null, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall, null);

        // Build water surfaces as a SEPARATE model to avoid transparency issues
        ModelBuilder waterModelBuilder = new ModelBuilder();
        chunkedBuilder.buildWaterGeometry(waterModelBuilder, waterMaterial);

        // Store the chunk builder for rendering
        this.chunkModelBuilder = chunkedBuilder;

        // No single model for chunk-based rendering - we use individual chunk instances
        model = null;
        Log.info("GameMapRenderer", "Built " + chunkedBuilder.getAllChunkInstances().size + " individual chunk models (including water)");

        // Surface materials are now built separately

        // Update stats for debug UI
        lastFacesBuilt = chunkedBuilder.getTotalFacesBuilt();
        lastTilesProcessed = chunkedBuilder.getTotalTilesProcessed();

        // Create model instances for rendering
        instances.clear();
        if (chunkModelBuilder != null) {
            // Add all chunk instances to render queue
            instances.addAll(chunkModelBuilder.getAllChunkInstances());
            Log.info("GameMapRenderer", "Added " + instances.size + " chunk instances to render queue");
        }

        // Surface instances are now handled within individual chunks

        long endTime = System.currentTimeMillis();
        Log.info("GameMapRenderer", "Completed map update in " + (endTime - startTime) + "ms using " + chunkedBuilder.getStrategyDescription());
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
        // Set water color (blue/cyan) with alpha for transparency
        Color waterColor = new Color(0.2f, 0.4f, 0.8f, 0.4f); // Blue with 40% opacity
        material.set(new ColorAttribute(ColorAttribute.Diffuse, waterColor));
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
            if (tile != null) {
                light.setPosition(new Vector3(tile.x + Constants.MAP_TILE_SIZE / 2f, tile.y + Constants.MAP_TILE_SIZE / 2f, tile.z + Constants.MAP_TILE_SIZE / 2f));
            } else {
                Log.error("extractLightsFromMap", "We registered a light for a tile that does not exist somehow");
            }
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

        // Dispose chunk model builder
        if (chunkModelBuilder != null) {
            try {
                chunkModelBuilder.dispose();
                Log.info("GameMapRenderer", "Chunk model builder disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing chunk model builder: " + e.getMessage());
            }
            chunkModelBuilder = null;
        }

        // Surface models are now handled within individual chunks


        disposed = true;
    }

    public void disposeAll() {
        dispose();

        // Dispose rendering components
        if (shadowMapGenerator != null && !disposed) {
            try {
                shadowMapGenerator.dispose();
                Log.info("GameMapRenderer", "Shadow map generator disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing shadow map generator: " + e.getMessage());
            }
        }

        if (sceneRenderer != null && !disposed) {
            try {
                sceneRenderer.dispose();
                Log.info("GameMapRenderer", "Scene renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing scene renderer: " + e.getMessage());
            }
        }

        if (debugRenderer != null && !disposed) {
            try {
                debugRenderer.dispose();
                Log.info("GameMapRenderer", "Debug renderer disposed");
            } catch (Exception e) {
                Log.error("GameMapRenderer", "Error disposing debug renderer: " + e.getMessage());
            }
        }

        // Dispose post-processing components
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

        // Custom shaders are now handled within individual chunks
    }
}
