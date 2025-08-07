#ifdef GL_ES
precision mediump float;
#endif

// Material and environment
uniform vec3 u_ambientLight;
uniform vec3 u_diffuseColor;
uniform float u_diffuseAlpha;
uniform sampler2D u_diffuseTexture;
uniform int u_hasTexture;

// Lightmap support
uniform sampler2D u_lightmapTexture;
uniform int u_hasLightmap;
uniform float u_lightmapIntensity;

// Dynamic lighting (shadowed lights)
uniform int u_numShadowLights;
uniform vec3 u_shadowLightPositions[4];
uniform vec3 u_shadowLightColors[4];
uniform float u_shadowLightIntensities[4];
uniform sampler2D u_cubeShadowMaps[24]; // 4 lights × 6 faces = 24 textures max
uniform float u_farPlane;

// Dynamic lighting (unshadowed lights)
uniform int u_numUnshadowedLights;
uniform vec3 u_unshadowedLightPositions[12];
uniform vec3 u_unshadowedLightColors[12];
uniform float u_unshadowedLightIntensities[12];

// Lighting quality settings
uniform float u_shadowBias;
uniform float u_lightmapBlendFactor; // How much to blend baked vs dynamic lighting

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying vec2 v_lightmapCoord;

float sampleCubeShadowMap(vec3 lightDirection, int lightIndex) {
    // Determine which face of the cube to sample
    vec3 absDir = abs(lightDirection);
    float maxComponent = max(max(absDir.x, absDir.y), absDir.z);
    
    vec2 uv;
    int face;
    
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
    
    // Convert from [-1,1] to [0,1]
    uv = uv * 0.5 + 0.5;
    
    // Calculate shadow map texture index: lightIndex * 6 + face
    int shadowMapIndex = lightIndex * 6 + face;
    
    // Sample the appropriate shadow map with unrolled loops for GLSL compatibility
    float closestDepth = 1.0; // Default to no shadow
    
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
    }
    
    // Calculate current distance from light
    float currentDistance = length(lightDirection) / u_farPlane;
    
    // Shadow test with bias
    return currentDistance - u_shadowBias > closestDepth ? 1.0 : 0.0;
}

vec3 calculateDynamicLighting(vec3 normal) {
    vec3 dynamicLighting = vec3(0.0);
    
    // Calculate shadowed dynamic lights
    for (int i = 0; i < 4; i++) {
        if (i >= u_numShadowLights) break;
        
        vec3 lightPos = u_shadowLightPositions[i];
        vec3 lightColor = u_shadowLightColors[i];
        float lightIntensity = u_shadowLightIntensities[i];
        
        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        
        // Diffuse lighting calculation
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 lightContribution = diff * lightColor * attenuation;
        
        // Apply shadows
        float shadow = sampleCubeShadowMap(lightDirection, i);
        lightContribution *= (1.0 - shadow);
        
        dynamicLighting += lightContribution;
    }
    
    // Calculate unshadowed dynamic lights
    for (int i = 0; i < 12; i++) {
        if (i >= u_numUnshadowedLights) break;
        
        vec3 lightPos = u_unshadowedLightPositions[i];
        vec3 lightColor = u_unshadowedLightColors[i];
        float lightIntensity = u_unshadowedLightIntensities[i];
        
        vec3 lightDir = normalize(lightPos - v_worldPos);
        float distance = length(lightPos - v_worldPos);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        
        // Diffuse lighting calculation
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 lightContribution = diff * lightColor * attenuation;
        
        dynamicLighting += lightContribution;
    }
    
    return dynamicLighting;
}

void main() {
    vec3 normal = normalize(v_normal);
    
    // Sample base material
    vec3 baseMaterial = u_diffuseColor;
    if (u_hasTexture == 1) {
        vec4 texColor = texture2D(u_diffuseTexture, v_texCoord);
        baseMaterial = texColor.rgb * u_diffuseColor;
    }
    
    // Sample baked lightmap if available
    vec3 bakedLighting = vec3(0.0);
    if (u_hasLightmap == 1) {
        vec3 lightmapColor = texture2D(u_lightmapTexture, v_lightmapCoord).rgb;
        bakedLighting = lightmapColor * u_lightmapIntensity;
    }
    
    // Calculate dynamic lighting
    vec3 dynamicLighting = calculateDynamicLighting(normal);
    
    // Blend baked and dynamic lighting
    vec3 totalLighting;
    if (u_hasLightmap == 1) {
        // Blend between baked and dynamic lighting
        totalLighting = u_ambientLight + 
                       mix(dynamicLighting, bakedLighting, u_lightmapBlendFactor) + 
                       dynamicLighting * (1.0 - u_lightmapBlendFactor);
    } else {
        // Pure dynamic lighting
        totalLighting = u_ambientLight + dynamicLighting;
    }
    
    vec3 finalColor = baseMaterial * totalLighting;
    
    gl_FragColor = vec4(finalColor, u_diffuseAlpha);
}