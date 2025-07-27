#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_lightPosition;
uniform float u_farPlane;

varying vec3 v_worldPos;

void main() {
    // Calculate distance from light to this fragment
    float distance = length(v_worldPos - u_lightPosition);
    
    // Normalize the distance to [0,1] range
    distance = distance / u_farPlane;
    
    // Store the normalized distance as depth
    gl_FragColor = vec4(distance, distance, distance, 1.0);
}