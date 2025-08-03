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

void main() {
    // Create flowing lava pattern
    vec2 flowCoord1 = v_texCoord * 3.0 + vec2(u_time * 0.2, u_time * 0.1);
    vec2 flowCoord2 = v_texCoord * 6.0 + vec2(u_time * -0.15, u_time * 0.25);
    
    float flow1 = fractalNoise(flowCoord1);
    float flow2 = smoothNoise(flowCoord2);
    
    // Combine flows for lava cracks and streams
    float lavaPattern = flow1 * 0.7 + flow2 * 0.3;
    
    // Create hot spots that pulse
    float hotSpot = sin(u_time * 2.0 + lavaPattern * 6.28) * 0.5 + 0.5;
    
    // Lava color gradient from dark red to bright orange/yellow
    vec3 darkLava = vec3(0.3, 0.05, 0.0);
    vec3 hotLava = vec3(1.0, 0.6, 0.1);
    vec3 brightLava = vec3(1.0, 1.0, 0.3);
    
    // Mix colors based on pattern and hot spots
    float intensity = lavaPattern * 0.8 + hotSpot * 0.2;
    vec3 lavaColor;
    if (intensity < 0.5) {
        lavaColor = mix(darkLava, hotLava, intensity * 2.0);
    } else {
        lavaColor = mix(hotLava, brightLava, (intensity - 0.5) * 2.0);
    }
    
    // Add emissive glow (lava doesn't need external lighting)
    lavaColor *= (1.0 + hotSpot * 0.5);
    
    // Lava transparency
    float alpha = 0.9;
    
    gl_FragColor = vec4(lavaColor, alpha);
}