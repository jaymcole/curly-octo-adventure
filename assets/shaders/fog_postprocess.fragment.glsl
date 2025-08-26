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

// Fractal noise for more complex fog patterns
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

// Distance-based fade function for simulating reduced visibility
float calculateViewFade(vec2 uv, vec2 center) {
    float distance = length(uv - center);
    // Reduced view distance - fog obscures distant objects
    float fadeStart = 0.25;  // Start fading at 25% from center
    float fadeEnd = 0.6;     // Heavy fog at 60% from center
    return 1.0 - smoothstep(fadeStart, fadeEnd, distance);
}

void main() {
    vec2 uv = v_texCoords;
    vec2 center = vec2(0.5, 0.5);

    // Sample the original scene
    vec4 sceneColor = texture2D(u_texture, uv);

    // Create rolling fog patterns
    float time = u_time * 0.8;
    vec2 fogCoord1 = uv * 4.0 + vec2(time * 0.2, time * 0.15);
    vec2 fogCoord2 = uv * 8.0 + vec2(time * -0.1, time * 0.25);

    float fog1 = fractalNoise(fogCoord1);
    float fog2 = smoothNoise(fogCoord2);

    // Combine fog patterns for dynamic density
    float fogDensity = (fog1 * 0.7 + fog2 * 0.3);

    // Create swirling motion in the fog
    vec2 swirl = vec2(
        sin(uv.x * 6.28 + time * 0.8) * 0.02,
        cos(uv.y * 4.71 + time * 1.2) * 0.015
    );

    float swirlFog = fractalNoise(uv * 6.0 + swirl + vec2(time * 0.3, time * -0.2));
    fogDensity = mix(fogDensity, swirlFog, 0.3);

    // Apply gray desaturation to simulate fog
    float luminance = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 grayColor = vec3(luminance);

    // Mix with slight blue-gray tint for atmospheric fog
    vec3 fogTint = vec3(0.85, 0.9, 0.95);
    vec3 tintedGray = grayColor * fogTint;

    // Apply fog tinting based on fog density
    float fogInfluence = 0.4 + fogDensity * 0.4;
    sceneColor.rgb = mix(sceneColor.rgb, tintedGray, fogInfluence);

    // Calculate view distance fade
    float viewFade = calculateViewFade(uv, center);

    // Create fog color for distance fade
    // vec3 fogColor = vec3(0.7, 0.75, 0.8); // dark blue color
    vec3 fogColor = vec3(1.00, 1.00, 1.0); // black color

    // Apply distance fade - objects far away disappear into fog
    sceneColor.rgb = mix(fogColor, sceneColor.rgb, viewFade);

    // Add subtle brightness reduction for atmospheric effect
    sceneColor.rgb *= (0.6 + viewFade * 0.4);

    // Add rolling fog overlay for dynamic effect
    float fogOverlay = smoothstep(0.3, 0.8, fogDensity) * 0.3;
    sceneColor.rgb = mix(sceneColor.rgb, fogColor, fogOverlay);

    // Reduce contrast for hazy appearance
    sceneColor.rgb = mix(vec3(0.5), sceneColor.rgb, 0.7);

    gl_FragColor = sceneColor;
}
