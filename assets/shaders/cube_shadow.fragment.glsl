#ifdef GL_ES
precision mediump float;
#endif

// Primary light (shadow-casting)
uniform vec3 u_lightPosition;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform vec3 u_diffuseColor;
uniform vec3 u_ambientLight;
uniform float u_farPlane;

// Additional lights from LibGDX environment will be handled by standard lighting
// This shader focuses on shadow-casting from the primary light

// Cube shadow map faces (6 textures)
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
    vec3 lightDirection = v_worldPos - u_lightPosition;
    vec3 lightDir = normalize(-lightDirection);
    
    // Calculate distance attenuation
    float distance = length(lightDirection);
    float attenuation = u_lightIntensity / (1.0 + 0.09 * distance + 0.032 * distance * distance);
    
    // Diffuse lighting calculation
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * u_lightColor * attenuation;
    
    // Cube shadow calculation
    float shadow = sampleCubeShadowMap(lightDirection);
    
    // Final color with shadows
    vec3 lighting = u_ambientLight + (1.0 - shadow) * diffuse;
    vec3 finalColor = u_diffuseColor * lighting;
    
    gl_FragColor = vec4(finalColor, 1.0);
}