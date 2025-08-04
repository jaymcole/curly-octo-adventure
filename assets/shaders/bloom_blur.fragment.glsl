#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform vec2 u_direction; // (1,0) for horizontal, (0,1) for vertical
uniform vec2 u_resolution;

varying vec2 v_texCoords;

// Gaussian blur weights (5-tap)
const float weights[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec2 texelSize = 1.0 / u_resolution;
    vec3 result = texture2D(u_texture, v_texCoords).rgb * weights[0];
    
    for(int i = 1; i < 5; i++) {
        vec2 offset = u_direction * texelSize * float(i);
        result += texture2D(u_texture, v_texCoords + offset).rgb * weights[i];
        result += texture2D(u_texture, v_texCoords - offset).rgb * weights[i];
    }
    
    gl_FragColor = vec4(result, 1.0);
}