attribute vec3 a_position;
attribute vec3 a_normal;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;
uniform mat4 u_lightMVP;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec4 v_lightSpacePos;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    v_lightSpacePos = u_lightMVP * worldPos;
    
    gl_Position = u_projViewTrans * worldPos;
}