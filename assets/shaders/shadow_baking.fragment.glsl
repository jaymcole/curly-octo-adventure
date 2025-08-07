#ifdef GL_ES
precision mediump float;
#endif

void main() {
    // Store normalized depth in red channel
    gl_FragColor = vec4(gl_FragCoord.z, 0.0, 0.0, 1.0);
}