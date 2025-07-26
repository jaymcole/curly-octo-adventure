#ifdef GL_ES
precision highp float;
#endif

void main() {
    // Store linear depth instead of gl_FragCoord.z for better precision
    float depth = gl_FragCoord.z;
    
    // Pack depth into RGBA for better precision (optional, but helps with precision issues)
    gl_FragColor = vec4(depth, depth, depth, 1.0);
}