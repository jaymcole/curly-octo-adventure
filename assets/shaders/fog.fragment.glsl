#ifdef GL_ES
precision mediump float;
#endif

// Material and environment
uniform vec3 u_ambientLight;
uniform vec3 u_lightPositions[8];
uniform vec3 u_lightColors[8];
uniform float u_lightIntensities[8];
uniform int u_numLights;
uniform float u_time;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying float v_time;

// Simple noise function
float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Smooth noise
float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    
    float a = noise(i);
    float b = noise(i + vec2(1.0, 0.0));
    float c = noise(i + vec2(0.0, 1.0));
    float d = noise(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Fractal noise for volumetric fog
float fractalNoise(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += smoothNoise(p) * amplitude;
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec3 normal = normalize(v_normal);
    
    // Create drifting fog patterns
    vec2 driftCoord1 = v_texCoord * 2.0 + vec2(u_time * 0.05, u_time * 0.03);
    vec2 driftCoord2 = v_texCoord * 4.0 + vec2(u_time * -0.02, u_time * 0.08);
    vec2 driftCoord3 = v_texCoord * 8.0 + vec2(u_time * 0.01, u_time * -0.04);
    
    float drift1 = fractalNoise(driftCoord1);
    float drift2 = smoothNoise(driftCoord2);
    float drift3 = smoothNoise(driftCoord3);
    
    // Combine drifts for volumetric fog density
    float fogDensity = (drift1 * 0.5 + drift2 * 0.3 + drift3 * 0.2);
    
    // Add wispy variations
    float wisp = sin(u_time * 0.5 + fogDensity * 3.14) * 0.1 + 0.9;
    fogDensity *= wisp;
    
    // Base fog color (light gray/white)
    vec3 fogColor = vec3(0.8, 0.85, 0.9);
    
    // Start with ambient lighting
    vec3 totalLighting = u_ambientLight;
    
    // Calculate lighting from all lights (fog scatters light)
    for (int i = 0; i < 8; i++) {
        if (i >= u_numLights) break;
        
        vec3 lightPos = u_lightPositions[i];
        vec3 lightColor = u_lightColors[i];
        float lightIntensity = u_lightIntensities[i];
        
        vec3 lightDirection = v_worldPos - lightPos;
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        
        // Fog scatters light more uniformly
        float scattering = max(0.3, dot(normal, normalize(-lightDirection)));
        vec3 lightContribution = scattering * lightColor * attenuation * 0.7; // Reduced intensity for fog
        
        totalLighting += lightContribution;
    }
    
    vec3 finalColor = fogColor * totalLighting;
    
    // Fog transparency varies with density and distance
    float alpha = 0.15 + fogDensity * 0.2;
    
    gl_FragColor = vec4(finalColor, alpha);
}