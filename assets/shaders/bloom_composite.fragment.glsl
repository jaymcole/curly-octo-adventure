#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_sceneTexture;
uniform sampler2D u_bloomTexture;
uniform float u_bloomIntensity;
uniform float u_time;

varying vec2 v_texCoords;

// Simple noise function for heat distortion
float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    // Sample original scene (SpriteBatch will bind this to u_texture)
    vec4 sceneColor = texture2D(u_texture, v_texCoords);
    
    // Sample bloom
    vec4 bloomColor = texture2D(u_bloomTexture, v_texCoords);
    
    // Add heat distortion effect near bright areas
    float heatStrength = length(bloomColor.rgb) * 0.02;
    vec2 distortion = vec2(
        sin(v_texCoords.y * 50.0 + u_time * 8.0) * heatStrength,
        cos(v_texCoords.x * 30.0 + u_time * 6.0) * heatStrength * 0.5
    );
    
    // Sample scene with slight distortion
    vec4 distortedScene = texture2D(u_texture, v_texCoords + distortion);
    
    // Blend distorted scene with original based on heat intensity
    float heatMix = smoothstep(0.0, 0.3, heatStrength * 10.0);
    sceneColor = mix(sceneColor, distortedScene, heatMix);
    
    // Composite bloom with scene
    vec3 finalColor = sceneColor.rgb + (bloomColor.rgb * u_bloomIntensity);
    
    gl_FragColor = vec4(finalColor, sceneColor.a);
}