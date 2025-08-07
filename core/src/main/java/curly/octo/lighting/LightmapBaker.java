package curly.octo.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import curly.octo.map.GameMap;
import curly.octo.map.MapTile;

/**
 * Bakes static lighting into textures for improved runtime performance.
 * Suitable for procedurally generated environments.
 */
public class LightmapBaker implements Disposable {
    
    // Lightmap configuration
    private final int lightmapSize;
    private final int shadowMapSize;
    
    // Rendering resources
    private FrameBuffer lightmapFrameBuffer;
    private FrameBuffer shadowFrameBuffer;
    private ShaderProgram bakingShader;
    private ShaderProgram shadowBakingShader;
    private Camera orthographicCamera;
    
    // Baked results
    private final ObjectMap<String, Texture> bakedLightmaps;
    private final ObjectMap<String, Texture> bakedShadowmaps;
    
    private boolean disposed = false;
    
    public LightmapBaker() {
        this(512, 1024); // Default sizes
    }
    
    public LightmapBaker(int lightmapSize, int shadowMapSize) {
        this.lightmapSize = lightmapSize;
        this.shadowMapSize = shadowMapSize;
        this.bakedLightmaps = new ObjectMap<>();
        this.bakedShadowmaps = new ObjectMap<>();
        
        initializeResources();
        Log.info("LightmapBaker", "Initialized with " + lightmapSize + "x" + lightmapSize + 
            " lightmaps and " + shadowMapSize + "x" + shadowMapSize + " shadow maps");
    }
    
    private void initializeResources() {
        // Create framebuffers
        lightmapFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, lightmapSize, lightmapSize, false);
        shadowFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, shadowMapSize, shadowMapSize, true);
        
        // Load baking shaders
        loadBakingShaders();
        
        // Set up orthographic camera for lightmap projection
        orthographicCamera = new OrthographicCamera(lightmapSize, lightmapSize);
        orthographicCamera.near = 0.1f;
        orthographicCamera.far = 100.0f;
    }
    
    private void loadBakingShaders() {
        // Load lightmap baking shader from files
        String lightmapVertexShader = Gdx.files.internal("shaders/lightmap_baking.vertex.glsl").readString();
        String lightmapFragmentShader = Gdx.files.internal("shaders/lightmap_baking.fragment.glsl").readString();
        
        bakingShader = new ShaderProgram(lightmapVertexShader, lightmapFragmentShader);
        if (!bakingShader.isCompiled()) {
            Log.error("LightmapBaker", "Lightmap baking shader failed: " + bakingShader.getLog());
            throw new RuntimeException("Lightmap baking shader compilation failed");
        }
        
        // Load shadow baking shader from files
        String shadowVertexShader = Gdx.files.internal("shaders/shadow_baking.vertex.glsl").readString();
        String shadowFragmentShader = Gdx.files.internal("shaders/shadow_baking.fragment.glsl").readString();
        
        shadowBakingShader = new ShaderProgram(shadowVertexShader, shadowFragmentShader);
        if (!shadowBakingShader.isCompiled()) {
            Log.error("LightmapBaker", "Shadow baking shader failed: " + shadowBakingShader.getLog());
            throw new RuntimeException("Shadow baking shader compilation failed");
        }
        
        Log.info("LightmapBaker", "Baking shaders loaded successfully from files");
    }
    
    /**
     * Bake lighting for a map region with static lights
     */
    public BakedLightingResult bakeRegionLighting(GameMap map, Array<Light> staticLights, 
                                                 int regionX, int regionY, int regionZ, 
                                                 int regionWidth, int regionHeight, int regionDepth) {
        
        Log.info("LightmapBaker", "Baking lighting for region (" + regionX + "," + regionY + "," + regionZ + 
            ") size " + regionWidth + "x" + regionHeight + "x" + regionDepth);
        
        long startTime = System.currentTimeMillis();
        
        // Generate region identifier
        String regionId = regionX + "_" + regionY + "_" + regionZ;
        
        // Filter lights that affect this region
        Array<Light> relevantLights = getRelevantLights(staticLights, regionX, regionY, regionZ, 
            regionWidth, regionHeight, regionDepth);
        
        if (relevantLights.size == 0) {
            Log.info("LightmapBaker", "No lights affect region " + regionId + ", skipping bake");
            return new BakedLightingResult(regionId, null, null);
        }
        
        Log.info("LightmapBaker", "Region " + regionId + " will be baked with " + relevantLights.size + " lights");
        
        // Create geometry for the region
        Array<ModelInstance> regionGeometry = createRegionGeometry(map, regionX, regionY, regionZ, 
            regionWidth, regionHeight, regionDepth);
        
        if (regionGeometry.size == 0) {
            Log.info("LightmapBaker", "No geometry in region " + regionId + ", skipping bake");
            return new BakedLightingResult(regionId, null, null);
        }
        
        Log.info("LightmapBaker", "Region " + regionId + " has " + regionGeometry.size + " geometry instances, proceeding with bake");
        
        // Set up camera to view the region
        setupRegionCamera(regionX, regionY, regionZ, regionWidth, regionHeight, regionDepth);
        
        // Bake lightmap
        Texture lightmapTexture = bakeLightmap(regionGeometry, relevantLights, regionId);
        Log.info("LightmapBaker", "Lightmap texture result for " + regionId + ": " + 
            (lightmapTexture != null ? "SUCCESS" : "FAILED"));
        
        // Bake shadow map for the primary light (if any cast shadows)
        Texture shadowmapTexture = null;
        Light primaryShadowLight = getPrimaryShadowLight(relevantLights);
        if (primaryShadowLight != null) {
            shadowmapTexture = bakeShadowmap(regionGeometry, primaryShadowLight, regionId);
            Log.info("LightmapBaker", "Shadowmap texture result for " + regionId + ": " + 
                (shadowmapTexture != null ? "SUCCESS" : "FAILED"));
        }
        
        // Store results
        if (lightmapTexture != null) {
            bakedLightmaps.put(regionId, lightmapTexture);
        }
        if (shadowmapTexture != null) {
            bakedShadowmaps.put(regionId, shadowmapTexture);
        }
        
        long endTime = System.currentTimeMillis();
        Log.info("LightmapBaker", "Baked region " + regionId + " in " + (endTime - startTime) + 
            "ms with " + relevantLights.size + " lights");
        
        return new BakedLightingResult(regionId, lightmapTexture, shadowmapTexture);
    }
    
    private Array<Light> getRelevantLights(Array<Light> allLights, int regionX, int regionY, int regionZ,
                                          int regionWidth, int regionHeight, int regionDepth) {
        Array<Light> relevant = new Array<>();
        
        // Convert tile coordinates to world coordinates for proper distance calculations
        Vector3 regionCenter = new Vector3(
            (regionX + regionWidth / 2f) * MapTile.TILE_SIZE,
            (regionY + regionHeight / 2f) * MapTile.TILE_SIZE,
            (regionZ + regionDepth / 2f) * MapTile.TILE_SIZE
        );
        
        Log.info("LightmapBaker", "Region " + regionX + "," + regionY + "," + regionZ + 
            " → world center(" + regionCenter.x + "," + regionCenter.y + "," + regionCenter.z + ")");
        
        float regionRadius = (float) Math.sqrt(
            regionWidth * regionWidth + regionHeight * regionHeight + regionDepth * regionDepth
        ) * MapTile.TILE_SIZE / 2f;
        
        for (Light light : allLights) {
            if (light.getType() != LightType.BAKED_STATIC || !light.isEnabled()) continue;
            
            // For debugging and dark environments: include ALL baked lights regardless of distance
            relevant.add(light);
            float distance = light.getPosition().dst(regionCenter);
            Log.info("LightmapBaker", "Light " + light.getId() + " affects region (distance: " + 
                String.format("%.1f", distance) + ") - NO DISTANCE LIMIT");
        }
        
        // Sort by baking priority (higher priority first)
        relevant.sort((a, b) -> Integer.compare(b.getBakingPriority(), a.getBakingPriority()));
        
        return relevant;
    }
    
    private Array<ModelInstance> createRegionGeometry(GameMap map, int regionX, int regionY, int regionZ,
                                                     int regionWidth, int regionHeight, int regionDepth) {
        Array<ModelInstance> geometry = new Array<>();
        
        Log.info("LightmapBaker", "Building geometry for region (" + regionX + "," + regionY + "," + regionZ + 
            ") size " + regionWidth + "x" + regionHeight + "x" + regionDepth);
        
        try {
            // Use the same geometry builder as the main renderer
            curly.octo.map.rendering.BFSVisibleMapModelBuilder builder = 
                new curly.octo.map.rendering.BFSVisibleMapModelBuilder(map);
            
            // Create materials (same as EnhancedGameMapRenderer)
            com.badlogic.gdx.graphics.g3d.Material stoneMaterial = createMaterial(com.badlogic.gdx.graphics.Color.GRAY, 0.2f, 8f);
            com.badlogic.gdx.graphics.g3d.Material dirtMaterial = createMaterial(com.badlogic.gdx.graphics.Color.BROWN, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material grassMaterial = createMaterial(com.badlogic.gdx.graphics.Color.GREEN, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material pinkWall = createMaterial(com.badlogic.gdx.graphics.Color.PINK, 0.1f, 4f);
            com.badlogic.gdx.graphics.g3d.Material spawnMaterial = createMaterial(com.badlogic.gdx.graphics.Color.LIME, 0.1f, 4f);
            
            // Create a ModelBuilder for this region
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            modelBuilder.begin();
            
            // Build geometry for the specific region
            // Note: BFSVisibleMapModelBuilder builds the entire map, but we only need this region
            // For now, build the full map and we'll optimize later if needed
            builder.buildGeometry(modelBuilder, stoneMaterial, dirtMaterial, grassMaterial, spawnMaterial, pinkWall, null);
            
            com.badlogic.gdx.graphics.g3d.Model regionModel = modelBuilder.end();
            
            if (regionModel != null) {
                com.badlogic.gdx.graphics.g3d.ModelInstance instance = new com.badlogic.gdx.graphics.g3d.ModelInstance(regionModel);
                
                // CRITICAL: Set userData to region ID so renderer can find the lightmap
                String regionId = regionX + "_" + regionY + "_" + regionZ;
                instance.userData = regionId;
                
                geometry.add(instance);
                
                Log.info("LightmapBaker", "Created geometry for region " + regionId + 
                    ": " + geometry.size + " instances, " + builder.getTotalFacesBuilt() + " faces, userData='" + regionId + "'");
            } else {
                Log.warn("LightmapBaker", "Failed to create model for region " + regionX + "_" + regionY + "_" + regionZ);
            }
            
        } catch (Exception e) {
            Log.error("LightmapBaker", "Error creating geometry for region " + regionX + "_" + regionY + "_" + regionZ + ": " + e.getMessage());
            e.printStackTrace();
            
            // Create fallback test geometry so we can still test lightmap baking
            createFallbackRegionGeometry(geometry, regionX, regionY, regionZ, regionWidth, regionHeight, regionDepth);
        }
        
        return geometry;
    }
    
    private com.badlogic.gdx.graphics.g3d.Material createMaterial(com.badlogic.gdx.graphics.Color diffuse, float specular, float shininess) {
        com.badlogic.gdx.graphics.g3d.Material material = new com.badlogic.gdx.graphics.g3d.Material();
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse, diffuse));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular, specular, specular, specular, 1f));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess, shininess));
        material.set(new com.badlogic.gdx.graphics.g3d.attributes.IntAttribute(com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace, GL20.GL_BACK));
        return material;
    }
    
    private void createFallbackRegionGeometry(Array<ModelInstance> geometry, int regionX, int regionY, int regionZ, 
                                            int regionWidth, int regionHeight, int regionDepth) {
        Log.info("LightmapBaker", "Creating fallback test geometry for region " + regionX + "_" + regionY + "_" + regionZ);
        
        try {
            // Create a simple test cube for the region center
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            modelBuilder.begin();
            
            com.badlogic.gdx.graphics.g3d.Material testMaterial = new com.badlogic.gdx.graphics.g3d.Material();
            testMaterial.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GRAY));
            
            modelBuilder.node().id = "test_region_" + regionX + "_" + regionY + "_" + regionZ;
            modelBuilder.part("test_cube", GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position |
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, testMaterial)
                .box(regionWidth * MapTile.TILE_SIZE, regionHeight * MapTile.TILE_SIZE, regionDepth * MapTile.TILE_SIZE);
            
            com.badlogic.gdx.graphics.g3d.Model testModel = modelBuilder.end();
            com.badlogic.gdx.graphics.g3d.ModelInstance testInstance = new com.badlogic.gdx.graphics.g3d.ModelInstance(testModel);
            
            // CRITICAL: Set userData to region ID so renderer can find the lightmap
            String regionId = regionX + "_" + regionY + "_" + regionZ;
            testInstance.userData = regionId;
            
            // Position at region center in world coordinates
            float worldX = (regionX + regionWidth / 2f) * MapTile.TILE_SIZE;
            float worldY = (regionY + regionHeight / 2f) * MapTile.TILE_SIZE;
            float worldZ = (regionZ + regionDepth / 2f) * MapTile.TILE_SIZE;
            testInstance.transform.setToTranslation(worldX, worldY, worldZ);
            
            geometry.add(testInstance);
            Log.info("LightmapBaker", "Created fallback geometry: 1 test cube at (" + worldX + "," + worldY + "," + worldZ + ")");
            
        } catch (Exception fallbackError) {
            Log.error("LightmapBaker", "Even fallback geometry creation failed: " + fallbackError.getMessage());
        }
    }
    
    private void setupRegionCamera(int regionX, int regionY, int regionZ, 
                                  int regionWidth, int regionHeight, int regionDepth) {
        // Position camera to view the entire region
        // Convert tile coordinates to world coordinates for proper distance calculations
        Vector3 regionCenter = new Vector3(
            (regionX + regionWidth / 2f) * MapTile.TILE_SIZE,
            (regionY + regionHeight / 2f) * MapTile.TILE_SIZE,
            (regionZ + regionDepth / 2f) * MapTile.TILE_SIZE
        );
        
        // Position camera above the region looking down
        orthographicCamera.position.set(regionCenter.x, regionCenter.y + 50f, regionCenter.z);
        orthographicCamera.lookAt(regionCenter);
        orthographicCamera.up.set(0, 0, 1);
        orthographicCamera.update();
    }
    
    private Texture bakeLightmap(Array<ModelInstance> geometry, Array<Light> lights, String regionId) {
        lightmapFrameBuffer.begin();
        
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.03f, 1.0f); // Dark ambient
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        
        // Enable additive blending to accumulate light from multiple sources
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        
        bakingShader.bind();
        bakingShader.setUniformMatrix("u_projViewTrans", orthographicCamera.combined);
        bakingShader.setUniformf("u_ambientLight", 0.02f, 0.02f, 0.03f);
        
        // Render contribution from each light
        for (Light light : lights) {
            bakingShader.setUniformf("u_lightPosition", light.getPosition());
            bakingShader.setUniformf("u_lightColor", light.getColor().r, light.getColor().g, light.getColor().b);
            bakingShader.setUniformf("u_lightIntensity", light.getIntensity());
            
            // Render geometry with this light
            for (ModelInstance instance : geometry) {
                bakingShader.setUniformMatrix("u_worldTrans", instance.transform);
                renderInstance(instance, bakingShader);
            }
        }
        
        Gdx.gl.glDisable(GL20.GL_BLEND);
        lightmapFrameBuffer.end();
        
        Log.debug("LightmapBaker", "Baked lightmap for region " + regionId + " with " + lights.size + " lights");
        return lightmapFrameBuffer.getColorBufferTexture();
    }
    
    private Texture bakeShadowmap(Array<ModelInstance> geometry, Light light, String regionId) {
        shadowFrameBuffer.begin();
        
        Gdx.gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // Clear to max depth
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        
        shadowBakingShader.bind();
        
        // Set up light view matrix
        Matrix4 lightViewProj = calculateLightViewProjection(light);
        shadowBakingShader.setUniformMatrix("u_lightViewProj", lightViewProj);
        
        // Render shadow casters
        for (ModelInstance instance : geometry) {
            shadowBakingShader.setUniformMatrix("u_worldTrans", instance.transform);
            renderInstance(instance, shadowBakingShader);
        }
        
        shadowFrameBuffer.end();
        
        Log.debug("LightmapBaker", "Baked shadowmap for region " + regionId);
        return shadowFrameBuffer.getColorBufferTexture();
    }
    
    private Light getPrimaryShadowLight(Array<Light> lights) {
        for (Light light : lights) {
            if (light.castsShadows()) {
                return light;
            }
        }
        return null;
    }
    
    private Matrix4 calculateLightViewProjection(Light light) {
        // Create a view-projection matrix from the light's perspective
        PerspectiveCamera lightCamera = new PerspectiveCamera(90f, shadowMapSize, shadowMapSize);
        lightCamera.position.set(light.getPosition());
        lightCamera.lookAt(light.getPosition().x, light.getPosition().y - 10f, light.getPosition().z);
        lightCamera.up.set(0, 0, 1);
        lightCamera.near = 0.1f;
        lightCamera.far = 200.0f; // Use generous far plane instead of effective range
        lightCamera.update();
        
        return new Matrix4(lightCamera.combined);
    }
    
    private void renderInstance(ModelInstance instance, ShaderProgram shader) {
        // Simplified rendering - in practice you'd use your existing rendering pipeline
        // This is a placeholder for integration with your model rendering system
    }
    
    /**
     * Get a baked lightmap texture by region ID
     */
    public Texture getBakedLightmap(String regionId) {
        return bakedLightmaps.get(regionId);
    }
    
    /**
     * Get a baked shadowmap texture by region ID
     */
    public Texture getBakedShadowmap(String regionId) {
        return bakedShadowmaps.get(regionId);
    }
    
    /**
     * Clear all baked textures for a region
     */
    public void clearRegion(String regionId) {
        Texture lightmap = bakedLightmaps.remove(regionId);
        if (lightmap != null && lightmap != lightmapFrameBuffer.getColorBufferTexture()) {
            lightmap.dispose();
        }
        
        Texture shadowmap = bakedShadowmaps.remove(regionId);
        if (shadowmap != null && shadowmap != shadowFrameBuffer.getColorBufferTexture()) {
            shadowmap.dispose();
        }
        
        Log.debug("LightmapBaker", "Cleared baked textures for region " + regionId);
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        
        // Dispose framebuffers
        if (lightmapFrameBuffer != null) {
            lightmapFrameBuffer.dispose();
        }
        if (shadowFrameBuffer != null) {
            shadowFrameBuffer.dispose();
        }
        
        // Dispose shaders
        if (bakingShader != null) {
            bakingShader.dispose();
        }
        if (shadowBakingShader != null) {
            shadowBakingShader.dispose();
        }
        
        // Dispose baked textures
        for (Texture texture : bakedLightmaps.values()) {
            if (texture != null) texture.dispose();
        }
        for (Texture texture : bakedShadowmaps.values()) {
            if (texture != null) texture.dispose();
        }
        
        bakedLightmaps.clear();
        bakedShadowmaps.clear();
        
        disposed = true;
        Log.info("LightmapBaker", "LightmapBaker disposed");
    }
    
    /**
     * Result of a lightmap baking operation
     */
    public static class BakedLightingResult {
        public final String regionId;
        public final Texture lightmapTexture;
        public final Texture shadowmapTexture;
        
        public BakedLightingResult(String regionId, Texture lightmapTexture, Texture shadowmapTexture) {
            this.regionId = regionId;
            this.lightmapTexture = lightmapTexture;
            this.shadowmapTexture = shadowmapTexture;
        }
        
        public boolean hasLightmap() {
            return lightmapTexture != null;
        }
        
        public boolean hasShadowmap() {
            return shadowmapTexture != null;
        }
    }
}