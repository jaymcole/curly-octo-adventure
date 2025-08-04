#ifdef GL_ES
precision mediump float;
#endif

// Material and environment
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

// Fractal noise for more complex patterns
float fractalNoise(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += smoothNoise(p) * amplitude;
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

// Simple bloom function to create glow effect
float calculateBloom(vec2 uv, float intensity, float radius) {
    float bloom = 0.0;
    float steps = 8.0;
    float angleStep = 6.28318 / steps;
    
    for (float i = 0.0; i < steps; i++) {
        float angle = i * angleStep;
        vec2 offset = vec2(cos(angle), sin(angle)) * radius;
        vec2 sampleUV = uv + offset;
        
        // Sample surrounding pixels for bloom effect
        float sampleNoise = fractalNoise(sampleUV * 3.0 + vec2(u_time * 0.2, u_time * 0.1));
        bloom += smoothstep(0.4, 0.8, sampleNoise) * intensity;
    }
    
    return bloom / steps;
}

void main() {
    // Create flowing lava pattern
    vec2 flowCoord1 = v_texCoord * 3.0 + vec2(u_time * 0.2, u_time * 0.1);
    vec2 flowCoord2 = v_texCoord * 6.0 + vec2(u_time * -0.15, u_time * 0.25);
    
    float flow1 = fractalNoise(flowCoord1);
    float flow2 = smoothNoise(flowCoord2);
    
    // Combine flows for lava cracks and streams
    float lavaPattern = flow1 * 0.7 + flow2 * 0.3;
    
    // Create hot spots that pulse more intensely
    float hotSpot = sin(u_time * 2.0 + lavaPattern * 6.28) * 0.5 + 0.5;
    float superHot = sin(u_time * 3.5 + lavaPattern * 12.56) * 0.3 + 0.7;
    
    // Enhanced lava color gradient with very bright peaks
    vec3 darkLava = vec3(0.5, 0.1, 0.0);
    vec3 hotLava = vec3(1.5, 0.8, 0.2);
    vec3 brightLava = vec3(2.2, 1.6, 0.6);
    vec3 whiteLava = vec3(3.5, 2.8, 1.5); // Very bright for glow effect
    
    // Mix colors based on pattern and hot spots
    float intensity = lavaPattern * 0.7 + hotSpot * 0.3;
    vec3 lavaColor;
    if (intensity < 0.4) {
        lavaColor = mix(darkLava, hotLava, intensity * 2.5);
    } else if (intensity < 0.7) {
        lavaColor = mix(hotLava, brightLava, (intensity - 0.4) * 3.33);
    } else {
        lavaColor = mix(brightLava, whiteLava, (intensity - 0.7) * 3.33);
    }
    
    // Enhanced emissive glow with more dramatic effect
    float emissive = 1.0 + hotSpot * 1.5 + superHot * 1.0;
    lavaColor *= emissive;
    
    // Simple bloom effect using shader-based expansion
    float bloomIntensity = smoothstep(0.6, 1.0, intensity) * superHot;
    float bloom = calculateBloom(v_texCoord, bloomIntensity, 0.03);
    
    // Add bloom as additive glow
    vec3 bloomColor = vec3(1.2, 0.6, 0.2) * bloom * 0.4;
    lavaColor += bloomColor;
    
    // Create rim lighting effect for additional glow
    float rimEffect = pow(1.0 - abs(dot(normalize(v_normal), vec3(0.0, 1.0, 0.0))), 1.5);
    vec3 rimGlow = vec3(1.5, 0.7, 0.2) * rimEffect * intensity * 0.3;
    lavaColor += rimGlow;
    
    gl_FragColor = vec4(lavaColor, 0.95);
}