attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;
uniform float u_time;

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec2 v_texCoord;
varying float v_time;

void main() {
    // Pass through values
    v_texCoord = a_texCoord0;
    v_time = u_time;
    
    // Transform vertex to world space
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    
    // Add subtle wave displacement to water surface
    float wave1 = sin(worldPos.x * 0.5 + u_time * 2.0) * 0.05;
    float wave2 = cos(worldPos.z * 0.3 + u_time * 1.5) * 0.03;
    worldPos.y += wave1 + wave2;
    
    // Transform normal to world space
    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
    
    // Transform to camera projection space
    gl_Position = u_projViewTrans * worldPos;
}