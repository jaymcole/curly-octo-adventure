#ifdef GL_ES
precision mediump float;
#endif

void main() {
    // Render solid green to test if quad geometry works
    gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);
}