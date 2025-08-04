#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform float u_time;
uniform vec2 u_resolution;

varying vec2 v_texCoords;

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

// Distance-based fade function for simulating short view distance
float calculateViewFade(vec2 uv, vec2 center) {
    float distance = length(uv - center);
    // Short view distance - everything beyond center fades quickly
    float fadeStart = 0.3;  // Start fading at 30% from center
    float fadeEnd = 0.7;    // Complete fade at 70% from center
    return 1.0 - smoothstep(fadeStart, fadeEnd, distance);
}

void main() {
    vec2 uv = v_texCoords;
    vec2 center = vec2(0.5, 0.5);
    
    // Sample the original scene
    vec4 sceneColor = texture2D(u_texture, uv);
    
    // Create heat haze distortion
    float time = u_time * 2.0;
    vec2 heatCoord = uv * 12.0 + vec2(time * 0.5, time * 0.3);
    float heatNoise = smoothNoise(heatCoord) * 0.006;
    
    // Apply subtle heat distortion to the scene
    vec2 distortedUV = uv + vec2(
        sin(uv.y * 20.0 + time) * heatNoise,
        cos(uv.x * 15.0 + time * 0.8) * heatNoise * 0.7
    );
    vec4 distortedScene = texture2D(u_texture, distortedUV);
    
    // Mix original and distorted for subtle heat haze
    sceneColor = mix(sceneColor, distortedScene, 0.3);
    
    // Apply strong red/orange tint
    vec3 lavaTint = vec3(1.0, 0.4, 0.1); // Orange-red tint
    sceneColor.rgb = mix(sceneColor.rgb, sceneColor.rgb * lavaTint, 0.8);
    
    // Add pulsing heat effect
    float heatPulse = sin(time * 1.5) * 0.5 + 0.5;
    vec3 heatGlow = vec3(1.2, 0.6, 0.2) * heatPulse * 0.2;
    sceneColor.rgb += heatGlow;
    
    // Calculate view distance fade
    float viewFade = calculateViewFade(uv, center);
    
    // Create lava-colored fog for distance fade
    vec3 lavaFogColor = vec3(0.8, 0.2, 0.05);
    
    // Apply distance fade - mix with lava fog color
    sceneColor.rgb = mix(lavaFogColor, sceneColor.rgb, viewFade);
    
    // Add edge vignette for more dramatic short view distance
    float vignetteDistance = length(uv - center);
    float vignette = 1.0 - smoothstep(0.4, 0.9, vignetteDistance);
    sceneColor.rgb *= (0.3 + vignette * 0.7);
    
    // Increase contrast and brightness for dramatic lava environment
    sceneColor.rgb = pow(sceneColor.rgb, vec3(1.2)) * 1.1;
    
    gl_FragColor = sceneColor;
}