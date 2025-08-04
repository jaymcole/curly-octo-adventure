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
    
    // Create rolling fog with much more obvious movement
    vec2 rollCoord1 = v_texCoord * 1.5 + vec2(u_time * 0.3, u_time * 0.2);   // Large rolling patterns
    vec2 rollCoord2 = v_texCoord * 3.2 + vec2(u_time * -0.25, u_time * 0.4); // Medium swirls  
    vec2 rollCoord3 = v_texCoord * 6.7 + vec2(u_time * 0.15, u_time * -0.3); // Fine detail
    
    float roll1 = fractalNoise(rollCoord1);
    float roll2 = fractalNoise(rollCoord2);
    float roll3 = smoothNoise(rollCoord3);
    
    // Create flowing, rolling motion by warping coordinates with faster movement
    vec2 warp = vec2(
        sin(v_texCoord.x * 6.28 + u_time * 1.2 + roll1 * 3.14) * 0.15,
        cos(v_texCoord.y * 4.71 + u_time * 1.6 + roll2 * 2.35) * 0.12
    );
    
    float warpedFog = fractalNoise(v_texCoord * 2.8 + warp + vec2(u_time * 0.35, u_time * -0.28));
    
    // Combine all layers for complex rolling fog density
    float fogBase = roll1 * 0.4 + roll2 * 0.35 + roll3 * 0.15 + warpedFog * 0.1;
    
    // Create clear gaps by applying threshold with smooth falloff
    float gapThreshold = 0.35;
    float gapSoftness = 0.25;
    float fogDensity = smoothstep(gapThreshold - gapSoftness, gapThreshold + gapSoftness, fogBase);
    
    // Add rolling waves that create moving gaps with much faster movement
    float wave1 = sin(v_texCoord.x * 8.0 + u_time * 2.5) * 0.5 + 0.5;
    float wave2 = cos(v_texCoord.y * 6.0 + u_time * 3.2) * 0.5 + 0.5;
    float wavePattern = (wave1 * 0.6 + wave2 * 0.4);
    
    // Modulate fog density with wave pattern to create moving clear areas
    fogDensity *= (0.3 + wavePattern * 0.7);
    
    // Highly reflective fog color - lighter and more responsive to lighting
    vec3 fogColor = vec3(0.9, 0.92, 0.95);
    
    // Start with ambient lighting
    vec3 totalLighting = u_ambientLight * 1.2; // Slightly brighter ambient for fog
    
    // Calculate lighting from all lights (fog scatters light volumetrically with high reflectivity)
    for (int i = 0; i < 8; i++) {
        if (i >= u_numLights) break;
        
        vec3 lightPos = u_lightPositions[i];
        vec3 lightColor = u_lightColors[i];
        float lightIntensity = u_lightIntensities[i];
        
        vec3 lightDirection = v_worldPos - lightPos;
        float distance = length(lightDirection);
        float attenuation = lightIntensity / (1.0 + 0.02 * distance + 0.005 * distance * distance);
        
        // Enhanced volumetric scattering - fog becomes very reflective and bright
        vec3 lightVec = normalize(-lightDirection);
        
        // Forward scattering (light coming towards viewer through fog)
        float forwardScatter = max(0.0, dot(normal, lightVec));
        
        // Back scattering (light hitting fog from behind)
        float backScatter = max(0.0, -dot(normal, lightVec));
        
        // Side scattering (light hitting fog from sides)
        float sideScatter = 1.0 - abs(dot(normal, lightVec));
        
        // Combine all scattering types with high intensity multipliers
        float totalScatter = forwardScatter * 2.5 + backScatter * 1.8 + sideScatter * 1.2;
        
        // Extra boost for fog visibility - make it very reflective
        vec3 lightContribution = totalScatter * lightColor * attenuation * 1.8;
        
        totalLighting += lightContribution;
    }
    
    vec3 finalColor = fogColor * totalLighting;
    
    // Make fog fairly opaque with clear gaps - perfect for hiding traps
    float baseOpacity = 0.4;  // Base opacity for atmospheric effect
    float maxOpacity = 0.85;  // Maximum opacity in dense areas
    float alpha = baseOpacity + fogDensity * (maxOpacity - baseOpacity);
    
    gl_FragColor = vec4(finalColor, alpha);
}