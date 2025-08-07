attribute vec3 a_position;
uniform mat4 u_worldTrans;
uniform mat4 u_lightViewProj;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    gl_Position = u_lightViewProj * worldPos;
}