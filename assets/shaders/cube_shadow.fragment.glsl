#ifdef GL_ES
precision mediump float;
#endif

// Material and environment
uniform vec3 u_ambientLight;
uniform vec3 u_diffuseColor;
uniform float u_farPlane;

// All lights (up to 8 lights total)
uniform int u_numLights;
uniform vec3 u_lightPositions[8];
uniform vec3 u_lightColors[8];
uniform float u_lightIntensities[8];

// Shadow casting lights (up to 8 lights with shadows)
uniform int u_numShadowLights;
uniform sampler2D u_cubeShadowMaps[48]; // 8 lights × 6 faces = 48 textures max

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;

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
    
    // Sample the appropriate shadow map - need to handle dynamic indexing
    float closestDepth = 1.0; // Default to no shadow
    
    // Handle first 8 lights with unrolled loops (GLSL limitation)
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
    
    // Calculate current distance from light
    float currentDistance = length(lightDirection) / u_farPlane;
    
    // Simple shadow test with bias
    float bias = 0.005;
    return currentDistance - bias > closestDepth ? 1.0 : 0.0;
}

void main() {
    vec3 normal = normalize(v_normal);
    vec3 baseMaterial = u_diffuseColor;
    
    // Start with ambient lighting
    vec3 totalLighting = u_ambientLight;
    
    // Calculate lighting from ALL lights
    for (int i = 0; i < 8; i++) {
        if (i >= u_numLights) break;
        
        vec3 lightPos = u_lightPositions[i];
        vec3 lightColor = u_lightColors[i];
        float lightIntensity = u_lightIntensities[i];
        
        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        
        // Diffuse lighting calculation
        float diff = max(dot(normal, lightDir), 0.0);
        vec3 lightContribution = diff * lightColor * attenuation;
        
        // Apply shadows if this light has shadow maps (first u_numShadowLights)
        if (i < u_numShadowLights) {
            float shadow = sampleCubeShadowMap(lightDirection, i);
            lightContribution *= (1.0 - shadow);
        }
        
        totalLighting += lightContribution;
    }
    
    vec3 finalColor = baseMaterial * totalLighting;
    
    gl_FragColor = vec4(finalColor, 1.0);
}