attribute vec3 a_position;
uniform mat4 u_worldTrans;
uniform mat4 u_lightMVP;
uniform vec3 u_lightPosition;

varying vec3 v_worldPos;

void main() {
    // Transform vertex to world space
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    
    // Transform to light projection space
    gl_Position = u_lightMVP * vec4(a_position, 1.0);
}