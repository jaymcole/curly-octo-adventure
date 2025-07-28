#ifdef GL_ES
precision mediump float;
#endif

// Material and environment
uniform vec3 u_ambientLight;
uniform float u_farPlane;

// All lights (up to 8 lights total, index 0 is primary with shadows)
uniform int u_numLights;
uniform vec3 u_lightPositions[8];
uniform vec3 u_lightColors[8];
uniform float u_lightIntensities[8];

// Cube shadow map faces (6 textures) - only for primary light (index 0)
uniform sampler2D u_cubeShadowMap[6];

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;

float sampleCubeShadowMap(vec3 lightDirection) {
    // Determine which face of the cube to sample
    vec3 absDir = abs(lightDirection);
    float maxComponent = max(max(absDir.x, absDir.y), absDir.z);
    
    vec2 uv;
    int face;
    
    if (maxComponent == absDir.x) {
        if (lightDirection.x > 0.0) {
            // +X face (right)
            face = 0;
            uv = vec2(-lightDirection.z, -lightDirection.y) / maxComponent;
        } else {
            // -X face (left)
            face = 1;
            uv = vec2(lightDirection.z, -lightDirection.y) / maxComponent;
        }
    } else if (maxComponent == absDir.y) {
        if (lightDirection.y > 0.0) {
            // +Y face (top)
            face = 2;
            uv = vec2(lightDirection.x, lightDirection.z) / maxComponent;
        } else {
            // -Y face (bottom)
            face = 3;
            uv = vec2(lightDirection.x, -lightDirection.z) / maxComponent;
        }
    } else {
        if (lightDirection.z > 0.0) {
            // +Z face (near)
            face = 4;
            uv = vec2(lightDirection.x, -lightDirection.y) / maxComponent;
        } else {
            // -Z face (far)
            face = 5;
            uv = vec2(-lightDirection.x, -lightDirection.y) / maxComponent;
        }
    }
    
    // Convert from [-1,1] to [0,1]
    uv = uv * 0.5 + 0.5;
    
    // Sample the appropriate face
    float closestDepth;
    if (face == 0) closestDepth = texture2D(u_cubeShadowMap[0], uv).r;
    else if (face == 1) closestDepth = texture2D(u_cubeShadowMap[1], uv).r;
    else if (face == 2) closestDepth = texture2D(u_cubeShadowMap[2], uv).r;
    else if (face == 3) closestDepth = texture2D(u_cubeShadowMap[3], uv).r;
    else if (face == 4) closestDepth = texture2D(u_cubeShadowMap[4], uv).r;
    else closestDepth = texture2D(u_cubeShadowMap[5], uv).r;
    
    // Calculate current distance from light
    float currentDistance = length(lightDirection) / u_farPlane;
    
    // Simple shadow test with bias
    float bias = 0.005;
    return currentDistance - bias > closestDepth ? 1.0 : 0.0;
}

void main() {
    vec3 normal = normalize(v_normal);
    vec3 baseMaterial = vec3(0.7, 0.7, 0.7);
    
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
        
        // Apply shadows ONLY to the primary light (index 0)
        if (i == 0) {
            float shadow = sampleCubeShadowMap(lightDirection);
            lightContribution *= (1.0 - shadow);
        }
        
        totalLighting += lightContribution;
    }
    
    vec3 finalColor = baseMaterial * totalLighting;
    
    gl_FragColor = vec4(finalColor, 1.0);
}