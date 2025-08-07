attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;
attribute vec2 a_lightmapCoord; // Second UV set for lightmaps

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying vec2 v_lightmapCoord;

void main() {
    // Transform vertex to world space
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    
    // Transform normal to world space
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    
    // Pass through texture coordinates
    v_texCoord = a_texCoord0;
    v_lightmapCoord = a_lightmapCoord;
    
    // Transform to camera projection space
    gl_Position = u_projViewTrans * worldPos;
}