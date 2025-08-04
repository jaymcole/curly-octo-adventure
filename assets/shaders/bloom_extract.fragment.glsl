#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform float u_bloomThreshold;

varying vec2 v_texCoords;

void main() {
    vec4 color = texture2D(u_texture, v_texCoords);
    
    // Calculate luminance
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    // Extract bright areas above threshold
    float bloomStrength = max(0.0, luminance - u_bloomThreshold);
    bloomStrength = smoothstep(0.0, 0.5, bloomStrength);
    
    // Preserve original color but scale by bloom strength
    gl_FragColor = vec4(color.rgb * bloomStrength, 1.0);
}