package curly.octo.rendering.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Utility class for extracting and managing material properties during rendering.
 * Centralizes material-related operations to avoid code duplication across renderers.
 */
public class MaterialUtils {

    // Reusable color object to avoid allocations
    private static final Color tempColor = new Color();

    /**
     * Represents extracted material properties ready for shader uniform setting.
     */
    public static class MaterialProperties {
        public final Color diffuseColor;
        public float alpha;
        public boolean hasTexture;
        public boolean isTransparent;

        public MaterialProperties() {
            this.diffuseColor = new Color(Color.WHITE);
            this.alpha = 1.0f;
            this.hasTexture = false;
            this.isTransparent = false;
        }

        public MaterialProperties(Color diffuseColor, float alpha, boolean hasTexture, boolean isTransparent) {
            this.diffuseColor = new Color(diffuseColor);
            this.alpha = alpha;
            this.hasTexture = hasTexture;
            this.isTransparent = isTransparent;
        }
    }

    /**
     * Extracts material properties from a LibGDX Material object.
     *
     * @param material The material to extract properties from
     * @return MaterialProperties object containing diffuse color, alpha, texture info, and transparency flag
     */
    public static MaterialProperties extractProperties(Material material) {
        MaterialProperties props = new MaterialProperties();

        if (material == null) {
            return props;
        }

        // Extract diffuse color
        ColorAttribute diffuseAttr = (ColorAttribute) material.get(ColorAttribute.Diffuse);
        if (diffuseAttr != null) {
            props.diffuseColor.set(diffuseAttr.color);
        }

        // Extract alpha from blending attribute
        BlendingAttribute blendingAttr = (BlendingAttribute) material.get(BlendingAttribute.Type);
        if (blendingAttr != null) {
            props.alpha = blendingAttr.opacity;
            props.isTransparent = props.alpha < 1.0f;
        } else {
            props.alpha = 1.0f;
            props.isTransparent = false;
        }

        // Check for texture
        TextureAttribute textureAttr = (TextureAttribute) material.get(TextureAttribute.Diffuse);
        props.hasTexture = (textureAttr != null);

        return props;
    }

    /**
     * Sets material properties as shader uniforms.
     *
     * @param shader The shader program to set uniforms on
     * @param properties The material properties to set
     */
    public static void setMaterialUniforms(ShaderProgram shader, MaterialProperties properties) {
        shader.setUniformf("u_diffuseColor", properties.diffuseColor);
        shader.setUniformf("u_alpha", properties.alpha);
    }

    /**
     * Determines if a material should be rendered in the transparent pass.
     *
     * @param material The material to check
     * @return true if material requires alpha blending
     */
    public static boolean isTransparent(Material material) {
        if (material == null) {
            return false;
        }

        BlendingAttribute blendingAttr = (BlendingAttribute) material.get(BlendingAttribute.Type);
        if (blendingAttr != null && blendingAttr.opacity < 1.0f) {
            return true;
        }

        return false;
    }

    /**
     * Sets common light uniforms for an array of lights.
     *
     * @param shader The shader program to set uniforms on
     * @param lightPositions Array of light positions (vec3)
     * @param lightColors Array of light colors (vec3)
     * @param lightIntensities Array of light intensities (float)
     * @param numLights Number of active lights
     */
    public static void setLightArrayUniforms(ShaderProgram shader,
                                            float[] lightPositions,
                                            float[] lightColors,
                                            float[] lightIntensities,
                                            int numLights) {
        shader.setUniform3fv("u_lightPositions", lightPositions, 0, numLights * 3);
        shader.setUniform3fv("u_lightColors", lightColors, 0, numLights * 3);
        shader.setUniform1fv("u_lightIntensities", lightIntensities, 0, numLights);
        shader.setUniformi("u_numLights", numLights);
    }

    /**
     * Sets cube shadow map texture uniforms.
     *
     * @param shader The shader program
     * @param startTextureUnit Starting texture unit (e.g., 1, as 0 is usually diffuse)
     * @param numShadowMaps Number of shadow maps to bind
     */
    public static void setShadowMapUniforms(ShaderProgram shader, int startTextureUnit, int numShadowMaps) {
        // Set each sampler uniform individually
        for (int i = 0; i < numShadowMaps; i++) {
            shader.setUniformi("u_cubeShadowMaps[" + i + "]", startTextureUnit + i);
        }
    }
}
