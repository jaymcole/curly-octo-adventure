#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform float u_time;
uniform vec2 u_resolution;

varying vec2 v_texCoords;

// Simple noise function for distortion
float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Smooth noise for better distortion
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
    vec2 uv = v_texCoords;
    
    // Create flowing water distortion patterns
    float time = u_time * 0.5;
    
    // Multiple layers of distortion at different scales and speeds
    vec2 distortion1 = vec2(
        sin(uv.y * 15.0 + time * 2.0) * 0.008,
        cos(uv.x * 12.0 + time * 1.5) * 0.006
    );
    
    vec2 distortion2 = vec2(
        sin(uv.y * 25.0 - time * 3.0) * 0.004,
        cos(uv.x * 20.0 + time * 2.5) * 0.003
    );
    
    // Add noise-based distortion for more organic movement
    vec2 noiseCoord = uv * 8.0 + vec2(time * 0.3, time * 0.2);
    float noiseValue = smoothNoise(noiseCoord);
    vec2 noiseDistortion = vec2(
        (noiseValue - 0.5) * 0.012,
        (smoothNoise(noiseCoord + vec2(1.3, 2.7)) - 0.5) * 0.010
    );
    
    // Combine all distortions
    vec2 finalDistortion = distortion1 + distortion2 + noiseDistortion;
    
    // Sample the scene with distortion
    vec2 distortedUV = uv + finalDistortion;
    vec4 sceneColor = texture2D(u_texture, distortedUV);
    
    // Apply underwater color tinting
    vec3 underwaterTint = vec3(0.4, 0.7, 1.0); // Blue-green tint
    sceneColor.rgb = mix(sceneColor.rgb, sceneColor.rgb * underwaterTint, 0.6);
    
    // Add slight brightness reduction and contrast adjustment
    sceneColor.rgb = sceneColor.rgb * 0.8 + 0.1;
    
    // Add subtle caustic light patterns
    vec2 causticCoord = uv * 6.0 + vec2(time * 0.8, time * 0.6);
    float caustic1 = sin(causticCoord.x + cos(causticCoord.y + time)) * 0.5 + 0.5;
    float caustic2 = sin(causticCoord.y + cos(causticCoord.x - time * 0.7)) * 0.5 + 0.5;
    float causticPattern = (caustic1 * caustic2) * 0.15;
    
    sceneColor.rgb += vec3(causticPattern * 0.3, causticPattern * 0.5, causticPattern * 0.7);
    
    gl_FragColor = sceneColor;
}