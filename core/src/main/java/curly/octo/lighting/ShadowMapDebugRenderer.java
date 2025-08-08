package curly.octo.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.minlog.Log;
import curly.octo.rendering.CubeShadowMapRenderer;

/**
 * Debug renderer for visualizing shadow maps on screen
 */
public class ShadowMapDebugRenderer implements Disposable {
    
    private ShaderProgram debugShader;
    private Mesh quadMesh;
    private boolean disposed = false;
    
    public ShadowMapDebugRenderer() {
        createQuadMesh();
        loadDebugShader();
    }
    
    private void createQuadMesh() {
        float[] vertices = {
            // Position (x, y, z), UV (u, v)
            -1f, -1f, 0f,  0f, 0f,  // Bottom-left
             1f, -1f, 0f,  1f, 0f,  // Bottom-right
             1f,  1f, 0f,  1f, 1f,  // Top-right
            -1f,  1f, 0f,  0f, 1f   // Top-left
        };
        
        short[] indices = {
            0, 1, 2,
            2, 3, 0
        };
        
        quadMesh = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        
        quadMesh.setVertices(vertices);
        quadMesh.setIndices(indices);
    }
    
    private void loadDebugShader() {
        String vertexShader = 
            "attribute vec4 a_position;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform vec4 u_viewport;\n" + // x, y, width, height in normalized coords
            "\n" +
            "void main() {\n" +
            "    v_texCoords = a_texCoord0;\n" +
            "    // Transform position to viewport\n" +
            "    vec2 pos = a_position.xy;\n" +
            "    pos = pos * u_viewport.zw + u_viewport.xy;\n" +
            "    gl_Position = vec4(pos, 0.0, 1.0);\n" +
            "}\n";
        
        String fragmentShader = 
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 v_texCoords;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform int u_debugMode;\n" + // 0 = depth, 1 = color
            "\n" +
            "void main() {\n" +
            "    vec4 texColor = texture2D(u_texture, v_texCoords);\n" +
            "    \n" +
            "    if (u_debugMode == 0) {\n" +
            "        // Depth visualization - make depth visible\n" +
            "        float depth = texColor.r;\n" +
            "        gl_FragColor = vec4(depth, depth, depth, 1.0);\n" +
            "    } else {\n" +
            "        // Color visualization\n" +
            "        gl_FragColor = vec4(texColor.rgb, 1.0);\n" +
            "    }\n" +
            "}\n";
        
        debugShader = new ShaderProgram(vertexShader, fragmentShader);
        
        if (!debugShader.isCompiled()) {
            Log.error("ShadowMapDebugRenderer", "Debug shader compilation failed: " + debugShader.getLog());
            throw new RuntimeException("Shadow map debug shader compilation failed");
        }
        
        Log.info("ShadowMapDebugRenderer", "Debug shader loaded successfully");
    }
    
    /**
     * Render shadow maps to screen divided evenly
     */
    public void renderShadowMaps(CubeShadowMapRenderer shadowRenderer, int numLights, boolean showDepth) {
        renderShadowMaps(shadowRenderer, numLights, 0, showDepth);
    }
    
    /**
     * Render shadow maps to screen divided evenly with dynamic/static separation
     */
    public void renderShadowMaps(CubeShadowMapRenderer shadowRenderer, int numDynamicLights, int numStaticLights, boolean showDepth) {
        int numLights = numDynamicLights + numStaticLights;
        if (numLights <= 0) {
            Log.warn("ShadowMapDebugRenderer", "No shadow maps to render");
            return;
        }
        
        // Calculate grid dimensions
        int cols = (int) Math.ceil(Math.sqrt(numLights * 6)); // 6 faces per light
        int rows = (int) Math.ceil((numLights * 6) / (float) cols);
        
        Log.info("ShadowMapDebugRenderer", "Rendering " + (numLights * 6) + " shadow map faces (" + 
            numDynamicLights + " dynamic, " + numStaticLights + " static) in " + cols + "x" + rows + " grid");
        
        // Disable depth testing for debug overlay
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        
        debugShader.bind();
        debugShader.setUniformi("u_debugMode", showDepth ? 0 : 1);
        
        int currentIndex = 0;
        
        // Render each light's 6 cube faces
        for (int lightIndex = 0; lightIndex < numLights; lightIndex++) {
            String lightType = (lightIndex < numDynamicLights) ? "DYNAMIC" : "STATIC";
            for (int face = 0; face < 6; face++) {
                Texture shadowMap = shadowRenderer.getShadowMapTexture(lightIndex, face);
                if (shadowMap != null) {
                    Log.debug("ShadowMapDebugRenderer", "Rendering " + lightType + " light " + lightIndex + " face " + face + " at index " + currentIndex);
                    renderShadowMapQuad(shadowMap, currentIndex, cols, rows);
                    currentIndex++;
                }
            }
        }
        
        // Re-enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
    
    private void renderShadowMapQuad(Texture shadowMap, int index, int cols, int rows) {
        // Calculate grid position
        int col = index % cols;
        int row = index / cols;
        
        // Calculate normalized viewport coordinates
        float cellWidth = 2.0f / cols;  // Full screen width is 2.0 in normalized coords (-1 to 1)
        float cellHeight = 2.0f / rows; // Full screen height is 2.0 in normalized coords (-1 to 1)
        
        float x = -1.0f + col * cellWidth;        // Start from left edge (-1.0)
        float y = 1.0f - (row + 1) * cellHeight;  // Start from top edge (1.0), going down
        
        // Set viewport for this quad (x, y, width, height in normalized coords)
        debugShader.setUniformf("u_viewport", x, y, cellWidth, cellHeight);
        
        // Bind shadow map texture
        shadowMap.bind(0);
        debugShader.setUniformi("u_texture", 0);
        
        // Render quad
        quadMesh.render(debugShader, GL20.GL_TRIANGLES);
    }
    
    /**
     * Render specific shadow map faces to screen
     */
    public void renderSpecificShadowMaps(Array<Texture> shadowMaps, boolean showDepth) {
        if (shadowMaps.size == 0) {
            Log.warn("ShadowMapDebugRenderer", "No shadow maps provided");
            return;
        }
        
        // Calculate grid dimensions
        int totalMaps = shadowMaps.size;
        int cols = (int) Math.ceil(Math.sqrt(totalMaps));
        int rows = (int) Math.ceil(totalMaps / (float) cols);
        
        Log.info("ShadowMapDebugRenderer", "Rendering " + totalMaps + " shadow maps in " + cols + "x" + rows + " grid");
        
        // Disable depth testing for debug overlay
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        
        debugShader.bind();
        debugShader.setUniformi("u_debugMode", showDepth ? 0 : 1);
        
        for (int i = 0; i < totalMaps; i++) {
            Texture shadowMap = shadowMaps.get(i);
            if (shadowMap != null) {
                renderShadowMapQuad(shadowMap, i, cols, rows);
            }
        }
        
        // Re-enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
    
    @Override
    public void dispose() {
        if (disposed) return;
        
        if (debugShader != null) {
            debugShader.dispose();
        }
        
        if (quadMesh != null) {
            quadMesh.dispose();
        }
        
        disposed = true;
        Log.info("ShadowMapDebugRenderer", "ShadowMapDebugRenderer disposed");
    }
}