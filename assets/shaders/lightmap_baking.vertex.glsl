attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    v_texCoord = a_texCoord0;
    gl_Position = u_projViewTrans * worldPos;
}