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

void main() {
    vec3 normal = normalize(v_normal);
    
    // Create animated ripple pattern
    vec2 rippleCoord = v_texCoord * 8.0 + u_time * 0.5;
    float ripple1 = sin(length(rippleCoord - vec2(4.0)) * 6.0 - u_time * 3.0) * 0.5 + 0.5;
    float ripple2 = sin(length(rippleCoord - vec2(2.0, 6.0)) * 4.0 - u_time * 2.0) * 0.3 + 0.7;
    
    // Add flowing noise pattern
    vec2 flowCoord = v_texCoord * 4.0 + vec2(u_time * 0.1, u_time * 0.05);
    float flow = smoothNoise(flowCoord) * 0.3 + 0.7;
    
    // Combine effects for water pattern
    float waterPattern = ripple1 * ripple2 * flow;
    
    // Base water color with pattern modulation
    vec3 waterColor = vec3(0.2, 0.6, 1.0) * (0.7 + waterPattern * 0.3);
    
    // Start with ambient lighting
    vec3 totalLighting = u_ambientLight;
    
    // Calculate lighting from all lights
    for (int i = 0; i < 8; i++) {
        if (i >= u_numLights) break;
        
        vec3 lightPos = u_lightPositions[i];
        vec3 lightColor = u_lightColors[i];
        float lightIntensity = u_lightIntensities[i];
        
        vec3 lightDirection = v_worldPos - lightPos;
        vec3 lightDir = normalize(-lightDirection);
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.05 * distance + 0.016 * distance * distance);
        
        // Diffuse lighting calculation with animated normal perturbation
        vec3 perturbedNormal = normal + vec3(
            sin(v_worldPos.x * 2.0 + u_time) * 0.1,
            0.0,
            cos(v_worldPos.z * 2.0 + u_time) * 0.1
        );
        perturbedNormal = normalize(perturbedNormal);
        
        float diff = max(dot(perturbedNormal, lightDir), 0.0);
        vec3 lightContribution = diff * lightColor * attenuation;
        
        totalLighting += lightContribution;
    }
    
    vec3 finalColor = waterColor * totalLighting;
    
    // Water transparency with subtle variation
    float alpha = 0.4 + waterPattern * 0.2;
    
    gl_FragColor = vec4(finalColor, alpha);
}