#ifdef GL_ES
precision mediump float;
#endif

// Material and environment uniforms
uniform vec3 u_ambientLight;
uniform vec3 u_diffuseColor;
uniform float u_diffuseAlpha;
uniform sampler2D u_diffuseTexture;
uniform int u_hasTexture;
uniform float u_farPlane;

// Shadow-casting lights (expensive, limited quantity)
uniform int u_numShadowLights;
uniform vec3 u_shadowLightPositions[8];
uniform vec3 u_shadowLightColors[8]; 
uniform float u_shadowLightIntensities[8];
uniform sampler2D u_cubeShadowMaps[48]; // 8 lights × 6 faces = 48 textures max

// Fallback lights (cheap, support for many more lights)
uniform int u_numFallbackLights;
uniform vec3 u_fallbackLightPositions[256];  // Dramatically increased for many fallback lights
uniform vec3 u_fallbackLightColors[256];
uniform float u_fallbackLightIntensities[256];

// Performance optimization flags
uniform int u_enableSpecular;           // 0/1 to enable/disable specular highlights
uniform float u_specularShininess;     // Shininess factor for specular calculation
uniform float u_lightAttenuationFactor; // Global attenuation multiplier for fine-tuning

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;

/**
 * Samples a cube shadow map for a given light direction and light index.
 * This function determines which face of the cube to sample based on the
 * light direction vector and performs the shadow comparison.
 * 
 * @param lightDirection Direction from fragment to light
 * @param lightIndex Index of the shadow-casting light (0-7)
 * @return Shadow factor (0.0 = fully shadowed, 1.0 = fully lit)
 */
float sampleCubeShadowMap(vec3 lightDirection, int lightIndex) {
    // Determine which face of the cube to sample based on dominant axis
    vec3 absDir = abs(lightDirection);
    float maxComponent = max(max(absDir.x, absDir.y), absDir.z);
    
    vec2 uv;
    int face;
    
    // Calculate UV coordinates for the appropriate cube face
    if (maxComponent == absDir.x) {
        if (lightDirection.x > 0.0) {
            face = 0; // +X face (right)
            uv = vec2(-lightDirection.z, -lightDirection.y) / maxComponent;
        } else {
            face = 1; // -X face (left)
            uv = vec2(lightDirection.z, -lightDirection.y) / maxComponent;
        }
    } else if (maxComponent == absDir.y) {
        if (lightDirection.y > 0.0) {
            face = 2; // +Y face (top)
            uv = vec2(lightDirection.x, lightDirection.z) / maxComponent;
        } else {
            face = 3; // -Y face (bottom)
            uv = vec2(lightDirection.x, -lightDirection.z) / maxComponent;
        }
    } else {
        if (lightDirection.z > 0.0) {
            face = 4; // +Z face (near)
            uv = vec2(lightDirection.x, -lightDirection.y) / maxComponent;
        } else {
            face = 5; // -Z face (far)
            uv = vec2(-lightDirection.x, -lightDirection.y) / maxComponent;
        }
    }
    
    // Convert from [-1,1] to [0,1] UV space
    uv = uv * 0.5 + 0.5;
    
    // Sample the appropriate shadow map using unrolled loop (GLSL requirement)
    float closestDepth = 1.0; // Default to no shadow
    
    // Unrolled loop for shadow map sampling (GPU driver optimization)
    if (lightIndex == 0) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[0], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[1], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[2], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[3], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[4], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[5], uv).r;
    } else if (lightIndex == 1) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[6], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[7], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[8], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[9], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[10], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[11], uv).r;
    } else if (lightIndex == 2) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[12], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[13], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[14], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[15], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[16], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[17], uv).r;
    } else if (lightIndex == 3) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[18], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[19], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[20], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[21], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[22], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[23], uv).r;
    } else if (lightIndex == 4) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[24], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[25], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[26], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[27], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[28], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[29], uv).r;
    } else if (lightIndex == 5) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[30], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[31], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[32], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[33], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[34], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[35], uv).r;
    } else if (lightIndex == 6) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[36], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[37], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[38], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[39], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[40], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[41], uv).r;
    } else if (lightIndex == 7) {
        if (face == 0) closestDepth = texture2D(u_cubeShadowMaps[42], uv).r;
        else if (face == 1) closestDepth = texture2D(u_cubeShadowMaps[43], uv).r;
        else if (face == 2) closestDepth = texture2D(u_cubeShadowMaps[44], uv).r;
        else if (face == 3) closestDepth = texture2D(u_cubeShadowMaps[45], uv).r;
        else if (face == 4) closestDepth = texture2D(u_cubeShadowMaps[46], uv).r;
        else closestDepth = texture2D(u_cubeShadowMaps[47], uv).r;
    }
    
    // Calculate current distance from light and perform shadow comparison
    float currentDistance = length(lightDirection) / u_farPlane;
    float bias = 0.005; // Bias to prevent shadow acne
    
    // Return shadow factor: 0.0 = fully shadowed, 1.0 = fully lit
    return currentDistance - bias > closestDepth ? 0.0 : 1.0;
}

/**
 * Calculates lighting contribution from shadow-casting lights.
 * These lights are expensive but provide realistic shadows.
 * 
 * @param normal Surface normal vector
 * @param viewDir Direction from fragment to camera (for specular calculation)
 * @return Combined lighting from all shadow-casting lights
 */
vec3 calculateShadowCastingLights(vec3 normal, vec3 viewDir) {
    vec3 totalLighting = vec3(0.0);
    
    // Process each shadow-casting light
    for (int i = 0; i < 8; i++) {
        if (i >= u_numShadowLights) break;
        
        vec3 lightPos = u_shadowLightPositions[i];
        vec3 lightColor = u_shadowLightColors[i];
        float lightIntensity = u_shadowLightIntensities[i];
        
        // Calculate light direction and distance
        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        
        // Physically-based attenuation: intensity / (1 + linear*d + quadratic*d²)
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        attenuation *= u_lightAttenuationFactor; // Global attenuation control
        
        // Diffuse lighting (Lambertian reflection)
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 diffuse = diff * lightColor * attenuation;
        
        // Specular lighting (Blinn-Phong model) - only if enabled
        vec3 specular = vec3(0.0);
        if (u_enableSpecular == 1 && diff > 0.0) {
            vec3 halfwayDir = normalize(lightDir + viewDir);
            float spec = pow(max(dot(normal, halfwayDir), 0.0), u_specularShininess);
            specular = spec * lightColor * attenuation * 0.5; // Reduced specular intensity
        }
        
        // Sample shadow map and apply shadows
        float shadow = sampleCubeShadowMap(lightDirection, i);
        vec3 lightContribution = (diffuse + specular) * shadow;
        
        totalLighting += lightContribution;
    }
    
    return totalLighting;
}

/**
 * Calculates lighting contribution from fallback lights.
 * These lights are performant and don't cast shadows.
 * Uses optimized calculations for better performance with many lights.
 * 
 * @param normal Surface normal vector
 * @param viewDir Direction from fragment to camera (for specular calculation)
 * @return Combined lighting from all fallback lights
 */
vec3 calculateFallbackLights(vec3 normal, vec3 viewDir) {
    vec3 totalLighting = vec3(0.0);
    
    // Process all fallback lights efficiently
    // Use a single loop with optimizations for performance
    for (int i = 0; i < 256 && i < u_numFallbackLights; i++) {
        vec3 lightPos = u_fallbackLightPositions[i];
        vec3 lightColor = u_fallbackLightColors[i];
        float lightIntensity = u_fallbackLightIntensities[i];
        
        // Calculate light direction and distance
        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        
        // Optimized attenuation for fallback lights (less expensive than shadow lights)
        float attenuation = lightIntensity / (1.0 + 0.03 * distance + 0.012 * distance * distance);
        attenuation *= u_lightAttenuationFactor;
        
        // Early distance culling for performance - skip very dim lights
        if (attenuation < 0.005) continue;
        
        // Diffuse lighting calculation
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 diffuse = diff * lightColor * attenuation;
        
        // Simplified specular for fallback lights (performance optimization)
        // Only calculate specular for brighter lights to maintain performance
        vec3 specular = vec3(0.0);
        if (u_enableSpecular == 1 && diff > 0.15 && attenuation > 0.02) {
            float spec = pow(max(dot(normal, lightDir), 0.0), u_specularShininess * 0.7);
            specular = spec * lightColor * attenuation * 0.3; // Reduced specular contribution
        }
        
        totalLighting += diffuse + specular;
    }
    
    return totalLighting;
}

/**
 * Main fragment shader entry point.
 * Combines ambient lighting, shadow-casting lights, and fallback lights
 * into the final fragment color.
 */
void main() {
    // Normalize the interpolated surface normal
    vec3 normal = normalize(v_normal);
    
    // Calculate view direction for specular highlights
    vec3 viewDir = normalize(-v_worldPos); // Assuming camera at origin
    
    // Sample material color from texture or use diffuse color
    vec3 materialColor = u_diffuseColor;
    if (u_hasTexture == 1) {
        vec4 texColor = texture2D(u_diffuseTexture, v_texCoord);
        materialColor = texColor.rgb * u_diffuseColor; // Modulate texture with diffuse color
    }
    
    // Start with ambient lighting (provides base illumination)
    vec3 totalLighting = u_ambientLight;
    
    // Add contribution from shadow-casting lights (expensive but realistic)
    if (u_numShadowLights > 0) {
        totalLighting += calculateShadowCastingLights(normal, viewDir);
    }
    
    // Add contribution from fallback lights (cheap and numerous)
    if (u_numFallbackLights > 0) {
        totalLighting += calculateFallbackLights(normal, viewDir);
    }
    
    // Apply lighting to material color
    vec3 finalColor = materialColor * totalLighting;
    
    // Clamp to reasonable values to prevent overexposure
    finalColor = min(finalColor, vec3(2.0));
    
    // Output final color with material alpha
    gl_FragColor = vec4(finalColor, u_diffuseAlpha);
}