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
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.game.GameObjectManager;
import curly.octo.map.ChunkDebugger;
import curly.octo.map.enums.MapTileFillType;
import curly.octo.map.hints.LightHint;
import curly.octo.map.hints.MapHint;
import curly.octo.map.rendering.ChunkedMapModelBuilder;
import curly.octo.rendering.CubeShadowMapRenderer;
import curly.octo.rendering.BloomRenderer;
import curly.octo.rendering.PostProcessingRenderer;
import lights.BaseLight;

/**
 * Handles rendering of the VoxelMap in 3D space with shadow mapping.
 */
public class GameMapRenderer implements Disposable {
    private final CubeShadowMapRenderer cubeShadowMapRenderer;
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
        cubeShadowMapRenderer = new CubeShadowMapRenderer(CubeShadowMapRenderer.QUALITY_HIGH, maxShadowCastingLights);
        bloomRenderer = new BloomRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        postProcessingRenderer = new PostProcessingRenderer(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        instances = new Array<>();

        Log.info("GameMapRenderer", "Initialized with HIGH quality cube shadow mapping (" + maxShadowCastingLights + " shadow-casting lights, 512x512 per face)");
        Log.info("GameMapRenderer", "Surface shaders are now handled within individual chunk models");
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

            // Get the appropriate map instances based on rendering strategy
            Array<ModelInstance> mapInstances;
            if (chunkModelBuilder != null) {
                // For chunked strategy, get only chunks near camera
                float renderDistance = 200f; // Render chunks within 200 units of camera
                mapInstances = chunkModelBuilder.getChunksNearPosition(camera.position, renderDistance);
                Log.debug("GameMapRenderer", String.format("Rendering %d chunks near camera position (%.1f, %.1f, %.1f)",
                    mapInstances.size, camera.position.x, camera.position.y, camera.position.z));
            } else {
                // For traditional strategies, use all instances
                mapInstances = instances;
            }

            // Combine map instances with additional dynamic objects for shadow generation
            Array<ModelInstance> allInstances = mapInstances;
            if (additionalInstances != null && additionalInstances.size > 0) {
                allInstances = new Array<>(mapInstances);
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

            // Transparent surfaces are now handled within individual chunk models
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

    // Surface rendering is now handled within individual chunk models


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
        // Surface materials are handled within individual chunks

        // Create chunk-based model builder
        ChunkDebugger.quickDebug(map, "Before ChunkedMapModelBuilder");
        ChunkedMapModelBuilder chunkedBuilder = new ChunkedMapModelBuilder(map);

        // Build individual chunk models
        chunkedBuilder.buildGeometry(null, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall, null);

        // Store the chunk builder for rendering
        this.chunkModelBuilder = chunkedBuilder;

        // No single model for chunk-based rendering - we use individual chunk instances
        model = null;
        Log.info("GameMapRenderer", "Built " + chunkedBuilder.getAllChunkInstances().size + " individual chunk models");

        // Surface materials are now built into individual chunks

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
            if (tile != null) {
                light.setPosition(new Vector3(tile.x + MapTile.TILE_SIZE / 2f, tile.y + MapTile.TILE_SIZE / 2f, tile.z + MapTile.TILE_SIZE / 2f));
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

        // Custom shaders are now handled within individual chunks
    }
}
